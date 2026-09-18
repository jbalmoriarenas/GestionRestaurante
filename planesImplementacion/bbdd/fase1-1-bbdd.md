# Plan de Implementación — Fase 1.1 (Base de datos): Fichaje

Depende del esquema de [`fase0-bbdd.md`](./fase0-bbdd.md) (tabla `empleados` ya existente). Es la fuente de verdad de nombres para [`../back/fase1-1-back.md`](../back/fase1-1-back.md) y [`../front/fase1-1-front.md`](../front/fase1-1-front.md), y la reutiliza sin cambios [`../back/fase1-3-back.md`](../back/fase1-3-back.md) (vista Admin de fichajes).

## Contexto y objetivo

Modelar el fichaje de entrada/salida de un empleado (estilo Woffu: un botón "Iniciar" / "Finalizar"), garantizando a nivel de base de datos — no solo de código — que un empleado nunca pueda tener dos fichajes abiertos simultáneos, guardando la ubicación GPS con la que se fichó la entrada (auditoría de la verificación de geovalla, ver `back/fase1-1-back.md`), y persistiendo la ubicación/radio del restaurante en BBDD (no en `application.properties`) para que el **Admin pueda cambiarla sin redesplegar** (confirmado con el usuario, ver `back/fase1-3-back.md` para el endpoint de edición).

## Análisis y decisiones técnicas

- `lat_entrada`/`lng_entrada` son **nullable**: un fichaje creado/corregido manualmente por el Admin (`fase1-3-back.md`) no tiene coordenadas propias (el Admin no está físicamente fichando), así que deben poder quedar vacías sin violar ninguna restricción.
- `DECIMAL(9,6)` da una precisión de ~11 cm por grado decimal, más que suficiente para coordenadas GPS (el propio GPS de un móvil rara vez baja de varios metros de error) — no hace falta más precisión ni `DOUBLE`.
- Solo se auditan las coordenadas de la **entrada**, no de la salida: la verificación de geovalla (ver `back/fase1-1-back.md`) solo aplica al iniciar el turno, no al finalizarlo.
- **`configuracion_ubicacion` es una tabla de una sola fila** (`id` fijo = 1, forzado por `CHECK`), no una fila por restaurante: coherente con la asunción de un solo restaurante ya fijada en el plan general. Guardar esto en BBDD (en vez de en `application.properties`, como se había planteado inicialmente) es lo que permite que el Admin la edite **en caliente**, sin reiniciar el backend — el cambio lo pide explícitamente el usuario.
- **Seed intencionalmente "fail-closed"**: la fila inicial se siembra con `lat=0, lng=0` (un punto en mitad del océano, no un valor real), lo que hace que **todos** los fichajes fallen por "fuera de rango" hasta que el Admin configure la ubicación real por primera vez. Se prefiere este comportamiento a sembrar una coordenada de ejemplo que pareciera válida y que alguien olvidara cambiar — un fallo evidente desde el primer fichaje es mejor que una geovalla que "funciona" apuntando al sitio equivocado sin que nadie lo note.

## Desglose de subtareas

1. **[BBDD, media]** `V6__create_fichajes_table.sql`: tabla `fichajes` + índice único parcial sobre "fichaje abierto" + columnas de auditoría de ubicación de entrada.
2. **[BBDD, baja]** `V13__create_configuracion_ubicacion_table.sql`: tabla de configuración de una sola fila con la ubicación/radio del restaurante, editable por el Admin (`back/fase1-3-back.md`). Numerada `V13` porque se añade después de que `fase1-2-bbdd.md` (`V7`–`V11`) y `fase1-3-bbdd.md` (`V12`) ya estuvieran especificadas; no hay conflicto de orden, solo se aplica más tarde en la secuencia.

## DDL

### `V6__create_fichajes_table.sql`

```sql
CREATE TABLE fichajes (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    empleado_id             BIGINT        NOT NULL,
    fecha                   DATE          NOT NULL,
    hora_entrada            DATETIME      NOT NULL,
    hora_salida             DATETIME      NULL,
    horas_calculadas        DECIMAL(5,2)  NULL,
    lat_entrada             DECIMAL(9,6)  NULL,
    lng_entrada             DECIMAL(9,6)  NULL,
    observaciones           VARCHAR(255)  NULL,
    corregido_por_admin     TINYINT(1)    NOT NULL DEFAULT 0,
    corregido_por_admin_id  BIGINT        NULL,
    fecha_correccion        TIMESTAMP     NULL,
    created_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_fichajes_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id),
    CONSTRAINT fk_fichajes_corregido_por FOREIGN KEY (corregido_por_admin_id) REFERENCES empleados (id)
);

CREATE INDEX idx_fichajes_empleado_fecha ON fichajes (empleado_id, fecha);

-- Columna generada + índice único: solo puede existir UN fichaje con hora_salida NULL
-- por empleado. Si hora_salida no es NULL, la columna generada vale NULL (MySQL permite
-- múltiples NULL en un índice único), así que los fichajes ya cerrados nunca chocan entre
-- sí; solo choca un segundo INTENTO de fichaje abierto del mismo empleado. Esto convierte
-- la regla de negocio "no se puede fichar entrada dos veces sin fichar salida" en una
-- garantía de integridad de datos, no solo en una validación en el servicio (que también
-- se hace, en fase1-1-back.md, para devolver un 409 con mensaje claro en vez de un error
-- de constraint desnudo).
ALTER TABLE fichajes
    ADD COLUMN marca_abierto BIGINT
    GENERATED ALWAYS AS (CASE WHEN hora_salida IS NULL THEN empleado_id END) VIRTUAL;

CREATE UNIQUE INDEX uk_fichajes_abierto ON fichajes (marca_abierto);
```

