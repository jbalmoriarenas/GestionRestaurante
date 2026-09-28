# Plan de Implementación — Cumplimiento Registro Horario 2027 (Base de datos)

Depende de [`fase0-bbdd.md`](./fase0-bbdd.md) (`empleados`, catálogo de permisos), [`fase1-1-bbdd.md`](./fase1-1-bbdd.md) (`fichajes`, `configuracion_ubicacion`) y [`fase1-2-bbdd.md`](./fase1-2-bbdd.md) (`solicitudes_vacaciones`). Numerado a partir de **V42**, asumiendo que [`integracion_verifactu-bbdd.md`](./integracion_verifactu-bbdd.md) (V39-V41) se aplica primero — si se decide implementar este plan antes, renumerar en consecuencia. Fuente de verdad de nombres para [`../back/fichajes_vacaciones_2027-back.md`](../back/fichajes_vacaciones_2027-back.md).

## Contexto y objetivo

España tiene ya en vigor desde 2019 (RDL 8/2019, art. 34.9 ET) la obligación de registrar diariamente la jornada de cada empleado. Hay un nuevo Real Decreto en tramitación (aprobación esperada durante 2026, entrada en vigor estimada entre marzo y abril de 2027) que endurece los requisitos técnicos de ese registro: **inmutabilidad**, **trazabilidad completa de cualquier modificación** (quién, cuándo y qué valor había antes), **acceso remoto** para la Inspección de Trabajo (ITSS) y el propio empleado, y **custodia de 4 años**. Esta fase adapta el módulo `Fichajes` a esos requisitos.

**Vacaciones no es el objeto directo de esa norma** (que regula la jornada trabajada, no las ausencias) — se incluye en este mismo plan porque el módulo actual tiene la misma debilidad de fondo (un `DELETE` real al cancelar una solicitud, sin dejar rastro) y porque, en la práctica, una inspección de jornada suele cruzar los días trabajados con los días de ausencia justificada (vacaciones) para comprobar que el cuadrante es coherente. Se aplica aquí el mismo principio de "nada se borra, todo queda trazado", como refuerzo razonable, no como un mandato legal distinto.

Dos migraciones nuevas de esquema para `Fichajes` (tabla de historial de correcciones + nombre del centro de trabajo) y una para `Vacaciones` (estado `CANCELADA` en vez de borrado).

## Análisis y decisiones técnicas

- **Tabla `fichaje_historial_correccion` nueva, append-only, en vez de versionar `fichajes` con Hibernate Envers o similar**: para el tamaño de este proyecto (una tabla, un único punto de escritura que la genera — `FichajeService.corregir`, `back/fichajes_vacaciones_2027-back.md`), una tabla de historial explícita y sencilla de leer/exportar es más transparente que introducir una librería de auditoría genérica que auditaría todo el proyecto sin que se haya pedido.
- **Cada fila del historial guarda el snapshot completo (antes Y después), no solo el delta**: así cada fila es autocontenida y legible sin tener que reconstruir el estado recorriendo filas anteriores — coherente con el requisito de que la Inspección pueda leer directamente "qué había, qué se puso, quién y cuándo" sin herramientas adicionales.
- **`fichaje_historial_correccion` no tiene ningún endpoint ni mecanismo de `DELETE`/`UPDATE` a nivel de aplicación** (ver `back/fichajes_vacaciones_2027-back.md`): es el propio requisito de inmutabilidad — si se pudiera editar el historial de correcciones, dejaría de ser fiable como prueba.
- **`nombre_centro` se añade a `configuracion_ubicacion` (`ALTER TABLE`), no una tabla `centros_trabajo` nueva**: el negocio tiene un único centro de trabajo (fila única `id = 1`, ya existente) — el nuevo decreto exige poder identificar el centro en el registro, y con un solo centro basta una columna informativa, no un modelo de múltiples centros que no se ha pedido. Si el negocio abriera un segundo local, sería una fase aparte (habría que replantear `configuracion_ubicacion` como fila única, no solo añadir una columna).
- **`solicitudes_vacaciones.estado` añade `CANCELADA` al `CHECK`, y `VacacionesService.cancelar()` deja de hacer `DELETE`** (`back/fichajes_vacaciones_2027-back.md`): mismo principio de no destruir información — una solicitud cancelada por el empleado sigue existiendo, con quién y cuándo la canceló, en vez de desaparecer sin rastro. No cambia el cálculo de cupo disponible (`CANCELADA` queda fuera de `ESTADOS_QUE_CONSUMEN_CUPO`, igual que `RECHAZADA` ya lo está).
- **`cancelada_por_empleado_id` como columna separada de `resuelta_por_admin_id`**: una cancelación la ejecuta el propio empleado sobre su solicitud `PENDIENTE` (`VacacionesService.cancelar`, hoy exige que sea el dueño); no tiene sentido reutilizar la columna que identifica al Admin que aprueba/rechaza, son dos acciones y dos actores distintos.

## Desglose de subtareas

1. **[BBDD, media]** `V42__create_fichaje_historial_correccion_table.sql`.
2. **[BBDD, baja]** `V43__add_nombre_centro_a_configuracion_ubicacion.sql`.
3. **[BBDD, media]** `V44__add_estado_cancelada_a_solicitudes_vacaciones.sql`.

## DDL

### `V42__create_fichaje_historial_correccion_table.sql`

