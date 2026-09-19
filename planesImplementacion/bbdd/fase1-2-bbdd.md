# Plan de Implementación — Fase 1.2 (Base de datos): Vacaciones

Depende de [`fase0-bbdd.md`](./fase0-bbdd.md) (`empleados`, `tipos_usuarios`, `vistas`, `permisos`). Fuente de verdad de nombres para [`../back/fase1-2-back.md`](../back/fase1-2-back.md) y [`../front/fase1-2-front.md`](../front/fase1-2-front.md). Reutilizado sin cambios de esquema por [`../back/fase1-4-back.md`](../back/fase1-4-back.md) (vista Admin de vacaciones).

## Contexto y objetivo

Modelar los días de vacaciones anuales de cada empleado, el catálogo de días festivos (para pintarlos en el calendario) y las solicitudes de vacaciones con su flujo de estados (`PENDIENTE` → `APROBADA`/`RECHAZADA`).

## Análisis y decisiones técnicas

- Los "23 días por defecto" son días **laborables** (lunes a viernes, excluyendo festivos), no días naturales — es la convención española habitual (30 días naturales ≈ 22–23 laborables). El cálculo de días laborables entre dos fechas se hace en el backend (`back/fase1-2-back.md`), nunca en el front ni en SQL.
- `dias_vacaciones_anuales` se añade como columna en `empleados` (no una tabla de configuración global) para permitir de entrada, sin migración futura, que algún empleado tenga un número distinto de días (p.ej. por convenio o antigüedad), aunque hoy todos arranquen en 23. **Revisado en `fase1-4-bbdd.md`**: cuando el usuario pidió poder editar el valor por defecto en caliente (no solo por empleado), esta columna pasó a nullable y se añadió `configuracion_vacaciones` para el valor global — la columna en sí no se elimina, sigue siendo el mecanismo de "número distinto por empleado" previsto aquí.
- **Prorrateo del año de alta** (confirmado con el usuario): el cupo efectivo del primer año de un empleado se prorratea según su `empleados.fecha_alta` (columna ya existente desde `fase0-bbdd.md`, no requiere ninguna columna nueva). El cálculo vive enteramente en `back/fase1-2-back.md` (`VacacionesService.calcularCupoAnual`), no se persiste ningún "cupo ya calculado" — así, si `dias_vacaciones_anuales` cambia más adelante, el prorrateo se recalcula solo, sin migración de datos.
- `dias_festivos` es una tabla plana de fechas (sin columna de "ámbito"/sede): coherente con la asunción de un solo restaurante ya fijada en `plan-implementacion-general.md`.
- Necesita una vista nueva en el catálogo de permisos (`VACACIONES`), con el mismo patrón que `FICHAJES`: `ADMIN` con `EDICION`, `EMPLEADO` con `EDICION` (sobre sus propias solicitudes; igual que en Fichajes, "solo las suyas" es lógica de negocio del backend, no de esta tabla).
- El mantenimiento del catálogo de festivos (añadir los del año siguiente) se deja como **tarea manual de seed por ahora** — no se construye una pantalla de administración de festivos en esta fase, no estaba entre las 4 vistas solicitadas; ver "Riesgos".

## Desglose de subtareas

1. **[BBDD, baja]** `V7__add_dias_vacaciones_a_empleados.sql`.
2. **[BBDD, baja]** `V8__create_dias_festivos_table.sql`.
3. **[BBDD, media]** `V9__create_solicitudes_vacaciones_table.sql`.
4. **[BBDD, baja]** `V10__seed_vista_vacaciones.sql`.
5. **[BBDD, baja]** `V11__seed_dias_festivos_2026.sql` (festivos nacionales de España de ejemplo; ver nota en "Riesgos" sobre mantenimiento anual).

## DDL

### `V7__add_dias_vacaciones_a_empleados.sql`

```sql
ALTER TABLE empleados
    ADD COLUMN dias_vacaciones_anuales SMALLINT NOT NULL DEFAULT 23;
```

### `V8__create_dias_festivos_table.sql`

```sql
CREATE TABLE dias_festivos (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha        DATE          NOT NULL,
    descripcion  VARCHAR(150)  NOT NULL,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_dias_festivos_fecha UNIQUE (fecha)
);
```

### `V9__create_solicitudes_vacaciones_table.sql`

