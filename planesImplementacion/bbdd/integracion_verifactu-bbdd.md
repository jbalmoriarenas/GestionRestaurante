# Plan de Implementación — Integración VeriFactu (Base de datos)

Depende únicamente de [`fase0-bbdd.md`](./fase0-bbdd.md) (tabla `empleados` y el catálogo de permisos). Independiente de `facturas`/`proveedores` (`fase2-1-bbdd.md`) — **no comparte ninguna tabla ni fila con ese módulo** (ver "Análisis y decisiones técnicas"). Fuente de verdad de nombres para [`../back/integracion_verifactu-back.md`](../back/integracion_verifactu-back.md).

## Contexto y objetivo

Alcance acordado explícitamente con el usuario: la app **no emite hoy facturas/tickets a clientes** (el módulo `facturas` existente son facturas *recibidas* de proveedores, sin relación con la obligación VeriFactu, que regula el software con el que un negocio *emite* sus propias facturas). Esta fase **no construye ese módulo de venta** — solo prepara el terreno técnico de VeriFactu (huella encadenada, QR, envío a la AEAT) con tres tablas nuevas e independientes, para poder generar y validar "registros de prueba" en local y contra el entorno de preproducción (*pre*/sandbox) de la AEAT antes de que exista una factura de venta real que los dispare automáticamente.

Dos tablas nuevas: `configuracion_emisor_fiscal` (datos fiscales del negocio — NIF, razón social — que hoy no existen en ninguna tabla del proyecto) y `registro_facturacion_verifactu` (una fila por registro de facturación generado, encadenado criptográficamente con el anterior). Una vista de permisos nueva, `VERIFACTU`.

## Análisis y decisiones técnicas

- **Necesita una vista nueva en el catálogo de permisos (`VERIFACTU`)**, exclusiva de `ADMIN` — mismo patrón que `VALORACIONES` (`fase4-1-bbdd.md`): configurar datos fiscales y generar/enviar registros de facturación no es algo a lo que un `EMPLEADO` deba tener acceso.
- **`registro_facturacion_verifactu` es una tabla completamente independiente de `facturas`**: no lleva ninguna FK hacia `facturas`/`proveedores` a propósito. `facturas` son gastos recibidos; este registro modela (de forma simulada, por ahora) facturas *emitidas*. Cuando exista el módulo real de facturación de venta, se añadirá en una migración posterior la columna/FK que enlace cada registro con la factura de venta que lo originó — fuera de alcance de este documento.
- **`configuracion_emisor_fiscal` es una fila única (`id = 1`), igual que `configuracion_ubicacion`** (`fase1-1-bbdd.md`), **y sí lleva `INSERT` inicial** (a diferencia de `conexion_google_business`, `fase4-1-bbdd.md`, que no lo lleva): aquí el patrón correcto es el de `configuracion_ubicacion` — una fila que existe siempre y que fuerza a que el Admin la complete, no una fila cuya ausencia signifique "no configurado". Todas las columnas fiscales nacen `NULL`; el backend (`back/integracion_verifactu-back.md`) rechaza generar un registro de facturación real mientras `nif`/`razon_social` sigan vacíos.
- **Los datos del emisor se copian (denormalizan) en cada fila de `registro_facturacion_verifactu`** (`nif_emisor`, `nombre_emisor`), en vez de una FK a `configuracion_emisor_fiscal`: la huella encadenada de un registro depende del valor de esos campos *en el momento de generarlo*; si el Admin corrige más adelante el NIF en `configuracion_emisor_fiscal`, los registros ya generados no deben cambiar de valor bajo los pies (romperían su propia huella). Mismo principio de inmutabilidad que ya aplica implícitamente `facturas.ruta_archivo` (el archivo subido no se recalcula si cambian otros datos).
- **`registro_facturacion_verifactu` es de solo inserción (append-only)**: ninguna columna se actualiza tras crearse **excepto** `estado_envio`, `respuesta_aeat` y `fecha_envio` (que reflejan el resultado de intentar enviar el registro ya calculado a la AEAT, no el contenido facturado). No se modela ningún `DELETE` a nivel de aplicación — es responsabilidad del backend, no de esta capa, impedirlo.
- **`entorno` solo admite `LOCAL_SIMULADO` y `PRE`, nunca `PRODUCCION`** en esta fase (`CHECK`): decisión explícita — sin módulo de venta real ni certificado definitivo de la empresa, no tiene sentido permitir un valor que sugiera un envío en producción. Añadir `PRODUCCION` al `CHECK` será parte de la fase que construya el módulo de venta real.
- **`huella_anterior` nullable, `huella_actual` no**: el primer registro de la cadena (por `entorno`, ver más abajo) no tiene anterior. La cadena se lee siempre como "el `huella_actual` del último registro insertado", replicando el mismo criterio de encadenamiento que exige el reglamento VeriFactu.
- **La cadena de huellas es única por `entorno`, no global**: los registros de prueba en `LOCAL_SIMULADO` y los enviados de verdad a `PRE` son dos cadenas independientes (así se pueden repetir pruebas en local sin contaminar ni depender de lo enviado al sandbox de la AEAT). `back/integracion_verifactu-back.md` calcula `huella_anterior` buscando el último registro **del mismo `entorno`**.
- **`respuesta_aeat` en `JSON`, no en columnas sueltas**: la respuesta del servicio SOAP de la AEAT es una estructura que puede variar (códigos de error adicionales, avisos); guardarla completa en `JSON` (mismo tipo ya usado para datos semi-estructurados en el proyecto, aunque `facturas.datos_extraidos_raw` ya no exista, fue el precedente en `fase2-1-bbdd.md`) evita tener que migrar el esquema cada vez que se quiera consultar un campo nuevo de la respuesta.
- **`qr_contenido` guarda el contenido/URL codificado en el QR, no la imagen**: la imagen PNG se genera al vuelo a partir de este texto (`back/integracion_verifactu-back.md`) — no tiene sentido persistir un binario derivado y regenerable.

