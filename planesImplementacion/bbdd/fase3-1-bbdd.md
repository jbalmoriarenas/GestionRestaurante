# Plan de Implementación — Fase 3.1 (Base de datos): Nóminas — modelo y listado

Depende únicamente de [`fase0-bbdd.md`](./fase0-bbdd.md) (tabla `empleados` y el catálogo de permisos, que ya siembra la vista `NOMINAS` con `EDICION` solo para `ADMIN` — ver "Análisis y decisiones técnicas"). No depende de la Fase 2 (Facturas): el usuario ha decidido implementar primero la Fase 3 por ser más rápida, y ambos módulos son independientes entre sí (ver `plan-implementacion-general.md`). Fuente de verdad de nombres para [`../back/fase3-1-back.md`](../back/fase3-1-back.md).

## Contexto y objetivo

Primera migración de la Fase 3 (Nóminas — vista Admin): una tabla `nominas` que registre, por empleado y periodo (mes/año), el PDF de su nómina, si ya se ha adjuntado y si ya se ha enviado por email. Las capacidades de subida de ZIP (`fase3-2`) y envío por email (`fase3-3`) reutilizan esta misma tabla sin necesitar columnas nuevas (ver ambos documentos).

## Análisis y decisiones técnicas

- **El catálogo de permisos ya está sembrado desde `fase0-bbdd.md` (`V5__seed_catalogo_permisos.sql`)**: la vista `NOMINAS` ya existe con `ADMIN` teniendo `EDICION` y `EMPLEADO` sin ninguna fila (sin acceso). A diferencia de `fase1-3-back.md`/`fase1-4-back.md` (donde `FICHAJES:EDICION`/`VACACIONES:EDICION` los tiene también `EMPLEADO`, obligando a proteger los endpoints de Admin con `EMPLEADOS:EDICION`), aquí `NOMINAS:EDICION` ya es exclusiva de `ADMIN` — no hace falta ninguna migración de permisos nueva ni ningún rodeo de autorización en el backend.
- **El estado "pendiente" (sin nómina subida todavía) no se persiste como valor de columna**: se decide **no** crear una fila en `nominas` para cada empleado y periodo por adelantado (evitaría una fila "vacía" por empleado y mes, la mayoría de las veces sin usar hasta que se sube el archivo). Una fila en `nominas` solo existe **a partir de que se sube un archivo** — por tanto, el enum `estado` de la columna solo necesita dos valores reales (`ADJUNTADA`, `ENVIADA`); el tercer estado que pide el usuario ("pendiente") es la ausencia de fila para ese empleado/periodo, calculada por el backend en el momento de listar (`back/fase3-1-back.md`), igual que `configuracion_vacaciones`/`empleados.dias_vacaciones_anuales` (`fase1-4-bbdd.md`) resuelven "sin valor propio" sin necesitar un valor mágico en la columna.
- **`ruta_archivo` es `NOT NULL`**: al no existir la fila hasta que hay un archivo, no hace falta nunca representar "adjuntada pero sin archivo" — evita el problema de mantener nulidad condicional entre columnas relacionadas.
- **Único índice por empleado + periodo** (`UNIQUE (empleado_id, periodo_mes, periodo_anio)`): un empleado solo puede tener una nómina por mes/año. Subir un nuevo archivo para un empleado/periodo que ya tiene fila **reemplaza** el archivo existente en vez de duplicar la fila (lógica de "upsert" en `back/fase3-1-back.md`).
- **`nombre_archivo_original` se guarda aparte del nombre físico en disco**: el nombre físico (generado por `FileStorageService`, ver `back/fase3-1-back.md`) es un UUID para evitar colisiones y ataques de *path traversal*; el nombre original solo sirve para mostrarlo en la UI y en la descarga (`Content-Disposition`).
- **Auditoría de subida y de envío como columnas separadas** (`subida_por_admin_id` siempre relleno; `fecha_envio`/`enviada_por_admin_id` nullable hasta que se envía): mismo patrón de auditoría ya usado en `solicitudes_vacaciones` (`resuelta_por_admin_id`/`fecha_resolucion`, `fase1-2-bbdd.md`) y en `configuracion_ubicacion`/`configuracion_vacaciones` (`actualizado_por_admin_id`).
- **Sin columna para distinguir "asignación manual" de "asignación automática por ZIP"**: da igual cómo llegó el archivo a estar asociado a un empleado, la fila resultante es idéntica en ambos casos (ver `fase3-2-back.md`) — no aporta valor de negocio persistirlo, solo sería una columna más que mantener.

