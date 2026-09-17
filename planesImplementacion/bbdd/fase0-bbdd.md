# Plan de Implementación — Fase 0 (Base de datos)

Contrato de datos para Fase 0 (Empleados + Autenticación + Permisos). Este documento es la **fuente de verdad de nombres** (tablas, columnas, códigos) que deben respetar [`../back/fase0-back.md`](../back/fase0-back.md) y [`../front/fase0-front.md`](../front/fase0-front.md).

## Contexto y objetivo

Sentar el esquema base de MySQL (`gestion_restaurante`) para autenticar usuarios y gestionar el CRUD de empleados, con un **sistema de permisos extensible por tipo de usuario y vista** (en vez de un rol fijo `ADMIN`/`EMPLEADO` hardcodeado), y con migraciones versionadas desde el día uno (nada de `ddl-auto=update`).

## Stack

- MySQL (driver `mysql-connector-j` 9.7.0, ya en el proyecto).
- **Flyway** (`flyway-core` + `flyway-mysql`).
- Convención de nombres: `snake_case` en BBDD, mapeado automáticamente por la estrategia física por defecto de Hibernate a los campos `camelCase` de las entidades JPA.

## Modelo de permisos — decisión de diseño

En vez de una columna `rol` fija en `empleados`, se modela como catálogo relacional para poder crear nuevos tipos de usuario en el futuro sin tocar código ni esquema:

- **`tipos_usuarios`**: catálogo de roles (ADMIN, EMPLEADO, y los que se añadan después — p.ej. "Encargado" más adelante sería solo una fila nueva + sus permisos, sin migración de esquema).
- **`vistas`**: catálogo de los módulos/secciones de la aplicación controlables por permisos (Empleados, Fichajes, Facturas, Nóminas — una vista por módulo grande, no por cada pantalla suelta dentro de él).
- **`permisos`**: tabla puente `tipo_usuario_id` × `vista_id` → `nivel_acceso` (`EDICION` o `VISUALIZACION`). **Si no existe fila para una combinación, ese tipo de usuario no tiene ningún acceso a esa vista** — la ausencia de permiso es la representación de "sin acceso", no un tercer valor en el enum.
- `empleados.tipo_usuario_id` sustituye a la antigua columna `rol`.

Esto permite que, en el futuro, ampliar el sistema de permisos (Fase posterior, fuera de alcance de Fase 0) sea añadir/editar filas en `permisos` desde una pantalla de administración, sin cambios de esquema ni de código de autorización.

## Análisis y decisiones técnicas

- `spring.jpa.hibernate.ddl-auto=validate` — el esquema lo gobierna Flyway, Hibernate solo valida.
- Migraciones en `GestionRestauranteBack/src/main/resources/db/migration/`, nomenclatura `V{n}__descripcion.sql`.
- `nivel_acceso` como `VARCHAR(20)` con `CHECK` (no enum nativo de MySQL), igual que se decidió antes para `rol` — el enum real vive en el código.
- `tipos_usuarios.codigo` y `vistas.codigo` son los identificadores **estables** que usa el código (backend y frontend), independientes del `id` autonumérico — así una vista o tipo de usuario se referencia siempre por su código (`'ADMIN'`, `'FICHAJES'`...) y nunca por un id mágico.
- Contraseñas: `password_hash` (BCrypt, `VARCHAR(60)`), nunca en texto plano.
- Timestamps de auditoría (`created_at`, `updated_at`) en todas las tablas nuevas desde ya.
- Índices únicos en `empleados.email`, `empleados.dni_nie`, `tipos_usuarios.codigo`, `vistas.codigo`, y en el par (`tipo_usuario_id`, `vista_id`) de `permisos`.
- Se prepara la carpeta de migraciones para que las Fases 1-3 añadan sus propias tablas (`fichajes`, `proveedores`, `facturas`, `nominas`) y, si hiciera falta, nuevas filas en `vistas` — no se crean todavía en Fase 0.

## Desglose de subtareas

