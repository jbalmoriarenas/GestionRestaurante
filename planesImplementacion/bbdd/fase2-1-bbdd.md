# Plan de Implementación — Fase 2.1 (Base de datos): Facturas — modelo y listado

Depende únicamente de [`fase0-bbdd.md`](./fase0-bbdd.md) (tabla `empleados` y el catálogo de permisos, que ya siembra la vista `FACTURAS` con `EDICION` solo para `ADMIN` — ver `V5__seed_catalogo_permisos.sql`). No requiere ninguna migración de permisos nueva, igual que `fase3-1-bbdd.md` con `NOMINAS`. Fuente de verdad de nombres para [`../back/fase2-1-back.md`](../back/fase2-1-back.md).

## Contexto y objetivo

Dos tablas nuevas: `proveedores` (catálogo de empresas a las que se les paga — proveedores de mercancía/servicios y suministradores de luz/agua/gas, distinguidos por `tipo`) y `facturas` (una fila por factura registrada, manual o vía OCR). Las capacidades de exportación (`fase2-2`) y extracción automática (`fase2-3`) reutilizan estas mismas tablas — `fase2-3` sí añade columnas nuevas sobre `facturas` y una tabla adicional para campos dinámicos (ver ese documento).

## Análisis y decisiones técnicas

- **`proveedores.tipo` en vez de `facturas.tipo`**: el tipo (`PROVEEDOR` | `SUMINISTRO`) es una propiedad intrínseca de la empresa (Iberdrola siempre es un suministro, nunca mezclado con mercancía), no de la factura individual — normalizarlo en `proveedores` evita que dos facturas de la misma empresa puedan quedar en pestañas distintas por error de captura. Las dos pestañas del front (`front/fase2-1-front.md`) filtran `facturas` vía `JOIN proveedores` por este campo.
- **`cif` nullable con índice único**: no todas las empresas de suministros facilitan CIF de forma consistente en la factura (algunas facturas domésticas de luz/agua solo traen el nombre comercial); MySQL permite múltiples `NULL` en una columna `UNIQUE`, así que se deja nullable sin renunciar a evitar duplicados cuando sí se conoce.
- **`facturas.ruta_archivo` es `NOT NULL`** (mismo criterio que `nominas.ruta_archivo` en `fase3-1-bbdd.md`): toda factura registrada, manual o por OCR, requiere el documento adjunto — no existe el estado "factura sin archivo".
- **`mes`/`anio` como columnas separadas, calculadas por el backend a partir de `fecha_factura`** (nunca enviadas por el cliente): mismo patrón ya usado en `nominas.periodo_mes`/`periodo_anio`, pero aquí **derivadas** en vez de introducidas por el usuario — evita filtros/índices inconsistentes con la fecha real de la factura.
- **`importe_base`/`iva` nullables, `importe_total` obligatorio**: no todas las facturas domésticas de suministros desglosan base imponible e IVA de forma clara en el documento; el importe total (la "cuantía" pedida por el usuario) es el único dato económico que siempre se puede rellenar con confianza en el MVP manual (`fase2-1`). El desglose completo se exporta igualmente en el XLS (`fase2-2-back.md`) cuando esté disponible.
- **`estado_pago` (`PENDIENTE`|`PAGADA`) es un campo de negocio nuevo**, distinto del futuro `estado_extraccion` de `fase2-3-bbdd.md` (que rastrea el pipeline de OCR, no si se ha pagado). No confundir ambos.
- **Sin `estado_extraccion`/`datos_extraidos_raw` todavía**: esta migración no los incluye porque en `fase2-1` no existe extracción automática — se añaden en `fase2-3-bbdd.md` vía `ALTER TABLE`, con un valor por defecto (`'MANUAL'`) que cubre retroactivamente las filas creadas en esta fase sin necesitar backfill manual.

## Desglose de subtareas

1. **[BBDD, baja]** `V26__create_proveedores_table.sql`.
2. **[BBDD, media]** `V27__create_facturas_table.sql`.

## DDL

### `V26__create_proveedores_table.sql`

```sql
CREATE TABLE proveedores (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(150)  NOT NULL,
    cif             VARCHAR(15)   NULL,
    email_contacto  VARCHAR(150)  NULL,
    telefono        VARCHAR(20)   NULL,
    tipo            VARCHAR(20)   NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_proveedores_cif UNIQUE (cif),
    CONSTRAINT chk_proveedores_tipo CHECK (tipo IN ('PROVEEDOR', 'SUMINISTRO'))
);

CREATE INDEX idx_proveedores_tipo ON proveedores (tipo);
```