## Desglose de subtareas

1. **[BBDD, baja]** `V39__seed_vista_verifactu.sql`.
2. **[BBDD, media]** `V40__create_configuracion_emisor_fiscal_table.sql`.
3. **[BBDD, media]** `V41__create_registro_facturacion_verifactu_table.sql`.

## DDL

### `V39__seed_vista_verifactu.sql`

```sql
INSERT INTO vistas (codigo, nombre, descripcion) VALUES
    ('VERIFACTU', 'VeriFactu', 'Configuración fiscal e integración de pruebas con el sistema VeriFactu de la AEAT');

INSERT INTO permisos (tipo_usuario_id, vista_id, nivel_acceso)
SELECT tu.id, v.id, 'EDICION'
FROM tipos_usuarios tu
JOIN vistas v ON v.codigo = 'VERIFACTU'
WHERE tu.codigo = 'ADMIN';
```

### `V40__create_configuracion_emisor_fiscal_table.sql`

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

### `V41__create_registro_facturacion_verifactu_table.sql`

```sql
CREATE TABLE registro_facturacion_verifactu (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    entorno                   VARCHAR(20)    NOT NULL,
    tipo_registro             VARCHAR(20)    NOT NULL DEFAULT 'ALTA',
    numero_factura            VARCHAR(60)    NOT NULL,
    serie_factura             VARCHAR(20)    NULL,
    fecha_expedicion          DATE           NOT NULL,
    nif_emisor                VARCHAR(15)    NOT NULL,
    nombre_emisor             VARCHAR(150)   NOT NULL,
    importe_total             DECIMAL(12,2)  NOT NULL,
    cuota_total               DECIMAL(12,2)  NOT NULL,
    huella_anterior           VARCHAR(64)    NULL,
    huella_actual             VARCHAR(64)    NOT NULL,
    qr_contenido              VARCHAR(500)   NOT NULL,
    estado_envio              VARCHAR(20)    NOT NULL DEFAULT 'PENDIENTE',
    respuesta_aeat            JSON           NULL,
    fecha_generacion          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_envio               TIMESTAMP      NULL,
    generado_por_admin_id      BIGINT         NOT NULL,

    CONSTRAINT fk_registro_verifactu_admin FOREIGN KEY (generado_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT uk_registro_verifactu_huella_actual UNIQUE (huella_actual),
    CONSTRAINT chk_registro_verifactu_entorno CHECK (entorno IN ('LOCAL_SIMULADO', 'PRE')),
    CONSTRAINT chk_registro_verifactu_tipo CHECK (tipo_registro IN ('ALTA', 'ANULACION')),
    CONSTRAINT chk_registro_verifactu_estado_envio CHECK (estado_envio IN ('PENDIENTE', 'ENVIADA', 'ACEPTADA', 'ACEPTADA_CON_ERRORES', 'RECHAZADA', 'ERROR_ENVIO'))
);

CREATE INDEX idx_registro_verifactu_entorno_fecha ON registro_facturacion_verifactu (entorno, fecha_generacion);
CREATE INDEX idx_registro_verifactu_estado_envio ON registro_facturacion_verifactu (estado_envio);
```