1. **[BBDD, baja]** Añadir `flyway-core` y `flyway-mysql` al `pom.xml` (coordina con `back/fase0-back.md`).
2. **[BBDD, baja]** Configurar `application.properties`: `spring.flyway.enabled=true`, `spring.flyway.baseline-on-migrate=true`, `spring.jpa.hibernate.ddl-auto=validate`.
3. **[BBDD, baja]** `V1__create_tipos_usuarios_table.sql`.
4. **[BBDD, baja]** `V2__create_vistas_table.sql`.
5. **[BBDD, media]** `V3__create_permisos_table.sql` (FKs a las dos anteriores).
6. **[BBDD, media]** `V4__create_empleados_table.sql` (FK a `tipos_usuarios`).
7. **[BBDD, media]** `V5__seed_catalogo_permisos.sql`: inserta los dos tipos de usuario iniciales, las 4 vistas, y la matriz de permisos acordada (ver más abajo).
8. **[BBDD, baja]** Verificar que `flyway_schema_history` marca `V1`-`V5` en `Success` al arrancar el backend.

## DDL

### `V1__create_tipos_usuarios_table.sql`

```sql
CREATE TABLE tipos_usuarios (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo       VARCHAR(30)   NOT NULL,
    nombre       VARCHAR(50)   NOT NULL,
    descripcion  VARCHAR(255)  NULL,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_tipos_usuarios_codigo UNIQUE (codigo)
);
```

### `V2__create_vistas_table.sql`

```sql
CREATE TABLE vistas (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo       VARCHAR(30)   NOT NULL,
    nombre       VARCHAR(50)   NOT NULL,
    descripcion  VARCHAR(255)  NULL,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_vistas_codigo UNIQUE (codigo)
);
```

### `V3__create_permisos_table.sql`

```sql
CREATE TABLE permisos (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo_usuario_id  BIGINT       NOT NULL,
    vista_id         BIGINT       NOT NULL,
    nivel_acceso     VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_permisos_tipo_usuario FOREIGN KEY (tipo_usuario_id) REFERENCES tipos_usuarios (id),
    CONSTRAINT fk_permisos_vista FOREIGN KEY (vista_id) REFERENCES vistas (id),
    CONSTRAINT uk_permisos_tipo_vista UNIQUE (tipo_usuario_id, vista_id),
    CONSTRAINT chk_permisos_nivel_acceso CHECK (nivel_acceso IN ('EDICION', 'VISUALIZACION'))
);
```

### `V4__create_empleados_table.sql`

```sql
CREATE TABLE empleados (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre           VARCHAR(100)  NOT NULL,
    apellidos        VARCHAR(150)  NOT NULL,
    email            VARCHAR(150)  NOT NULL,
    dni_nie          VARCHAR(15)   NOT NULL,
    telefono         VARCHAR(20)   NULL,
    puesto           VARCHAR(100)  NULL,
    fecha_alta       DATE          NOT NULL,
    fecha_baja       DATE          NULL,
    activo           TINYINT(1)    NOT NULL DEFAULT 1,
    tipo_usuario_id  BIGINT        NOT NULL,
    password_hash    VARCHAR(60)   NOT NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_empleados_email UNIQUE (email),
    CONSTRAINT uk_empleados_dni_nie UNIQUE (dni_nie),
    CONSTRAINT fk_empleados_tipo_usuario FOREIGN KEY (tipo_usuario_id) REFERENCES tipos_usuarios (id)
);

CREATE INDEX idx_empleados_activo ON empleados (activo);
CREATE INDEX idx_empleados_tipo_usuario ON empleados (tipo_usuario_id);
```

### `V5__seed_catalogo_permisos.sql`

