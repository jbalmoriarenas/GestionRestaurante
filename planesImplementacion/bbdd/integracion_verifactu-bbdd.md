# Plan de Implementación — Integración VeriFactu (Base de datos)

Depende únicamente de [`fase0-bbdd.md`](./fase0-bbdd.md) (tabla `empleados` y el catálogo de permisos). Independiente de `facturas`/`proveedores` (`fase2-1-bbdd.md`) — **no comparte ninguna tabla con ese módulo** (ver "Análisis y decisiones técnicas"). Fuente de verdad de nombres para [`../back/integracion_verifactu-back.md`](../back/integracion_verifactu-back.md).

**Revisión de alcance (esta versión)**: la versión anterior de este documento explícitamente **no** construía el módulo de ventas ("la app no emite hoy facturas a clientes... esta fase no construye ese módulo"). A petición del usuario, esa decisión cambia: esta versión **sí** construye `ventas` (registro de lo que el restaurante factura a sus clientes) como pieza que dispara de verdad la generación de registros VeriFactu, en vez de depender solo del endpoint de prueba. Las tablas `configuracion_emisor_fiscal` y `registro_facturacion_verifactu` se mantienen tal como se diseñaron (siguen siendo correctas), ampliando esta última con el enganche a `ventas` que el documento anterior dejaba pendiente a propósito.

**Ninguna migración de este documento se ha implementado todavía en el repositorio** (la más alta actualmente aplicada es `V41__add_email_remitente_a_configuracion_empresa.sql`, Fase 6.1) — la numeración `V39`-`V44` de la versión anterior de este plan era **provisional y nunca llegó a aplicarse**; se renumera aquí a partir de `V42`, el primer número real libre, siguiendo el criterio ya documentado en `plan-implementacion-general.md` ("Notas adicionales").

## Contexto y objetivo

Cuatro tablas nuevas y una ampliación:

1. `configuracion_emisor_fiscal` — datos fiscales del negocio (NIF, razón social). Sin cambios respecto a la versión anterior.
2. `secuencia_venta` — un contador de numeración por serie, para poder asignar `numero` de forma correlativa y sin huecos bajo escrituras concurrentes (ver "Análisis y decisiones técnicas").
3. `ventas` — una fila por venta emitida a un cliente (factura simplificada por defecto, o completa si se identifica al cliente).
4. `venta_desglose_iva` — el desglose de base imponible/cuota por cada tipo impositivo aplicado en una venta (una venta de hostelería habitualmente mezcla comida al 10% y bebidas alcohólicas al 21%; el esquema oficial de la AEAT exige este desglose por tramo, no un único importe/cuota global — ver más abajo).
5. `registro_facturacion_verifactu` — se amplía con `venta_id` (nullable) y `desglose_iva` (JSON), resolviendo el punto que la versión anterior dejaba abierto ("cuando exista el módulo real de facturación de venta, se añadirá... columna/FK que enlace cada registro con la factura de venta que lo originó").

Una vista de permisos nueva, `VENTAS` (además de `VERIFACTU`, ya prevista), ambas de acceso exclusivo `ADMIN`.

## Análisis y decisiones técnicas

