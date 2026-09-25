# Plan de Implementación — Fase 4.1 (Base de datos): Valoraciones — conexión con Google y modelo de reseñas

Depende únicamente de [`fase0-bbdd.md`](./fase0-bbdd.md) (tabla `empleados` y el catálogo de permisos). Independiente de las Fases 1, 2 y 3 — no comparte ninguna tabla con ellas. Fuente de verdad de nombres para [`../back/fase4-1-back.md`](../back/fase4-1-back.md).

## Contexto y objetivo

Dos tablas nuevas: `conexion_google_business` (una única fila, guarda la conexión OAuth con la cuenta de Google Business Profile del restaurante) y `valoraciones` (una fila por reseña de Google sincronizada). La vista de consulta con filtros/resumen (`fase4-2`) reutiliza ambas sin necesitar columnas nuevas.

## Análisis y decisiones técnicas

- **Necesita una vista nueva en el catálogo de permisos (`VALORACIONES`)**, exclusiva de `ADMIN` — mismo patrón que `FACTURAS`/`NOMINAS` (`fase0-bbdd.md`): conectar la cuenta de Google, sincronizar y consultar reseñas son capacidades de gestión del negocio, no algo a lo que un `EMPLEADO` necesite acceder. Si en el futuro se quiere que el equipo vea las reseñas (p. ej. como panel motivacional), es una migración de permisos aparte (`VISUALIZACION` para `EMPLEADO`), no construida aquí sin que se pida explícitamente.
- **`conexion_google_business` es una fila única (`id = 1`), igual que `configuracion_ubicacion`** (`fase1-1-bbdd.md`): la app se conecta a una sola cuenta/ubicación de Google Business Profile (la del restaurante), no hace falta modelar múltiples conexiones. A diferencia de `configuracion_ubicacion` (que se siembra con una fila por defecto para forzar su configuración), esta tabla **no lleva `INSERT` inicial**: sin fila significa "Desconectado" — mismo criterio de "ausencia de fila = estado por defecto calculado" ya usado en `nominas` (`fase3-1-bbdd.md`, estado `PENDIENTE` sin fila) en vez de inventar un valor `'DESCONECTADO'` en una columna de estado. La fila se inserta (`id = 1`) solo cuando el Admin completa el flujo OAuth (`back/fase4-1-back.md`) y se borra al desconectar.
- **`refresh_token_cifrado`, no en texto plano**: a diferencia de cualquier otro dato guardado hasta ahora en el proyecto, un refresh token de Google concede acceso de pie (hasta que se revoque) a los datos de negocio de la cuenta de Google conectada — más sensible que una contraseña de empleado (que ya se guarda con `BCrypt`, un hash irreversible) porque este token **sí** necesita poder recuperarse en claro para llamar a la API de Google en cada sincronización. Se cifra con AES (clave simétrica fuera de BBDD, ver `back/fase4-1-back.md`) en vez de guardarse tal cual — primera vez que el proyecto cifra un dato en reposo.
- **`location_resource_name` es el identificador de Google** (p. ej. `accounts/123456789/locations/987654321`), no un ID propio — se necesita tal cual, en ese formato, para llamar al endpoint de reseñas de la API de Google en cada sincronización; guardarlo evita repetir el descubrimiento de cuentas/ubicaciones en cada llamada.
- **`nombre_ubicacion` es solo informativo** (el nombre del local tal como lo devuelve Google, p. ej. "Sidrería La Zapica"): se muestra en el panel de conexión para que el Admin confirme visualmente que la cuenta conectada es la correcta, sin ningún uso funcional más allá de eso.
- **`ultimo_error` guarda el mensaje del último fallo de sincronización** (nullable, se limpia en la siguiente sincronización correcta): permite mostrar en el panel de conexión por qué la sincronización automática lleva tiempo sin actualizarse (p. ej. token revocado por el negocio desde la propia cuenta de Google), sin tener que revisar logs del servidor.
- **`valoraciones.google_review_id` con índice único**: identificador de la reseña que asigna Google; es la clave de *upsert* en cada sincronización (`back/fase4-1-back.md`) — evita duplicar una reseña ya sincronizada en ejecuciones sucesivas del job periódico.
- **`fecha_publicacion` vs. `fecha_sincronizacion`**: la primera es la fecha real en que se publicó la reseña en Google (la que se muestra y con la que se ordena/filtra en `fase4-2`); la segunda es metadato interno de cuándo la trajo nuestro sistema — no confundirlas, mismo cuidado que `estado_pago` vs. `estado_extraccion` en `facturas` (`fase2-1-bbdd.md`).
- **`fecha_actualizacion` nullable**: Google permite que el autor edite su reseña después de publicarla; si la API la marca como editada, esta columna registra cuándo, distinto de `fecha_publicacion` (que no cambia). Si Google nunca la marca como editada, queda `NULL`.
- **Sin tabla de "respuestas a reseñas"**: esta fase solo consulta reseñas, no responde a ellas desde la app (no solicitado por el usuario) — si se pide en el futuro, es una fase aparte con su propio modelo (una respuesta por reseña, con quién la escribió y cuándo).
- **`puntuacion` es `INT`, no `TINYINT`** (aunque el rango real sea 1-5 y `TINYINT` bastaría): Hibernate, con `ddl-auto=validate`, espera `SQLTypes.INTEGER` para un campo de entidad `Integer` salvo que se le indique explícitamente lo contrario (`columnDefinition`) — usar `TINYINT` aquí haría fallar la validación de esquema al arrancar. El rango 1-5 ya lo garantiza `chk_valoraciones_puntuacion`, así que el ahorro de espacio de `TINYINT` no compensa la fricción.