```sql
INSERT INTO tipos_usuarios (codigo, nombre, descripcion) VALUES
    ('ADMIN', 'Administrador', 'Acceso completo a todos los módulos'),
    ('EMPLEADO', 'Empleado', 'Acceso limitado a su propio fichaje');

INSERT INTO vistas (codigo, nombre, descripcion) VALUES
    ('EMPLEADOS', 'Empleados', 'Alta, edición y baja de empleados'),
    ('FICHAJES', 'Fichajes', 'Registro de entrada/salida y consulta de horas'),
    ('FACTURAS', 'Facturas de proveedores', 'Gestión de facturas de proveedores'),
    ('NOMINAS', 'Nóminas', 'Gestión y envío de nóminas');

-- ADMIN: edición en las 4 vistas
INSERT INTO permisos (tipo_usuario_id, vista_id, nivel_acceso)
SELECT tu.id, v.id, 'EDICION'
FROM tipos_usuarios tu
JOIN vistas v ON v.codigo IN ('EMPLEADOS', 'FICHAJES', 'FACTURAS', 'NOMINAS')
WHERE tu.codigo = 'ADMIN';

-- EMPLEADO: edición únicamente en Fichajes (sobre sus propios registros; la restricción
-- "solo los suyos" se aplica a nivel de lógica de negocio en el backend, no en esta tabla).
INSERT INTO permisos (tipo_usuario_id, vista_id, nivel_acceso)
SELECT tu.id, v.id, 'EDICION'
FROM tipos_usuarios tu
JOIN vistas v ON v.codigo = 'FICHAJES'
WHERE tu.codigo = 'EMPLEADO';
```

**Mapeo de columnas ↔ campo de entidad JPA / DTO** (debe coincidir con `back/fase0-back.md`):

| Tabla | Columna | Campo entidad/DTO | Tipo Java | Tipo TS (front) |
|---|---|---|---|---|
| `tipos_usuarios` | `codigo` | `codigo` | `String` | `string` |
| `vistas` | `codigo` | `codigo` | `String` | `string` (p.ej. `'FICHAJES'`) |
| `permisos` | `nivel_acceso` | `nivelAcceso` | `NivelAcceso` (enum `EDICION`/`VISUALIZACION`) | `'EDICION' \| 'VISUALIZACION'` |
| `empleados` | `tipo_usuario_id` | `tipoUsuario` (relación `@ManyToOne`) | `TipoUsuario` | `{ codigo: string; nombre: string }` |
| `empleados` | resto de columnas | igual que antes (`nombre`, `apellidos`, `email`, `dniNie`, `telefono`, `puesto`, `fechaAlta`, `fechaBaja`, `activo`) | — | — |

## Seed del usuario Admin inicial

El primer empleado `ADMIN` **no** se crea por SQL con un hash hardcodeado (evita el problema de precomputar un BCrypt válido a mano): se crea mediante un `CommandLineRunner` en el backend (`back/fase0-back.md`) que, si la tabla `empleados` está vacía al arrancar, crea un Admin con contraseña hasheada en caliente por `PasswordEncoder`, usando `tipos_usuarios` con `codigo='ADMIN'` ya sembrado por `V5`.

## Riesgos y dependencias

- **Riesgo bajo**: si ya existe una tabla `empleados` de un intento anterior con la columna `rol`, hay que partir de una BBDD limpia (`DROP DATABASE` + recrear con `GestionRestauranteBack/scripts/create-database.sql`) antes de aplicar estas migraciones.
- **Dependencia**: el backend depende de que `V1`-`V5` estén aplicadas antes de poder hacer login (necesita al menos un `tipo_usuario` y sus `permisos` para construir la respuesta de login).
- **Extensibilidad ya cubierta por diseño**: añadir un tipo de usuario nuevo en el futuro (p.ej. "Encargado") no requiere migración de esquema, solo `INSERT` en `tipos_usuarios` y `permisos` — importante para cuando se construya la pantalla de administración de permisos en una fase posterior.

## Criterios de aceptación técnicos

- Las tablas `tipos_usuarios`, `vistas`, `permisos` y `empleados` existen con las columnas/constraints anteriores.
- Tras `V5`, `ADMIN` tiene `EDICION` en las 4 vistas y `EMPLEADO` tiene `EDICION` únicamente en `FICHAJES` (0 filas de permiso para `EMPLEADO` en las otras 3 vistas = sin acceso).
- Insertar un `permiso` duplicado para el mismo (`tipo_usuario_id`, `vista_id`) falla por la constraint única.
- Borrar un `tipo_usuario` referenciado por algún `empleado` o `permiso` falla por FK (no se permite dejar empleados huérfanos de tipo de usuario).