- **Fundamento legal de cada decisión de esta sección**: Real Decreto 1007/2023 (Reglamento de sistemas informáticos de facturación / VeriFactu) y Reglamento de facturación (RD 1619/2012), consultados para esta revisión — no una interpretación libre.
- **Factura simplificada por defecto, completa si se identifica al cliente**: el Reglamento de facturación permite factura simplificada (sin datos del destinatario) en hostelería hasta **3.000 € IVA incluido** (frente a los 400 € del régimen general); solo es obligatorio identificar al cliente (NIF, nombre, domicilio) cuando este quiere deducirse el IVA o pide expresamente factura completa (RD 1619/2012, arts. 4 y 6). Por eso `ventas.cliente_nif`/`cliente_nombre`/`cliente_domicilio` son **nullable**: una venta nace como ticket simplificado y se convierte en factura completa solo si se rellenan.
- **Desglose de IVA por tramo, tabla propia (`venta_desglose_iva`) y no un único `importe_base`/`cuota` en `ventas`**: el esquema `SuministroInformacion.xsd` de la AEAT exige un `Desglose` con una entrada por tipo impositivo (base imponible + cuota), no un total agregado — la versión anterior de este documento no lo contemplaba porque el "registro de prueba" simulaba datos ya agregados; con ventas reales de un restaurante (que mezclan tipos con normalidad) hay que modelarlo bien desde el principio. `ventas.importe_total` seguirá existiendo como columna calculada (suma de `base_imponible + cuota` de todas las líneas) para listar/filtrar sin tener que agregar en cada consulta, igual que `facturas.importe_total` ya coexiste con `facturas.importe_base`/`iva`.
- **`secuencia_venta` con bloqueo de fila (`SELECT ... FOR UPDATE`), no "leer el último número e incrementar" sin más**: la numeración de facturas debe ser correlativa y sin huecos dentro de cada serie (RD 1619/2012 art. 6.1.b) — bajo dos ventas concurrentes, leer `MAX(numero)` y sumar 1 en cada transacción puede asignar el mismo número dos veces o dejar huecos si una transacción hace rollback. Un contador dedicado con bloqueo pesimista, actualizado dentro de la misma transacción que inserta la venta (`back/integracion_verifactu-back.md`), elimina la condición de carrera. Ver también `uk_ventas_serie_numero` como red de seguridad a nivel de esquema.
- **Sin reseteo anual automático de la numeración**: el usuario define sus propias series (p. ej. `A`, o `2026` si quiere resetear cada año) — la app no fuerza ningún criterio de cuándo empezar una serie nueva. **Suposición explícita**, no confirmada por el usuario ni exigida por la ley de forma única (la normativa exige correlatividad *dentro de cada serie*, no un criterio concreto de cuándo abrir una serie nueva).
- **`ventas` no es append-only como `registro_facturacion_verifactu`**: una venta puede pasar de `EMITIDA` a `ANULADA` (columnas `fecha_anulacion`/`motivo_anulacion`/`anulada_por_admin_id`), sin crear una fila nueva — es el mismo hecho de negocio, no una venta distinta. Lo que sí es estrictamente append-only es `registro_facturacion_verifactu`: anular una venta genera una **fila nueva** en esa tabla (`tipo_registro = 'ANULACION'`, encadenada con `huella_anterior`), nunca se borra ni modifica el registro `ALTA` original — así lo exige el Reglamento (un registro de anulación es un evento nuevo en la cadena, no una corrección del anterior).
- **`registro_facturacion_verifactu.venta_id` nullable**: los registros generados por el endpoint de prueba (`POST /api/verifactu/registros/prueba`, que se mantiene para validar el pipeline sin depender de una venta real) siguen sin `venta_id`. Los generados a partir de una venta real (alta o anulación) sí lo llevan — es la forma de distinguir, en el histórico de `/verifactu`, un registro de prueba de uno real.
- **`registro_facturacion_verifactu.desglose_iva` en JSON, no una tabla hija propia**: a diferencia de `venta_desglose_iva` (que el Admin edita como parte del formulario de alta de la venta, y por tanto necesita ser consultable/editable fila a fila), el desglose de un *registro* es una copia inmutable en el momento de generarse — mismo criterio ya aplicado a `nif_emisor`/`nombre_emisor` (denormalizados, no una FK a `configuracion_emisor_fiscal`) y a `respuesta_aeat` (JSON crudo): un dato que no debe cambiar bajo los pies del registro ya generado ni necesita consultarse relacionalmente.
- **`PRODUCCION` sigue sin añadirse al `CHECK` de `entorno`, a propósito**: aunque esta fase construye el módulo de venta real, seguir enviando (o simulando) esos registros contra `LOCAL_SIMULADO`/`PRE` es deliberado — ver "No-objetivos" en `back/integracion_verifactu-back.md`. Habilitar `PRODUCCION` de verdad exige primero el certificado digital de la empresa y validar el algoritmo de huella/esquema SOAP contra la documentación oficial vigente (riesgos ya señalados y todavía abiertos), y es una migración/decisión aparte cuando llegue ese momento — añadirlo ya sería dar por resuelto un riesgo que sigue sin estarlo.
- Todo lo demás de la versión anterior (fila única de `configuracion_emisor_fiscal` con `INSERT` inicial, cadena de huellas por `entorno`, `qr_contenido` no persiste la imagen) se mantiene sin cambios — sigue siendo correcto.

## Desglose de subtareas

1. **[BBDD, baja]** `V42__seed_vista_verifactu.sql`.
2. **[BBDD, baja]** `V43__seed_vista_ventas.sql`.
3. **[BBDD, media]** `V44__create_configuracion_emisor_fiscal_table.sql`.
4. **[BBDD, media]** `V45__create_secuencia_venta_table.sql`.
5. **[BBDD, alta]** `V46__create_ventas_table.sql`.
6. **[BBDD, media]** `V47__create_venta_desglose_iva_table.sql`.
7. **[BBDD, alta]** `V48__create_registro_facturacion_verifactu_table.sql`.

