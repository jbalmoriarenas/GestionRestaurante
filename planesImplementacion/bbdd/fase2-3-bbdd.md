# Plan de Implementación — Fase 2.3 (Base de datos): Facturas — estado de extracción automática

Depende de [`fase2-1-bbdd.md`](./fase2-1-bbdd.md) (tabla `facturas` debe existir). Fuente de verdad de nombres para [`../back/fase2-3-back.md`](../back/fase2-3-back.md).

## Contexto y objetivo

Una única migración amplía `facturas` con el estado del pipeline de extracción automática (OCR). La extracción solo propone valores para los campos fijos ya existentes (proveedor, fecha, importe base/IVA/total) — no hay ningún mecanismo para "campos nuevos que no existían en la BBDD": el PDF/imagen original (`facturas.ruta_archivo`, ya en `fase2-1-bbdd.md`) es la única fuente de verdad para cualquier dato de la factura que no sea uno de esos campos fijos, y siempre se puede descargar y consultar a mano.

## Análisis y decisiones técnicas

- **Se descarta explícitamente cualquier tabla o columna para "campos dinámicos" detectados por OCR** (una versión anterior de este documento definía una tabla Entidad-Atributo-Valor, `factura_campos_dinamicos`, para ese propósito — ver `V31__eliminar_campos_dinamicos_facturas.sql`). En la práctica, con facturas reales (tickets de venta con muchas líneas: código de barras, datos de cliente, líneas de producto...) ese mecanismo generaba decenas de filas de ruido por factura en vez de datos útiles, exactamente el problema que se quería evitar de "una entrada por cada valor de la factura". Guardar el PDF/imagen original y dejarlo descargable resuelve la misma necesidad (poder consultar cualquier dato que no esté entre las columnas fijas) sin ese ruido.
- **`estado_extraccion` con 5 valores, incluyendo `'MANUAL'`**: cubre también las facturas dadas de alta en `fase2-1` (antes de que existiera OCR) sin dejar la columna nullable — `'MANUAL'` significa explícitamente "esta factura no pasó por el pipeline de OCR", distinto de `'PENDIENTE'` (fila creada pero extracción aún no confirmada — en la práctica no debería persistir en ese estado más que instantáneamente, ver `back/fase2-3-back.md`) o `'ERROR'`.
- **`DEFAULT 'MANUAL'` en el `ALTER TABLE`**: todas las filas ya existentes de `fase2-1` quedan correctamente clasificadas sin necesitar un `UPDATE` de backfill aparte.
- **Sin columna de texto OCR crudo**: una versión anterior guardaba también `datos_extraidos_raw` (JSON con el texto OCR + valores propuestos) para auditoría — se elimina en `V31` por el mismo motivo que los campos dinámicos: no se usaba para nada más que depuración interna y el PDF original ya cubre esa necesidad de forma más simple.
- **Nota (fuera de alcance de esta fase)**: si en el futuro se necesita el listado de productos/líneas comprados por factura (p. ej. para una vista de almacén/inventario), eso requiere un modelo distinto — una tabla de líneas de factura con cantidad/producto/precio por fila, no un campo dinámico genérico — y una fase propia, no reutiliza nada de este documento.

> **Nota histórica (revisión posterior a esta fase)**: probar el alta y la extracción con facturas reales llevó a eliminar por completo `producto`, `fecha_factura` y `fecha_vencimiento` — no solo hacerlos nullable/opcionales como hizo `V32` con `producto`. Razones: (1) `producto` de texto libre nunca llegó a usarse — las facturas reales tienen varias líneas de artículos y un único campo no las representa, y la vía correcta para eso es una futura vista de almacén con su propio modelo de líneas, no este campo; (2) `fecha_factura` ("fecha de venta") y `fecha_impresion` resultaron ser, en la práctica, casi siempre el mismo dato en las facturas de proveedor que de verdad recibe el restaurante — mantener las dos como campos independientes solo generaba confusión sobre cuál usar para `mes`/`anio`, así que `fecha_impresion` pasa a ser la fecha de referencia única (obligatoria) y `fecha_factura` desaparece; (3) `fecha_vencimiento` (de la `V27` original) nunca se usó en ningún filtro, cálculo ni pantalla — puro campo muerto. Ver `V34`/`V35` más abajo.