**Mapeo de columnas ↔ campo de entidad JPA / DTO** (debe coincidir con `back/integracion_verifactu-back.md`):

| Columna | Campo entidad | Tipo Java | Tipo TS (front) |
|---|---|---|---|
| `configuracion_emisor_fiscal.nif` / `razon_social` / `nombre_comercial` | `nif` / `razonSocial` / `nombreComercial` | `String` (nullable) | `string \| null` |
| `configuracion_emisor_fiscal.direccion` / `codigo_postal` / `municipio` / `provincia` | idem | `String` (nullable) | `string \| null` |
| `registro_facturacion_verifactu.entorno` | `entorno` (enum `EntornoVerifactu`) | `LOCAL_SIMULADO \| PRE` | `'LOCAL_SIMULADO' \| 'PRE'` |
| `registro_facturacion_verifactu.tipo_registro` | `tipoRegistro` (enum `TipoRegistroVerifactu`) | `ALTA \| ANULACION` | `'ALTA' \| 'ANULACION'` |
| `registro_facturacion_verifactu.numero_factura` / `serie_factura` | `numeroFactura` / `serieFactura` | `String` / `String` (nullable) | `string` / `string \| null` |
| `registro_facturacion_verifactu.importe_total` / `cuota_total` | `importeTotal` / `cuotaTotal` | `BigDecimal` | `number` |
| `registro_facturacion_verifactu.huella_anterior` / `huella_actual` | `huellaAnterior` (nullable) / `huellaActual` | `String` | `string \| null` / `string` |
| `registro_facturacion_verifactu.qr_contenido` | `qrContenido` | `String` | — (el front pide la imagen ya generada, no este campo crudo) |
| `registro_facturacion_verifactu.estado_envio` | `estadoEnvio` (enum `EstadoEnvioVerifactu`) | ver `CHECK` | union de los mismos literales |
| `registro_facturacion_verifactu.respuesta_aeat` | `respuestaAeat` | `String` (JSON crudo) | `unknown \| null` |

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase0-bbdd.md`: sin `empleados` ni el catálogo de permisos, esta migración no tiene sentido.
- **Riesgo bajo**: `registro_facturacion_verifactu` acumulará registros de prueba en `LOCAL_SIMULADO` sin ningún mecanismo de limpieza — aceptable mientras sea una herramienta de validación técnica; si molesta en volumen, es una tarea de mantenimiento aparte, no construida aquí.
- **Riesgo medio**: si en el futuro se decide que la cadena de huellas debe ser única por NIF emisor (p. ej. varios negocios/establecimientos en la misma instancia) en vez de por `entorno`, esta tabla necesitará una columna adicional y recalcular qué se considera "el registro anterior" — no aplica hoy porque solo hay un emisor (`configuracion_emisor_fiscal`, fila única).

## Criterios de aceptación técnicos

- Tras `V40`, existe exactamente una fila en `configuracion_emisor_fiscal` (`id = 1`) con todos los campos fiscales a `NULL`.
- Insertar una segunda fila en `configuracion_emisor_fiscal` falla por clave primaria; insertar con `id != 1` falla por `chk_configuracion_emisor_fiscal_id_unico`.
- Insertar en `registro_facturacion_verifactu` con `entorno = 'PRODUCCION'` falla por `chk_registro_verifactu_entorno`.
- Insertar dos registros con la misma `huella_actual` falla por `uk_registro_verifactu_huella_actual`.
- Insertar con `estado_envio` fuera del `CHECK` (p. ej. `'ENVIADO'` sin la A final) falla.
- Tras `V39`, `ADMIN` tiene `EDICION` en `VERIFACTU` y `EMPLEADO` no tiene ninguna fila de permiso para esa vista.