## DDL

### `V42__seed_vista_verifactu.sql`

```sql
INSERT INTO vistas (codigo, nombre, descripcion) VALUES
    ('VERIFACTU', 'VeriFactu', 'Configuración fiscal e integración de pruebas con el sistema VeriFactu de la AEAT');

INSERT INTO permisos (tipo_usuario_id, vista_id, nivel_acceso)
SELECT tu.id, v.id, 'EDICION'
FROM tipos_usuarios tu
JOIN vistas v ON v.codigo = 'VERIFACTU'
WHERE tu.codigo = 'ADMIN';
```

### `V43__seed_vista_ventas.sql`

```sql
-- Vista propia, distinta de VERIFACTU (configuracion/pruebas) y de FACTURAS (gastos de
-- proveedores): emitir facturas de venta es una capacidad de negocio distinta — un Admin podria
-- querer delegar la gestion de facturas recibidas sin dar acceso a los datos de facturacion/venta
-- del negocio (mas sensibles). Mismo patron que VALORACIONES (fase4-1-bbdd.md): ADMIN unicamente.
INSERT INTO vistas (codigo, nombre, descripcion) VALUES
    ('VENTAS', 'Ventas', 'Registro de ventas (facturas simplificadas y completas) emitidas a clientes');

INSERT INTO permisos (tipo_usuario_id, vista_id, nivel_acceso)
SELECT tu.id, v.id, 'EDICION'
FROM tipos_usuarios tu
JOIN vistas v ON v.codigo = 'VENTAS'
WHERE tu.codigo = 'ADMIN';
```

### `V44__create_configuracion_emisor_fiscal_table.sql`

```sql
CREATE TABLE configuracion_emisor_fiscal (
    id                          BIGINT        PRIMARY KEY,
    nif                          VARCHAR(15)   NULL,
    razon_social                 VARCHAR(150)  NULL,
    nombre_comercial              VARCHAR(150)  NULL,
    direccion                    VARCHAR(255)  NULL,
    codigo_postal                 VARCHAR(10)   NULL,
    municipio                    VARCHAR(100)  NULL,
    provincia                    VARCHAR(100)  NULL,
    updated_at                   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    actualizado_por_admin_id       BIGINT        NULL,

    CONSTRAINT fk_configuracion_emisor_fiscal_admin FOREIGN KEY (actualizado_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT chk_configuracion_emisor_fiscal_id_unico CHECK (id = 1)
);

-- Con INSERT inicial a proposito (igual que configuracion_ubicacion, fase1-1-bbdd.md): la fila
-- siempre existe para forzar su configuracion desde el panel de VeriFactu; todos los campos
-- fiscales nacen NULL hasta que el Admin los rellena.
INSERT INTO configuracion_emisor_fiscal (id) VALUES (1);
```

### `V45__create_secuencia_venta_table.sql`

```sql
-- Contador de numeracion por serie, actualizado con SELECT ... FOR UPDATE dentro de la misma
-- transaccion que inserta la venta (back/integracion_verifactu-back.md) para garantizar
-- numeracion correlativa y sin huecos bajo escrituras concurrentes (RD 1619/2012 art. 6.1.b).
CREATE TABLE secuencia_venta (
    serie           VARCHAR(20)  PRIMARY KEY,
    ultimo_numero   BIGINT       NOT NULL DEFAULT 0
);
```

### `V46__create_ventas_table.sql`

```sql
CREATE TABLE ventas (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    serie                    VARCHAR(20)    NOT NULL,
    numero                   BIGINT         NOT NULL,
    fecha_expedicion         DATE           NOT NULL,
    descripcion_operacion    VARCHAR(255)   NOT NULL,
    cliente_nif              VARCHAR(15)    NULL,
    cliente_nombre           VARCHAR(150)   NULL,
    cliente_domicilio        VARCHAR(255)   NULL,
    importe_total            DECIMAL(12,2)  NOT NULL,
    estado                   VARCHAR(20)    NOT NULL DEFAULT 'EMITIDA',
    fecha_anulacion          TIMESTAMP      NULL,
    motivo_anulacion         VARCHAR(255)   NULL,
    anulada_por_admin_id      BIGINT         NULL,
    emitida_por_admin_id      BIGINT         NOT NULL,
    fecha_creacion           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ventas_anulada_admin FOREIGN KEY (anulada_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT fk_ventas_emitida_admin FOREIGN KEY (emitida_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT uk_ventas_serie_numero UNIQUE (serie, numero),
    CONSTRAINT chk_ventas_estado CHECK (estado IN ('EMITIDA', 'ANULADA'))
);

CREATE INDEX idx_ventas_fecha_expedicion ON ventas (fecha_expedicion);
CREATE INDEX idx_ventas_estado ON ventas (estado);
```