### `V13__create_configuracion_ubicacion_table.sql`

```sql
CREATE TABLE configuracion_ubicacion (
    id                        BIGINT PRIMARY KEY,
    lat                       DECIMAL(9,6)  NOT NULL,
    lng                       DECIMAL(9,6)  NOT NULL,
    radio_metros              INT           NOT NULL DEFAULT 150,
    updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    actualizado_por_admin_id  BIGINT        NULL,

    CONSTRAINT fk_configuracion_ubicacion_admin FOREIGN KEY (actualizado_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT chk_configuracion_ubicacion_id_unico CHECK (id = 1)
);

-- Fila única, sembrada en (0, 0) a propósito: bloquea todos los fichajes por "fuera de
-- rango" hasta que el Admin la configure con la ubicación real (ver "Análisis y decisiones
-- técnicas" más arriba).
INSERT INTO configuracion_ubicacion (id, lat, lng, radio_metros) VALUES (1, 0, 0, 150);
```

**Mapeo de columnas ↔ campo de entidad JPA / DTO** (debe coincidir con `back/fase1-1-back.md`):

| Columna | Campo entidad | Tipo Java | Tipo TS (front) |
|---|---|---|---|
| `empleado_id` | `empleado` (`@ManyToOne`) | `Empleado` | `number` (`empleadoId` en DTO) |
| `fecha` | `fecha` | `LocalDate` | `string` (ISO `yyyy-MM-dd`) |
| `hora_entrada` | `horaEntrada` | `LocalDateTime` | `string` (ISO 8601) |
| `hora_salida` | `horaSalida` | `LocalDateTime` (nullable) | `string \| null` |
| `horas_calculadas` | `horasCalculadas` | `BigDecimal` (nullable) | `number \| null` |
| `lat_entrada` / `lng_entrada` | `latEntrada` / `lngEntrada` | `BigDecimal` (nullable) | — (solo auditoría; no se expone en el DTO de respuesta, ver `back/fase1-1-back.md`) |
| `corregido_por_admin` | `corregidoPorAdmin` | `boolean` | `boolean` |
| `corregido_por_admin_id` | `corregidoPorAdminId` | `Long` (nullable) | — (no se expone en el DTO de empleado, solo en el de admin) |
| `marca_abierto` | *(no se mapea en la entidad; es un artefacto de BBDD)* | — | — |

`marca_abierto` no debe declararse en la entidad `Fichaje` (`@Transient` implícito por omisión): es puramente un mecanismo de integridad, Hibernate no necesita saber que existe.

**`configuracion_ubicacion`** (mapeo aparte, es su propia entidad):

| Columna | Campo entidad | Tipo Java | Tipo TS (front) |
|---|---|---|---|
| `id` | `id` | `Long` (siempre `1L`) | — (no se expone) |
| `lat` / `lng` | `lat` / `lng` | `BigDecimal` | `number` |
| `radio_metros` | `radioMetros` | `Integer` | `number` |
| `updated_at` | `updatedAt` | `LocalDateTime` | `string` (ISO 8601) |
| `actualizado_por_admin_id` | `actualizadoPorAdminId` | `Long` (nullable) | — (auditoría interna) |

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase0-bbdd.md`: necesita la tabla `empleados` ya creada.
- **Riesgo bajo — columnas generadas y MySQL**: las columnas `GENERATED ... VIRTUAL` requieren MySQL 5.7.6+ (el proyecto usa 8.x vía `mysql-connector-j` 9.7.0, sin problema). Si en el futuro se migra a otro motor de BBDD, esta técnica debe rehacerse (p.ej. con un `EXCLUDE`/constraint distinto en Postgres).
- **Dependencia hacia delante**: `back/fase1-3-back.md` (vista Admin) lee esta misma tabla sin migraciones adicionales; cualquier cambio de columna aquí debe revisarse contra ese documento también. El endpoint de edición de `configuracion_ubicacion` también vive en `back/fase1-3-back.md` (capacidad de Admin), aunque la tabla se defina aquí junto al resto del dominio de fichajes.
- **Riesgo bajo**: al ser una tabla de una sola fila sin `AUTO_INCREMENT`, un `INSERT` accidental con `id` distinto de 1 fallaría por el `CHECK` — comportamiento deseado, no hay manera de que "aparezca" una segunda configuración por error.

## Criterios de aceptación técnicos

- Insertar dos filas de `fichajes` para el mismo `empleado_id` con `hora_salida = NULL` falla por `uk_fichajes_abierto`.
- Insertar múltiples filas cerradas (`hora_salida` no nulo) para el mismo empleado no falla nunca por ese índice.
- Borrar un `empleado` referenciado en `fichajes` falla por FK (no se permite dejar fichajes huérfanos).
- Tras `V13`, existe exactamente una fila en `configuracion_ubicacion` (`id=1`) con `lat=0, lng=0`; intentar insertar una segunda fila con otro `id` falla por el `CHECK`.
- Actualizar `lat`/`lng`/`radio_metros` de la fila `id=1` (vía el endpoint de `back/fase1-3-back.md`, no por SQL directo en producción) se refleja inmediatamente en la siguiente validación de `POST /api/fichajes/entrada`, sin reiniciar el backend.