## Desglose de subtareas

1. **[BBDD, media]** `V36__create_conexion_google_business_table.sql`.
2. **[BBDD, media]** `V37__create_valoraciones_table.sql`.
3. **[BBDD, baja]** `V38__seed_vista_valoraciones.sql`.

## DDL

### `V36__create_conexion_google_business_table.sql`

```sql
CREATE TABLE conexion_google_business (
    id                            BIGINT        PRIMARY KEY,
    location_resource_name        VARCHAR(255)  NOT NULL,
    nombre_ubicacion               VARCHAR(255)  NULL,
    refresh_token_cifrado          VARCHAR(1000) NOT NULL,
    fecha_conexion                 TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    conectado_por_admin_id         BIGINT        NOT NULL,
    fecha_ultima_sincronizacion    TIMESTAMP     NULL,
    ultimo_error                   VARCHAR(500)  NULL,

    CONSTRAINT fk_conexion_google_business_admin FOREIGN KEY (conectado_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT chk_conexion_google_business_id_unico CHECK (id = 1)
);

-- Sin INSERT inicial a proposito: la ausencia de fila representa "Desconectado" (mismo patron
-- de "sin fila = PENDIENTE" que nominas, fase3-1-bbdd.md). La fila (id = 1) la inserta el
-- backend solo al completar el flujo OAuth (back/fase4-1-back.md).
```

### `V37__create_valoraciones_table.sql`

```sql
CREATE TABLE valoraciones (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    google_review_id       VARCHAR(150)  NOT NULL,
    autor_nombre            VARCHAR(150)  NOT NULL,
    autor_foto_url           VARCHAR(500)  NULL,
    puntuacion              INT           NOT NULL,
    comentario               TEXT          NULL,
    fecha_publicacion        TIMESTAMP     NOT NULL,
    fecha_actualizacion      TIMESTAMP     NULL,
    fecha_sincronizacion     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_valoraciones_google_review_id UNIQUE (google_review_id),
    CONSTRAINT chk_valoraciones_puntuacion CHECK (puntuacion BETWEEN 1 AND 5)
);

CREATE INDEX idx_valoraciones_puntuacion ON valoraciones (puntuacion);
CREATE INDEX idx_valoraciones_fecha_publicacion ON valoraciones (fecha_publicacion);
```