### `V27__create_facturas_table.sql`

```sql
CREATE TABLE facturas (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    proveedor_id             BIGINT         NOT NULL,
    numero_factura           VARCHAR(50)    NULL,
    producto                 VARCHAR(255)   NOT NULL,
    fecha_factura             DATE           NOT NULL,
    fecha_vencimiento        DATE           NULL,
    importe_base             DECIMAL(10,2)  NULL,
    iva                      DECIMAL(10,2)  NULL,
    importe_total            DECIMAL(10,2)  NOT NULL,
    mes                      TINYINT        NOT NULL,
    anio                     SMALLINT       NOT NULL,
    estado_pago              VARCHAR(20)    NOT NULL DEFAULT 'PENDIENTE',
    ruta_archivo              VARCHAR(255)   NOT NULL,
    nombre_archivo_original  VARCHAR(255)   NOT NULL,
    subida_por_admin_id      BIGINT         NOT NULL,
    fecha_subida             TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_facturas_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores (id),
    CONSTRAINT fk_facturas_subida_admin FOREIGN KEY (subida_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT chk_facturas_mes CHECK (mes BETWEEN 1 AND 12),
    CONSTRAINT chk_facturas_estado_pago CHECK (estado_pago IN ('PENDIENTE', 'PAGADA'))
);

CREATE INDEX idx_facturas_proveedor ON facturas (proveedor_id);
CREATE INDEX idx_facturas_periodo ON facturas (anio, mes);
CREATE INDEX idx_facturas_estado_pago ON facturas (estado_pago);
```

**Mapeo de columnas ↔ campo de entidad JPA / DTO** (debe coincidir con `back/fase2-1-back.md`):

| Columna | Campo entidad | Tipo Java | Tipo TS (front) |
|---|---|---|---|
| `proveedores.tipo` | `tipo` (`@Enumerated(EnumType.STRING)`) | `TipoProveedor {PROVEEDOR, SUMINISTRO}` | `'PROVEEDOR' \| 'SUMINISTRO'` |
| `facturas.proveedor_id` | `proveedor` (`@ManyToOne`) | `Proveedor` | `proveedorId: number` (DTO listado/detalle) |
| `facturas.producto` | `producto` | `String` | `string` |
| `facturas.fecha_factura` | `fechaFactura` | `LocalDate` | `string` (ISO `yyyy-MM-dd`) |
| `facturas.importe_total` | `importeTotal` | `BigDecimal` | `number` |
| `facturas.mes` / `anio` | `mes` / `anio` | `Integer` | `number` (solo lectura, no se envían al crear) |
| `facturas.estado_pago` | `estadoPago` (`@Enumerated(EnumType.STRING)`) | `EstadoPago {PENDIENTE, PAGADA}` | `'PENDIENTE' \| 'PAGADA'` |
| `facturas.ruta_archivo` | `rutaArchivo` | `String` | — (nunca se expone; solo vía endpoint de descarga) |

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase0-bbdd.md`: sin `empleados` ni el catálogo de permisos con `FACTURAS` ya sembrado, esta migración no tiene sentido.
- **Riesgo bajo**: `cif` nullable-único puede dejar pasar duplicados reales si dos proveedores se dan de alta sin CIF (p. ej. dos suministros con el mismo nombre comercial mal escrito) — mitigado en `fase2-3` por el *fuzzy matching* contra `proveedores.nombre` al usar OCR, pero no elimina el riesgo en alta manual.
- **Riesgo bajo**: `mes`/`anio` derivados de `fecha_factura` quedarían desincronizados si en el futuro se permite editar `fecha_factura` sin recalcularlos — a tener en cuenta si `fase2-1-back.md` añade edición de fecha más adelante (fuera de alcance actual, ver ese documento).

## Criterios de aceptación técnicos

- Tras `V26`/`V27`, ambas tablas existen vacías.
- Insertar una `factura` sin `producto`, sin `importe_total` o sin `ruta_archivo` falla (`NOT NULL`).
- Insertar una `factura` con `mes = 13` falla por `chk_facturas_mes`.
- Insertar una `factura` con `estado_pago = 'PROCESANDO'` falla por `chk_facturas_estado_pago`.
- Insertar un `proveedor` con `tipo = 'OTRO'` falla por `chk_proveedores_tipo`.
- Insertar dos `proveedores` con el mismo `cif` no nulo falla por `uk_proveedores_cif`; insertar varios con `cif NULL` no falla.