```sql
CREATE TABLE solicitudes_vacaciones (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    empleado_id            BIGINT        NOT NULL,
    fecha_inicio           DATE          NOT NULL,
    fecha_fin              DATE          NOT NULL,
    dias_solicitados       SMALLINT      NOT NULL,
    estado                 VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    fecha_solicitud        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_resolucion       TIMESTAMP     NULL,
    resuelta_por_admin_id  BIGINT        NULL,
    motivo_rechazo         VARCHAR(255)  NULL,
    created_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_solicitudes_vacaciones_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id),
    CONSTRAINT fk_solicitudes_vacaciones_admin FOREIGN KEY (resuelta_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT chk_solicitudes_vacaciones_estado CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    CONSTRAINT chk_solicitudes_vacaciones_fechas CHECK (fecha_fin >= fecha_inicio)
);

CREATE INDEX idx_solicitudes_vacaciones_empleado_estado ON solicitudes_vacaciones (empleado_id, estado);
```

### `V10__seed_vista_vacaciones.sql`

```sql
INSERT INTO vistas (codigo, nombre, descripcion) VALUES
    ('VACACIONES', 'Vacaciones', 'Solicitud y aprobación de días de vacaciones');

INSERT INTO permisos (tipo_usuario_id, vista_id, nivel_acceso)
SELECT tu.id, v.id, 'EDICION'
FROM tipos_usuarios tu
JOIN vistas v ON v.codigo = 'VACACIONES'
WHERE tu.codigo IN ('ADMIN', 'EMPLEADO');
```

### `V11__seed_dias_festivos_2026.sql`

```sql
INSERT INTO dias_festivos (fecha, descripcion) VALUES
    ('2026-01-01', 'Año Nuevo'),
    ('2026-01-06', 'Epifanía del Señor'),
    ('2026-04-03', 'Viernes Santo'),
    ('2026-05-01', 'Fiesta del Trabajo'),
    ('2026-08-15', 'Asunción de la Virgen'),
    ('2026-10-12', 'Fiesta Nacional de España'),
    ('2026-11-02', 'Todos los Santos (traslado)'),
    ('2026-12-07', 'Puente de la Constitución (traslado)'),
    ('2026-12-08', 'Inmaculada Concepción'),
    ('2026-12-25', 'Natividad del Señor');
```

**Mapeo de columnas ↔ campo de entidad JPA / DTO** (debe coincidir con `back/fase1-2-back.md`):

| Tabla | Columna | Campo entidad/DTO | Tipo Java | Tipo TS (front) |
|---|---|---|---|---|
| `empleados` | `dias_vacaciones_anuales` | `diasVacacionesAnuales` | `short`/`Integer` | `number` |
| `dias_festivos` | `fecha` | `fecha` | `LocalDate` | `string` |
| `solicitudes_vacaciones` | `estado` | `estado` | `EstadoSolicitud` (enum `PENDIENTE\|APROBADA\|RECHAZADA`) | `'PENDIENTE' \| 'APROBADA' \| 'RECHAZADA'` |
| `solicitudes_vacaciones` | resto de columnas | `empleado` (`@ManyToOne`), `fechaInicio`, `fechaFin`, `diasSolicitados`, `fechaSolicitud`, `fechaResolucion`, `resueltaPorAdminId`, `motivoRechazo` | — | — |

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase0-bbdd.md`: necesita `empleados`, `tipos_usuarios`, `vistas`, `permisos` ya creadas y sembradas.
- **Riesgo medio — mantenimiento del catálogo de festivos**: `V11` siembra solo 2026; cada año hace falta una migración nueva (`V{n}__seed_dias_festivos_2027.sql`, etc.) o, en una fase futura, una pantalla de administración de festivos (no solicitada en esta fase). Mitigación mínima mientras tanto: recordatorio operativo a final de año, documentado aquí para no perderlo de vista.
- **Riesgo bajo**: si se cambia `dias_vacaciones_anuales` de un empleado a mitad de año con solicitudes ya aprobadas, el cálculo de "disponibles" (`back/fase1-2-back.md`) siempre recalcula contra el valor **actual** de la columna, no contra un histórico — asumido como comportamiento correcto (se aplica el nuevo cupo desde el momento del cambio).

## Criterios de aceptación técnicos

- `empleados.dias_vacaciones_anuales` existe, `NOT NULL`, con `DEFAULT 23`, y todos los empleados ya existentes lo heredan sin necesidad de un `UPDATE` manual.
- Insertar dos `dias_festivos` con la misma fecha falla por `uk_dias_festivos_fecha`.
- Insertar una `solicitud_vacaciones` con `fecha_fin < fecha_inicio` falla por `chk_solicitudes_vacaciones_fechas`.
- Insertar una `solicitud_vacaciones` con `estado` distinto de los tres valores permitidos falla por el `CHECK`.
- Tras `V10`, tanto `ADMIN` como `EMPLEADO` tienen `EDICION` en `VACACIONES`.