### `V38__seed_vista_valoraciones.sql`

```sql
INSERT INTO vistas (codigo, nombre, descripcion) VALUES
    ('VALORACIONES', 'Valoraciones', 'Consulta de reseñas de Google Business Profile del restaurante');

INSERT INTO permisos (tipo_usuario_id, vista_id, nivel_acceso)
SELECT tu.id, v.id, 'EDICION'
FROM tipos_usuarios tu
JOIN vistas v ON v.codigo = 'VALORACIONES'
WHERE tu.codigo = 'ADMIN';
```

**Mapeo de columnas ↔ campo de entidad JPA / DTO** (debe coincidir con `back/fase4-1-back.md`):

| Columna | Campo entidad | Tipo Java | Tipo TS (front) |
|---|---|---|---|
| `conexion_google_business.location_resource_name` | `locationResourceName` | `String` | — (no se expone al front) |
| `conexion_google_business.nombre_ubicacion` | `nombreUbicacion` | `String` (nullable) | `string \| null` |
| `conexion_google_business.refresh_token_cifrado` | `refreshTokenCifrado` | `String` | — (nunca se expone) |
| `conexion_google_business.fecha_conexion` | `fechaConexion` | `LocalDateTime` | `string` |
| `conexion_google_business.fecha_ultima_sincronizacion` | `fechaUltimaSincronizacion` | `LocalDateTime` (nullable) | `string \| null` |
| `conexion_google_business.ultimo_error` | `ultimoError` | `String` (nullable) | `string \| null` |
| `valoraciones.google_review_id` | `googleReviewId` | `String` | — (no se expone; es un detalle de sincronización) |
| `valoraciones.autor_nombre` / `autor_foto_url` | `autorNombre` / `autorFotoUrl` | `String` / `String` (nullable) | `string` / `string \| null` |
| `valoraciones.puntuacion` | `puntuacion` | `Integer` (1-5) | `number` |
| `valoraciones.comentario` | `comentario` | `String` (nullable) | `string \| null` |
| `valoraciones.fecha_publicacion` / `fecha_actualizacion` | `fechaPublicacion` / `fechaActualizacion` | `LocalDateTime` / `LocalDateTime` (nullable) | `string` / `string \| null` |

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase0-bbdd.md`: sin `empleados` ni el catálogo de permisos, esta migración no tiene sentido.
- **Riesgo medio**: `refresh_token_cifrado` es el único dato cifrado en reposo de todo el proyecto — si se pierde o rota la clave de cifrado (`back/fase4-1-back.md`), el token queda irrecuperable y hay que reconectar manualmente con Google; no hay que confundir esto con una copia de seguridad de la clave (fuera de alcance de este documento, a valorar en despliegue).
- **Riesgo bajo**: sin `INSERT` inicial en `conexion_google_business`, cualquier consulta debe tratar "tabla vacía" como un caso normal (no un error) — mismo cuidado ya aplicado en `nominas` para el estado `PENDIENTE` calculado.

## Criterios de aceptación técnicos

- Tras `V36`/`V37`, ambas tablas existen vacías.
- Insertar una fila en `conexion_google_business` con `id != 1` falla por `chk_conexion_google_business_id_unico`.
- Insertar una segunda fila en `conexion_google_business` con `id = 1` falla por violación de clave primaria (garantiza una única conexión activa).
- Insertar una `valoracion` con `puntuacion = 0` o `puntuacion = 6` falla por `chk_valoraciones_puntuacion`.
- Insertar dos `valoraciones` con el mismo `google_review_id` falla por `uk_valoraciones_google_review_id`.
- Tras `V38`, `ADMIN` tiene `EDICION` en `VALORACIONES` y `EMPLEADO` no tiene ninguna fila de permiso para esa vista.