### `V47__create_venta_desglose_iva_table.sql`

```sql
CREATE TABLE venta_desglose_iva (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    venta_id           BIGINT         NOT NULL,
    tipo_impositivo    DECIMAL(5,2)   NOT NULL,
    base_imponible     DECIMAL(12,2)  NOT NULL,
    cuota              DECIMAL(12,2)  NOT NULL,

    CONSTRAINT fk_venta_desglose_iva_venta FOREIGN KEY (venta_id) REFERENCES ventas (id),
    -- Una sola linea por tipo impositivo distinto dentro de la misma venta (p.ej. no puede haber
    -- dos lineas al 10% en la misma venta; si hay mas base al mismo tipo, se suma en una sola fila).
    CONSTRAINT uk_venta_desglose_iva_tipo UNIQUE (venta_id, tipo_impositivo)
);
```

### `V48__create_registro_facturacion_verifactu_table.sql`

```sql
CREATE TABLE registro_facturacion_verifactu (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    entorno                   VARCHAR(20)    NOT NULL,
    tipo_registro             VARCHAR(20)    NOT NULL DEFAULT 'ALTA',
    venta_id                  BIGINT         NULL,
    numero_factura            VARCHAR(60)    NOT NULL,
    serie_factura             VARCHAR(20)    NULL,
    fecha_expedicion          DATE           NOT NULL,
    descripcion_operacion     VARCHAR(255)   NOT NULL,
    nif_emisor                VARCHAR(15)    NOT NULL,
    nombre_emisor             VARCHAR(150)   NOT NULL,
    cliente_nif               VARCHAR(15)    NULL,
    cliente_nombre            VARCHAR(150)   NULL,
    importe_total             DECIMAL(12,2)  NOT NULL,
    cuota_total               DECIMAL(12,2)  NOT NULL,
    desglose_iva              JSON           NOT NULL,
    huella_anterior           VARCHAR(64)    NULL,
    huella_actual             VARCHAR(64)    NOT NULL,
    qr_contenido              VARCHAR(500)   NOT NULL,
    estado_envio              VARCHAR(20)    NOT NULL DEFAULT 'PENDIENTE',
    respuesta_aeat            JSON           NULL,
    fecha_generacion          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_envio               TIMESTAMP      NULL,
    generado_por_admin_id      BIGINT         NOT NULL,

    CONSTRAINT fk_registro_verifactu_admin FOREIGN KEY (generado_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT fk_registro_verifactu_venta FOREIGN KEY (venta_id) REFERENCES ventas (id),
    CONSTRAINT uk_registro_verifactu_huella_actual UNIQUE (huella_actual),
    CONSTRAINT chk_registro_verifactu_entorno CHECK (entorno IN ('LOCAL_SIMULADO', 'PRE')),
    CONSTRAINT chk_registro_verifactu_tipo CHECK (tipo_registro IN ('ALTA', 'ANULACION')),
    CONSTRAINT chk_registro_verifactu_estado_envio CHECK (estado_envio IN ('PENDIENTE', 'ENVIADA', 'ACEPTADA', 'ACEPTADA_CON_ERRORES', 'RECHAZADA', 'ERROR_ENVIO'))
);

CREATE INDEX idx_registro_verifactu_entorno_fecha ON registro_facturacion_verifactu (entorno, fecha_generacion);
CREATE INDEX idx_registro_verifactu_estado_envio ON registro_facturacion_verifactu (estado_envio);
CREATE INDEX idx_registro_verifactu_venta ON registro_facturacion_verifactu (venta_id);
```

**Mapeo de columnas ↔ campo de entidad JPA / DTO** (debe coincidir con `back/integracion_verifactu-back.md`):

