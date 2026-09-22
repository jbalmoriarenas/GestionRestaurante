# Plan de Implementación — Fase 2.3 (Base de datos): Facturas — estado de extracción y campos dinámicos

Depende de [`fase2-1-bbdd.md`](./fase2-1-bbdd.md) (tabla `facturas` debe existir). Fuente de verdad de nombres para [`../back/fase2-3-back.md`](../back/fase2-3-back.md).

## Contexto y objetivo

Dos migraciones: (a) ampliar `facturas` con el estado del pipeline de extracción automática y el JSON crudo devuelto por el OCR; (b) una tabla nueva, `factura_campos_dinamicos`, para los campos que el OCR detecta en una factura pero que no existen como columna fija — es la implementación concreta de "si hay campos nuevos que no existían en la BBDD, se crean automáticamente" que pide el usuario.

## Análisis y decisiones técnicas

- **Se descarta explícitamente `ALTER TABLE ADD COLUMN` dinámico** (crear una columna nueva en `facturas` por cada etiqueta que el OCR detecte por primera vez) como forma de cumplir el requisito de "campos nuevos automáticos": es inseguro (el nombre de columna vendría de texto reconocido por OCR, con todo el riesgo de sanitización que implica ejecutar DDL con datos no confiables) y no escala (cada factura de formato distinto añadiría columnas nuevas sin límite, la mayoría `NULL` para el resto de filas) ni es compatible con una entidad JPA estática (`Factura` tendría que recompilarse por cada campo nuevo, lo cual es contradictorio con "automático"). La tabla `factura_campos_dinamicos` (patrón Entidad-Atributo-Valor) consigue el mismo efecto visible para el usuario — un campo nuevo aparece "solo", sin tocar código ni desplegar nada — de forma segura y estándar.
- **`estado_extraccion` con 5 valores, incluyendo `'MANUAL'`**: cubre también las facturas dadas de alta en `fase2-1` (antes de que existiera OCR) sin dejar la columna nullable — `'MANUAL'` significa explícitamente "esta factura no pasó por el pipeline de OCR", distinto de `'PENDIENTE'` (fila creada pero extracción aún no confirmada — en la práctica no debería persistir en ese estado más que instantáneamente, ver `back/fase2-3-back.md`) o `'ERROR'`.
- **`DEFAULT 'MANUAL'` en el `ALTER TABLE`**: todas las filas ya existentes de `fase2-1` quedan correctamente clasificadas sin necesitar un `UPDATE` de backfill aparte.
- **`datos_extraidos_raw` como `JSON` nativo de MySQL, nullable**: solo se rellena en facturas creadas vía OCR; guarda la respuesta cruda de la extracción (texto OCR + valores propuestos) para auditoría/depuración, nunca se usa para construir la UI (esa usa los campos estructurados + `factura_campos_dinamicos`).
- **`factura_campos_dinamicos` con `ON DELETE CASCADE`**: aunque `fase2-1`/`fase2-3` no incluyen borrado de facturas, se deja la integridad referencial correcta desde el principio para no depender de que quien añada borrado en el futuro se acuerde de limpiar también esta tabla.
- **`UNIQUE (factura_id, nombre_campo)`**: evita que una misma factura acumule dos filas para el mismo campo (p. ej. si el usuario confirma la extracción dos veces por error); `back/fase2-3-back.md` hace *upsert*, no `INSERT` ciego.

## Desglose de subtareas

1. **[BBDD, media]** `V28__add_ocr_columns_a_facturas.sql`.
2. **[BBDD, baja]** `V29__create_factura_campos_dinamicos_table.sql`.

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

### `V29__create_factura_campos_dinamicos_table.sql`

```sql
CREATE TABLE factura_campos_dinamicos (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    factura_id      BIGINT        NOT NULL,
    nombre_campo    VARCHAR(100)  NOT NULL,
    valor           VARCHAR(500)  NULL,
    fecha_creacion  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_campos_dinamicos_factura FOREIGN KEY (factura_id) REFERENCES facturas (id) ON DELETE CASCADE,
    CONSTRAINT uk_campos_dinamicos_factura_nombre UNIQUE (factura_id, nombre_campo)
);

CREATE INDEX idx_campos_dinamicos_factura ON factura_campos_dinamicos (factura_id);
```

**Mapeo de columnas ↔ campo de entidad JPA / DTO** (debe coincidir con `back/fase2-3-back.md`):

| Columna | Campo entidad | Tipo Java | Tipo TS (front) |
|---|---|---|---|
| `facturas.estado_extraccion` | `estadoExtraccion` (`@Enumerated(EnumType.STRING)`) | `EstadoExtraccion {MANUAL, PENDIENTE, PROCESADA, ERROR, REVISADA}` | `'MANUAL' \| 'PENDIENTE' \| 'PROCESADA' \| 'ERROR' \| 'REVISADA'` |
| `facturas.datos_extraidos_raw` | `datosExtraidosRaw` (`@JdbcTypeCode(SqlTypes.JSON)`) | `String` (JSON serializado) | no se expone tal cual al front, solo con fines de depuración interna |
| `factura_campos_dinamicos.factura_id` | `factura` (`@ManyToOne`) en `FacturaCampoDinamico` | `Factura` | — |
| `factura_campos_dinamicos.nombre_campo` / `valor` | `nombreCampo` / `valor` | `String` | `{clave: string, valor: string}` (DTO) |

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase2-1-bbdd.md`: `ALTER TABLE facturas` requiere que la tabla ya exista.
- **Riesgo bajo**: `nombre_campo` sin normalización (mayúsculas/tildes/espacios) puede generar variantes casi-duplicadas del mismo concepto entre facturas distintas (p. ej. `"CUPS"` vs `"Cups"` vs `"N. CUPS"`) — no se resuelve en esta fase (normalizar requeriría un catálogo de sinónimos o un paso de limpieza adicional, sobre-ingeniería para el volumen esperado); documentado como mejora futura si se vuelve un problema real al revisar los datos.
- **Riesgo bajo**: `valor VARCHAR(500)` podría truncar un campo dinámico inusualmente largo (poco probable en una etiqueta de factura, pero posible); si ocurre en la práctica, ampliar a `TEXT` es una migración trivial sin impacto en datos existentes.

## Criterios de aceptación técnicos

- Tras `V28`, todas las facturas creadas antes de esta migración tienen `estado_extraccion = 'MANUAL'` sin necesitar ningún `UPDATE` manual.
- Insertar/actualizar una factura con `estado_extraccion = 'DESCONOCIDO'` falla por `chk_facturas_estado_extraccion`.
- Tras `V29`, insertar dos filas de `factura_campos_dinamicos` con el mismo `(factura_id, nombre_campo)` falla por `uk_campos_dinamicos_factura_nombre`.
- Borrar una factura (si en el futuro se añade esa capacidad) borra en cascada sus filas de `factura_campos_dinamicos`.
