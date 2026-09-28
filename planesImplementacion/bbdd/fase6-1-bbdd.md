# Plan de Implementación — Fase 6.1 (Base de datos): Recuperación de contraseña ("olvidé mi contraseña")

Depende únicamente de [`fase0-bbdd.md`](./fase0-bbdd.md) (tabla `empleados`). Numerado a partir de **V40**: la Fase 5 (`fase5-1-bbdd.md`) ya está implementada de verdad en el repo como `V39__create_configuracion_empresa_table.sql`, así que V40 es el siguiente número real disponible hoy. Esta misma entrega añade también `V41` (ver "Ampliación V41" más abajo), así que el siguiente número real libre pasa a ser **V42**. Los dos planes de cumplimiento normativo (`integracion_verifactu-bbdd.md`, `fichajes_vacaciones_2027-bbdd.md`) siguen sin implementarse y reservaban provisionalmente V39-V44 — ese rango queda desactualizado por la implementación real de las Fases 5 y 6; si se implementan más adelante, renumerar sus migraciones a partir de V42 (o del siguiente número real libre en ese momento). Fuente de verdad de nombres para [`../back/fase6-1-back.md`](../back/fase6-1-back.md).

## Contexto y objetivo

Una tabla nueva, `tokens_recuperacion_password`: un token de un solo uso, con expiración corta, que se genera cuando un empleado pide "olvidé mi contraseña" desde el login, y se consume una única vez para permitirle fijar una contraseña nueva sin haber iniciado sesión.

## Análisis y decisiones técnicas

- **Se guarda el hash del token (SHA-256), nunca el token en claro** — el valor en claro solo existe en memoria el tiempo justo para meterlo en el enlace del email; si la tabla se filtrara, ningún token seguiría siendo utilizable. Distinto del cifrado reversible de `conexion_google_business.refresh_token_cifrado` (`fase4-1-bbdd.md`): aquel necesita descifrarse para reutilizar el token contra la API de Google, este solo necesita compararse por igualdad, así que un hash de una vía (SHA-256) es suficiente y más simple — mismo razonamiento ya aplicado a la huella de VeriFactu (`integracion_verifactu-bbdd.md`), no al cifrado de Google.
- **`fecha_uso` nullable marca "consumido" en vez de una columna `estado`/`usado` booleana aparte**: `NULL` = todavía válido (si no ha expirado), con fecha = ya usado — un único campo hace ambas veces de marca de consumo y de auditoría de cuándo se usó, sin necesitar dos columnas que podrían desincronizarse entre sí.
- **Al pedir un nuevo token se borran los anteriores no usados del mismo empleado** (`back/fase6-1-back.md`), en vez de dejarlos caducar solos: si alguien pide dos veces "olvidé mi contraseña", el enlace del primer email deja de funcionar en cuanto se pide el segundo — evita que un enlace antiguo, todavía dentro de su ventana de expiración, siga siendo válido después de que el propio usuario haya pedido uno más reciente.
- **Sin columna `intentos`/límite de frecuencia**: no solicitado y fuera de alcance de esta fase (ver "No-objetivos" en `back/fase6-1-back.md`) — si se detecta abuso real, es una fase de seguridad aparte.
- **`token_hash` con índice único**: además de servir de clave de búsqueda al restablecer, garantiza a nivel de BBDD que nunca puedan coexistir dos tokens con el mismo hash (colisión de `SecureRandom` con las probabilidades adecuadas es astronómicamente improbable, pero la constraint no cuesta nada tenerla).
- **Sin purga automática de tokens caducados/usados**: se acumulan en la tabla sin límite — a este volumen (un restaurante, empleados contados) es un riesgo bajo aceptado, mismo criterio que otras tablas de bajo volumen del proyecto; si molestara, sería una tarea de mantenimiento aparte.

## Desglose de subtareas

1. **[BBDD, media]** `V40__create_tokens_recuperacion_password_table.sql`.

## DDL

### `V40__create_tokens_recuperacion_password_table.sql`