| Columna | Campo entidad | Tipo Java | Tipo TS (front) |
|---|---|---|---|
| `configuracion_emisor_fiscal.*` | (sin cambios respecto a la versión anterior) | | |
| `secuencia_venta.serie` / `.ultimo_numero` | *(sin entidad JPA propia — acceso vía `EntityManager`/SQL nativo con `SELECT ... FOR UPDATE`, ver `back/integracion_verifactu-back.md`)* | | |
| `ventas.serie` / `.numero` | `serie` / `numero` | `String` / `Long` | `string` / `number` |
| `ventas.fecha_expedicion` | `fechaExpedicion` | `LocalDate` | `string` |
| `ventas.descripcion_operacion` | `descripcionOperacion` | `String` | `string` |
| `ventas.cliente_nif` / `.cliente_nombre` / `.cliente_domicilio` | `clienteNif` / `clienteNombre` / `clienteDomicilio` (todos nullable) | `String` | `string \| null` |
| `ventas.importe_total` | `importeTotal` (calculado, nunca enviado por el cliente — ver `back/integracion_verifactu-back.md`) | `BigDecimal` | `number` |
| `ventas.estado` | `estado` (enum `EstadoVenta`) | `EMITIDA \| ANULADA` | `'EMITIDA' \| 'ANULADA'` |
| `venta_desglose_iva.tipo_impositivo` / `.base_imponible` / `.cuota` | `tipoImpositivo` / `baseImponible` / `cuota` | `BigDecimal` | `number` |
| `registro_facturacion_verifactu.venta_id` | `ventaId` (nullable — `null` = registro de prueba) | `Long` | `number \| null` |
| `registro_facturacion_verifactu.descripcion_operacion` | `descripcionOperacion` | `String` | `string` |
| `registro_facturacion_verifactu.cliente_nif` / `.cliente_nombre` | `clienteNif` / `clienteNombre` (nullable) | `String` | `string \| null` |
| `registro_facturacion_verifactu.desglose_iva` | `desgloseIva` (JSON crudo — lista de `{tipoImpositivo, baseImponible, cuota}`) | `String` | `Array<{tipoImpositivo, baseImponible, cuota}>` |
| (resto de columnas de `registro_facturacion_verifactu`) | (sin cambios respecto a la versión anterior) | | |

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase0-bbdd.md`: sin `empleados` ni el catálogo de permisos, esta migración no tiene sentido.
- **Riesgo alto — correlatividad de la numeración bajo concurrencia**: sin el bloqueo `SELECT ... FOR UPDATE` sobre `secuencia_venta` dentro de la misma transacción que inserta la venta (`back/integracion_verifactu-back.md`), dos ventas concurrentes en la misma serie podrían recibir el mismo número (bloqueado a nivel de esquema por `uk_ventas_serie_numero`, pero eso solo evita el dato incorrecto, no el fallo/reintento visible al usuario) o dejar huecos si una transacción falla a mitad. Mitigado por el diseño ya explicado, pero conviene un test de concurrencia explícito (ver `back/integracion_verifactu-back.md`).
- **Riesgo bajo**: `registro_facturacion_verifactu` seguirá acumulando registros de prueba (`venta_id IS NULL`) sin mecanismo de limpieza — aceptable como herramienta de validación técnica.
- **Riesgo medio, ya señalado y sin cambios**: si en el futuro se admite más de un emisor/establecimiento en la misma instancia, la cadena de huellas (hoy por `entorno`) necesitaría una clave adicional.

## Criterios de aceptación técnicos

- Tras `V44`, existe exactamente una fila en `configuracion_emisor_fiscal` (`id = 1`) con todos los campos fiscales a `NULL`.
- Insertar una venta con `serie`/`numero` ya existentes falla por `uk_ventas_serie_numero`.
- Insertar una venta con `estado` fuera del `CHECK` falla.
- Insertar dos líneas de `venta_desglose_iva` con el mismo `tipo_impositivo` para la misma venta falla por `uk_venta_desglose_iva_tipo`.
- Insertar en `registro_facturacion_verifactu` con `entorno = 'PRODUCCION'` sigue fallando por `chk_registro_verifactu_entorno` (deliberado, ver "Análisis y decisiones técnicas").
- Insertar dos registros con la misma `huella_actual` falla por `uk_registro_verifactu_huella_actual`.
- Un registro con `venta_id` apuntando a una venta inexistente falla por `fk_registro_verifactu_venta`.
- Tras `V42`/`V43`, `ADMIN` tiene `EDICION` en `VERIFACTU` y en `VENTAS`; `EMPLEADO` no tiene ninguna fila de permiso para ninguna de las dos vistas.