- **`V34` hace obligatoria `fecha_impresion` antes de borrar `fecha_factura`**: como `fecha_impresion` pasa a ser la única fecha de referencia (sustituye a `fecha_factura` para `mes`/`anio`), la migración primero rellena con un `UPDATE` cualquier fila donde `fecha_impresion` fuera `NULL` (usando el valor de `fecha_factura`, que en `V27` era `NOT NULL`) y solo entonces la convierte en `NOT NULL` — evita dejar filas existentes con la nueva columna de referencia vacía.
- **`V35` es una migración aparte de `V34`** aunque ambas eliminan campos muertos de la misma tabla en la misma revisión: `fecha_vencimiento` no tiene ninguna dependencia de datos previos (a diferencia de `fecha_impresion`/`fecha_factura`, que sí necesitan el `UPDATE` de `V34`), así que se mantiene como su propio paso, más simple de leer y de revertir si hiciera falta.

## Desglose de subtareas

1. **[BBDD, media]** `V28__add_ocr_columns_a_facturas.sql`.
2. **[BBDD, baja]** `V31__eliminar_campos_dinamicos_facturas.sql` (revierte `V29`/parte de `V28`, ver más abajo).
3. **[BBDD, baja]** `V32__hacer_producto_nullable_en_facturas.sql`: tras probar el alta con facturas de varias líneas de artículos, forzar un único valor de texto en `producto` no tiene sentido — pasa a `NULL` opcional (el modal de alta deja de pedirlo, ver `front/fase2-3-front.md`).
4. **[BBDD, baja]** `V33__add_fecha_impresion_entrega_a_facturas.sql`: añade `fecha_impresion`/`fecha_entrega` (ambas `DATE NULL`) — un ticket de venta al por mayor puede traer estas dos fechas además de la de venta/emisión (`fecha_factura`, de la que se seguían derivando `mes`/`anio` en su momento).
5. **[BBDD, media]** `V34__eliminar_producto_y_fecha_factura_de_facturas.sql` (revisión posterior, ver nota histórica arriba): backfill de `fecha_impresion` desde `fecha_factura` donde falte, luego `fecha_impresion NOT NULL` y elimina `producto`/`fecha_factura`.
6. **[BBDD, baja]** `V35__eliminar_fecha_vencimiento_de_facturas.sql`: elimina `fecha_vencimiento` (campo muerto, nunca usado en filtros/cálculos/pantallas).

## DDL

### `V28__add_ocr_columns_a_facturas.sql`

```sql
ALTER TABLE facturas
    ADD COLUMN estado_extraccion    VARCHAR(20) NOT NULL DEFAULT 'MANUAL' AFTER estado_pago,
    ADD COLUMN datos_extraidos_raw  JSON        NULL     AFTER estado_extraccion;

ALTER TABLE facturas
    ADD CONSTRAINT chk_facturas_estado_extraccion
        CHECK (estado_extraccion IN ('MANUAL', 'PENDIENTE', 'PROCESADA', 'ERROR', 'REVISADA'));

CREATE INDEX idx_facturas_estado_extraccion ON facturas (estado_extraccion);
```

### `V29__create_factura_campos_dinamicos_table.sql` (histórica, revertida por `V31`)

Creaba la tabla `factura_campos_dinamicos` (patrón Entidad-Atributo-Valor). Se mantiene el archivo de migración histórico sin modificar (Flyway no permite editar migraciones ya aplicadas) pero su efecto queda revertido por `V31`.

### `V31__eliminar_campos_dinamicos_facturas.sql`