```sql
CREATE TABLE tokens_recuperacion_password (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    empleado_id       BIGINT       NOT NULL,
    token_hash        VARCHAR(64)  NOT NULL,
    fecha_creacion    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_expiracion  TIMESTAMP    NOT NULL,
    fecha_uso         TIMESTAMP    NULL,

    CONSTRAINT fk_tokens_recuperacion_password_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id),
    CONSTRAINT uk_tokens_recuperacion_password_hash UNIQUE (token_hash)
);

CREATE INDEX idx_tokens_recuperacion_password_empleado ON tokens_recuperacion_password (empleado_id);
```

**Mapeo de columnas ↔ campo de entidad JPA** (debe coincidir con `back/fase6-1-back.md`):

| Columna | Campo entidad | Tipo Java |
|---|---|---|
| `empleado_id` | `empleado` (`@ManyToOne`) | `Empleado` |
| `token_hash` | `tokenHash` | `String` |
| `fecha_creacion` | `fechaCreacion` | `LocalDateTime` |
| `fecha_expiracion` | `fechaExpiracion` | `LocalDateTime` |
| `fecha_uso` | `fechaUso` (nullable) | `LocalDateTime` |

## Riesgos y dependencias

- **Dependencia dura** con `fase0-bbdd.md`: sin `empleados`, `empleado_id` no tiene a qué apuntar.
- **Riesgo bajo**: sin purga automática, la tabla crece sin límite (ver "Análisis y decisiones técnicas") — aceptable al volumen actual del proyecto.

## Criterios de aceptación técnicos

- Tras `V40`, la tabla existe vacía.
- Insertar dos filas con el mismo `token_hash` falla por `uk_tokens_recuperacion_password_hash`.
- Insertar con un `empleado_id` inexistente falla por la FK.

## Ampliación V41 — remitente de email configurable (extiende Fase 5.1)

Añadida en la misma entrega que la tabla de tokens, a petición del usuario ("el correo debe salir desde el correo de la empresa, el mismo que envía la recuperación de contraseña"). Extiende `configuracion_empresa` (tabla de [`fase5-1-bbdd.md`](./fase5-1-bbdd.md)), no `tokens_recuperacion_password` — se documenta aquí, y no en `fase5-1-bbdd.md`, porque se implementó en esta entrega posterior; el desglose de `fase5-1-back.md` remite aquí para el DDL.

### `V41__add_email_remitente_a_configuracion_empresa.sql`

```sql
ALTER TABLE configuracion_empresa
    ADD COLUMN email_remitente VARCHAR(150) NULL;
```

Nullable a propósito: mientras no se configure, `EmailServiceImpl` usa `spring.mail.username` (la cuenta SMTP autenticada) como remitente por defecto — mismo criterio de "columna opcional con fallback a un valor por defecto ya existente" que otras columnas nullable del proyecto (p. ej. `logo_ruta` en la misma tabla).

**Mapeo de columnas ↔ campo de entidad JPA** (debe coincidir con `back/fase5-1-back.md`):

| Columna | Campo entidad | Tipo Java |
|---|---|---|
| `email_remitente` | `emailRemitente` (nullable) | `String` |

### Criterios de aceptación técnicos (V41)

- Tras `V41`, `configuracion_empresa` tiene la columna `email_remitente`, `NULL` para la fila sembrada en `V45`/`V39` (según el entorno).
- Un valor de más de 150 caracteres falla por el límite de columna (mitigado en el DTO con `@Size(max=150)`, ver `back/fase5-1-back.md`).

## Corrección de rango de numeración (nota para `plan-implementacion-general.md`)

Al implementarse `V41` en esta entrega, el "siguiente número real libre" para los planes de cumplimiento normativo (`integracion_verifactu-bbdd.md`, `fichajes_vacaciones_2027-bbdd.md`) pasa a ser **V42**, no V41 como indicaba la nota de `plan-implementacion-general.md` antes de esta ampliación — actualizado ahí también.