```sql
CREATE TABLE fichaje_historial_correccion (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    fichaje_id                BIGINT        NOT NULL,
    hora_entrada_anterior     DATETIME      NOT NULL,
    hora_salida_anterior      DATETIME      NULL,
    observaciones_anterior    VARCHAR(255)  NULL,
    hora_entrada_nueva        DATETIME      NOT NULL,
    hora_salida_nueva         DATETIME      NULL,
    observaciones_nueva       VARCHAR(255)  NULL,
    corregido_por_admin_id     BIGINT        NOT NULL,
    fecha_correccion           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_fichaje_historial_fichaje FOREIGN KEY (fichaje_id) REFERENCES fichajes (id),
    CONSTRAINT fk_fichaje_historial_admin FOREIGN KEY (corregido_por_admin_id) REFERENCES empleados (id)
);

CREATE INDEX idx_fichaje_historial_fichaje ON fichaje_historial_correccion (fichaje_id);
```

### `V43__add_nombre_centro_a_configuracion_ubicacion.sql`

```sql
ALTER TABLE configuracion_ubicacion
    ADD COLUMN nombre_centro VARCHAR(150) NULL;

-- Nullable: la fila unica (id = 1) ya existe desde fase1-1-bbdd.md; este ALTER no puede
-- forzar un valor sin romper filas existentes. El Admin lo rellena desde el panel de
-- ubicacion (back/fichajes_vacaciones_2027-back.md); mientras este a NULL, el informe
-- combinado y el export (back/fichajes_vacaciones_2027-back.md) muestran el centro como
-- "Centro de trabajo no configurado".
```

### `V44__add_estado_cancelada_a_solicitudes_vacaciones.sql`

```sql
ALTER TABLE solicitudes_vacaciones
    DROP CHECK chk_solicitudes_vacaciones_estado;

ALTER TABLE solicitudes_vacaciones
    ADD CONSTRAINT chk_solicitudes_vacaciones_estado
    CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA', 'CANCELADA'));

ALTER TABLE solicitudes_vacaciones
    ADD COLUMN fecha_cancelacion       TIMESTAMP NULL,
    ADD COLUMN cancelada_por_empleado_id BIGINT    NULL;

ALTER TABLE solicitudes_vacaciones
    ADD CONSTRAINT fk_solicitudes_vacaciones_cancelada_por
    FOREIGN KEY (cancelada_por_empleado_id) REFERENCES empleados (id);
```

**Mapeo de columnas ↔ campo de entidad JPA / DTO** (debe coincidir con `back/fichajes_vacaciones_2027-back.md`):

| Columna | Campo entidad | Tipo Java | Tipo TS (front) |
|---|---|---|---|
| `fichaje_historial_correccion.hora_entrada_anterior` / `_nueva` | `horaEntradaAnterior` / `horaEntradaNueva` | `LocalDateTime` | `string` |
| `fichaje_historial_correccion.hora_salida_anterior` / `_nueva` | `horaSalidaAnterior` / `horaSalidaNueva` | `LocalDateTime` (nullable) | `string \| null` |
| `fichaje_historial_correccion.corregido_por_admin_id` | `corregidoPorAdminId` | `Long` | `number` (se resuelve a nombre en el DTO de respuesta) |
| `configuracion_ubicacion.nombre_centro` | `nombreCentro` | `String` (nullable) | `string \| null` |
| `solicitudes_vacaciones.estado = 'CANCELADA'` | `EstadoSolicitud.CANCELADA` (nuevo valor de enum) | — | `'CANCELADA'` (nuevo literal de union) |
| `solicitudes_vacaciones.fecha_cancelacion` / `cancelada_por_empleado_id` | `fechaCancelacion` / `canceladaPorEmpleadoId` | `LocalDateTime` (nullable) / `Long` (nullable) | `string \| null` / — (no se expone, es implícito por ser "mi" solicitud) |

## Riesgos y dependencias

- **Dependencia dura** con `fase1-1-bbdd.md`/`fase1-2-bbdd.md`: sin `fichajes`/`solicitudes_vacaciones`, estas migraciones no tienen nada que extender.
- **Riesgo bajo**: `fichaje_historial_correccion` crecerá sin límite (ninguna fila se borra nunca) — mismo criterio ya aceptado para `fichajes`/`solicitudes_vacaciones`, que tampoco se purgan; si el volumen preocupa a largo plazo, es una política de archivado fuera de alcance de este documento.
- **Riesgo medio**: `V44` cambia un `CHECK` existente — en MySQL 8 (`DROP CHECK` + `ADD CONSTRAINT`) es una operación online sin bloqueo prolongado para el volumen de datos de este proyecto, pero conviene ejecutarla en una ventana de mantenimiento si ya hay tráfico real, no solo en local/pre.
- **No se implementa purga automática a los 4 años**: la obligación es de custodia *mínima* de 4 años, no de borrado a los 4 años — no hacer nada (conservar indefinidamente) ya cumple el mínimo legal; una política de purga sería una decisión de retención de datos aparte, no construida aquí.

## Criterios de aceptación técnicos

- Tras `V42`, corregir un fichaje debe dejar una fila nueva en `fichaje_historial_correccion` (verificado en `back/fichajes_vacaciones_2027-back.md`) — a nivel de BBDD, insertar una fila con `fichaje_id` inexistente falla por FK.
- Tras `V44`, insertar una solicitud con `estado = 'CANCELADA'` no falla; con cualquier valor fuera de la lista sigue fallando.
- Tras `V44`, una solicitud existente en `RECHAZADA`/`APROBADA` puede seguir consultándose exactamente igual que antes (columnas nuevas a `NULL`, sin migración de datos retroactiva).