## Desglose de subtareas

1. **[BBDD, baja]** `V23__create_nominas_table.sql`.

## DDL

### `V23__create_nominas_table.sql`

```sql
CREATE TABLE nominas (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    empleado_id              BIGINT        NOT NULL,
    periodo_mes              TINYINT       NOT NULL,
    periodo_anio             SMALLINT      NOT NULL,
    ruta_archivo             VARCHAR(255)  NOT NULL,
    nombre_archivo_original  VARCHAR(255)  NOT NULL,
    estado                   VARCHAR(20)   NOT NULL DEFAULT 'ADJUNTADA',
    fecha_subida             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    subida_por_admin_id      BIGINT        NOT NULL,
    fecha_envio              TIMESTAMP     NULL,
    enviada_por_admin_id     BIGINT        NULL,

    CONSTRAINT fk_nominas_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id),
    CONSTRAINT fk_nominas_subida_admin FOREIGN KEY (subida_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT fk_nominas_enviada_admin FOREIGN KEY (enviada_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT chk_nominas_periodo_mes CHECK (periodo_mes BETWEEN 1 AND 12),
    CONSTRAINT chk_nominas_estado CHECK (estado IN ('ADJUNTADA', 'ENVIADA')),
    CONSTRAINT uk_nominas_empleado_periodo UNIQUE (empleado_id, periodo_mes, periodo_anio)
);

CREATE INDEX idx_nominas_periodo ON nominas (periodo_anio, periodo_mes);
```

**Mapeo de columnas ↔ campo de entidad JPA / DTO** (debe coincidir con `back/fase3-1-back.md`):

| Columna | Campo entidad | Tipo Java | Tipo TS (front) |
|---|---|---|---|
| `empleado_id` | `empleado` (`@ManyToOne`) | `Empleado` | `empleadoId: number` (en el DTO de listado) |
| `periodo_mes` | `periodoMes` | `Integer` (1-12) | `number` |
| `periodo_anio` | `periodoAnio` | `Integer` | `number` |
| `ruta_archivo` | `rutaArchivo` | `String` | — (nunca se expone tal cual al front, solo vía el endpoint de descarga) |
| `nombre_archivo_original` | `nombreArchivoOriginal` | `String` | `string \| null` |
| `estado` | `estado` (`@Enumerated(EnumType.STRING)`) | `EstadoNomina {ADJUNTADA, ENVIADA}` | `'PENDIENTE' \| 'ADJUNTADA' \| 'ENVIADA'` (el DTO de listado añade `PENDIENTE` cuando no hay fila) |
| `fecha_subida` | `fechaSubida` | `LocalDateTime` | `string \| null` |
| `subida_por_admin_id` | `subidaPorAdminId` | `Long` | — |
| `fecha_envio` | `fechaEnvio` | `LocalDateTime` (nullable) | `string \| null` |
| `enviada_por_admin_id` | `enviadaPorAdminId` | `Long` (nullable) | — |

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase0-bbdd.md`: sin la tabla `empleados` ni el catálogo de permisos con `NOMINAS` ya sembrado, esta migración no tiene sentido.
- **Riesgo bajo**: si en el futuro se decide auditar explícitamente "esta nómina se asignó a mano" vs. "se matcheó automáticamente desde un ZIP", haría falta una columna nueva (`fase3-2-bbdd.md` correctiva) — se documenta como decisión consciente de no anticiparla sin un caso de uso real.
- **Riesgo bajo**: `periodo_mes`/`periodo_anio` como columnas separadas (en vez de un único `periodo VARCHAR(7)` tipo `'2026-01'`) hace el filtrado/ordenación más simple en SQL a cambio de dos columnas en vez de una — mismo criterio ya usado en el resto del proyecto (`mesMostrado`/`anioMostrado` en vacaciones/fichajes), no una decisión nueva de esta fase.

## Criterios de aceptación técnicos

- Tras `V23`, la tabla `nominas` existe vacía; insertar una fila sin `ruta_archivo` falla (`NOT NULL`).
- Insertar dos filas para el mismo `empleado_id`/`periodo_mes`/`periodo_anio` falla por `uk_nominas_empleado_periodo`.
- Insertar una fila con `periodo_mes = 13` falla por `chk_nominas_periodo_mes`.
- Insertar una fila con `estado = 'PENDIENTE'` falla por `chk_nominas_estado` (ese estado nunca se persiste, ver "Análisis y decisiones técnicas").