```sql
DROP TABLE factura_campos_dinamicos;

ALTER TABLE facturas DROP COLUMN datos_extraidos_raw;
```

### `V32__hacer_producto_nullable_en_facturas.sql`

```sql
ALTER TABLE facturas MODIFY COLUMN producto VARCHAR(255) NULL;
```

### `V33__add_fecha_impresion_entrega_a_facturas.sql`

```sql
ALTER TABLE facturas
    ADD COLUMN fecha_impresion DATE NULL AFTER fecha_factura,
    ADD COLUMN fecha_entrega   DATE NULL AFTER fecha_impresion;
```

### `V34__eliminar_producto_y_fecha_factura_de_facturas.sql`

```sql
UPDATE facturas SET fecha_impresion = fecha_factura WHERE fecha_impresion IS NULL;
ALTER TABLE facturas
    MODIFY COLUMN fecha_impresion DATE NOT NULL,
    DROP COLUMN producto,
    DROP COLUMN fecha_factura;
```

### `V35__eliminar_fecha_vencimiento_de_facturas.sql`

```sql
ALTER TABLE facturas DROP COLUMN fecha_vencimiento;
```

**Mapeo de columnas ↔ campo de entidad JPA / DTO** (debe coincidir con `back/fase2-3-back.md`; estado tras `V35`, última migración de esta fase):

| Columna | Campo entidad | Tipo Java | Tipo TS (front) |
|---|---|---|---|
| `facturas.estado_extraccion` | `estadoExtraccion` (`@Enumerated(EnumType.STRING)`) | `EstadoExtraccion {MANUAL, PENDIENTE, PROCESADA, ERROR, REVISADA}` | `'MANUAL' \| 'PENDIENTE' \| 'PROCESADA' \| 'ERROR' \| 'REVISADA'` |
| `facturas.fecha_impresion` (`V34`: `NOT NULL`, fecha de referencia única — sustituye a `fecha_factura`) | `fechaImpresion` | `LocalDate` | `string` |
| `facturas.fecha_entrega` | `fechaEntrega` | `LocalDate` (nullable) | `string \| null` |
| ~~`facturas.producto`~~ (eliminada en `V34`) | — | — | — |
| ~~`facturas.fecha_factura`~~ (eliminada en `V34`, sustituida por `fecha_impresion`) | — | — | — |
| ~~`facturas.fecha_vencimiento`~~ (eliminada en `V35`, campo muerto) | — | — | — |

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase2-1-bbdd.md`: `ALTER TABLE facturas` requiere que la tabla ya exista.
- **Dependencia dura** de `V31` con `V28`/`V29`: solo puede aplicarse después de que ambas hayan creado lo que revierte.
- **Dependencia dura** de `V34` con `V33`: el backfill de `fecha_impresion` solo tiene sentido porque `V33` la creó como `DATE NULL`.

## Criterios de aceptación técnicos

- Tras `V28`, todas las facturas creadas antes de esta migración tienen `estado_extraccion = 'MANUAL'` sin necesitar ningún `UPDATE` manual.
- Insertar/actualizar una factura con `estado_extraccion = 'DESCONOCIDO'` falla por `chk_facturas_estado_extraccion`.
- Tras `V31`, la tabla `factura_campos_dinamicos` y la columna `facturas.datos_extraidos_raw` no existen.
- Tras `V32`, insertar una factura sin `producto` no falla por restricción `NOT NULL` (columna eliminada por completo poco después en `V34`, ver abajo).
- Tras `V33`, `facturas.fecha_impresion`/`fecha_entrega` aceptan `NULL`.
- Tras `V34`, ninguna fila tiene `fecha_impresion` a `NULL` (backfill desde `fecha_factura`), la columna pasa a `NOT NULL`, y `facturas.producto`/`facturas.fecha_factura` ya no existen.
- Tras `V35`, `facturas.fecha_vencimiento` ya no existe.
