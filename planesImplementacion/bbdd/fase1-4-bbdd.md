# Plan de Implementación — Fase 1.4 (Base de datos): Configuración de vacaciones

Depende de [`fase0-bbdd.md`](./fase0-bbdd.md) (tabla `empleados`) y de [`fase1-2-bbdd.md`](./fase1-2-bbdd.md) (columna `dias_vacaciones_anuales`, cuya decisión de diseño original — "columna en `empleados`, sin tabla de configuración global" — se revisa aquí, ver más abajo). Mismo patrón de fila única que [`fase1-3-bbdd.md`](./fase1-3-bbdd.md) usó para `configuracion_ubicacion`. Fuente de verdad de nombres para [`../back/fase1-4-back.md`](../back/fase1-4-back.md).

## Contexto y objetivo

Añadido a petición del usuario tras el cierre de Fase 1.2: hoy los "23 días anuales" son un valor fijo (constante Java `Empleado.DIAS_VACACIONES_ANUALES_DEFECTO`, ver `back/fase1-2-back.md`) que se copia a `empleados.dias_vacaciones_anuales` en el momento del alta y nunca se puede editar después, ni globalmente ni por empleado. El usuario quiere que el Admin pueda, desde una vista nueva: (a) cambiar el valor por defecto **para todos los empleados que no tengan uno propio**, sin necesidad de re-desplegar el backend; (b) **sobrescribir el valor de un empleado concreto**, sin poder superar el valor global configurado.

## Análisis y decisiones técnicas

- **Revisión de la decisión de `fase1-2-bbdd.md`** ("columna en `empleados`, no una tabla de configuración global, aunque hoy todos arranquen en 23"): esa decisión seguía siendo válida para permitir un valor distinto por empleado, pero no contemplaba un valor **global editable en caliente** — no existía ningún sitio done persistir "el valor por defecto actual" fuera de una constante Java. Se añade `configuracion_vacaciones`, con el mismo patrón de fila única (`id=1`, forzado por `CHECK`) que `configuracion_ubicacion` (`fase1-3-bbdd.md`) — reutilizando un patrón ya validado en vez de inventar uno nuevo.
- **`empleados.dias_vacaciones_anuales` pasa a ser NULLABLE** (antes `NOT NULL DEFAULT 23`): mismo patrón exacto que `empleados.horas_jornada_diaria` (`fase1-3-bbdd.md`) — `NULL` significa "usa el valor global configurado", no "0 días". Esto es lo que permite que cambiar el valor global afecte **dinámicamente** a todos los empleados que no tengan un valor propio, que es justo lo que pidió el usuario ("editar los días de vacaciones anuales en general para todos los usuarios"); si la columna siguiera fija en el momento del alta, cambiar el global solo afectaría a los empleados dados de alta a partir de ese momento, no a los ya existentes.
- **Migración de los datos existentes**: hoy **todos** los empleados tienen `dias_vacaciones_anuales = 23` únicamente porque era el `DEFAULT` de `V14` (`fase1-2-bbdd.md`) — no existe ningún mecanismo hasta ahora para haberlo personalizado. Se pueden poner a `NULL` sin pérdida de información real: pasan a seguir el valor global dinámicamente, que hoy también es 23, así que el comportamiento observable no cambia el día del despliegue.
- **Rango del valor global**: se valida en el backend (`back/fase1-4-back.md`), no en la BBDD con un `CHECK` — mismo criterio que `configuracion_ubicacion`, cuyos rangos (`lat`/`lng`/`radioMetros`) tampoco se fuerzan por `CHECK` sino por `@Valid` en el DTO de entrada.
- **El techo "el valor de un empleado no puede superar el global" es una regla de negocio, no de la BBDD**: no hay forma sencilla de expresar en SQL "esta columna no puede superar el valor de otra fila de otra tabla" de forma portable y mantenible; se valida en `back/fase1-4-back.md` en el momento de guardar.

## Desglose de subtareas

1. **[BBDD, baja]** `V20__create_configuracion_vacaciones_table.sql`.
2. **[BBDD, baja]** `V21__hacer_nullable_dias_vacaciones_anuales.sql`.
3. **[BBDD, baja]** `V22__corregir_tipo_dias_anuales_a_int.sql` — **corrección detectada durante la implementación**: V20/V21 declararon `SMALLINT` por costumbre (mismo tipo que tenía originalmente `dias_vacaciones_anuales`), pero las entidades JPA correspondientes (`ConfiguracionVacaciones.diasAnualesDefecto`, `Empleado.diasVacacionesAnuales`) se tipan como `Integer` — Hibernate mapea `Integer` a `INT`, no a `SMALLINT`, y con `ddl-auto=validate` el arranque falla ("wrong column type encountered"). Se corrige con una migración nueva en vez de editar V20/V21 (ya aplicadas — nunca se edita una migración existente, ver convención ya establecida en el resto de este documento).

## DDL

### `V20__create_configuracion_vacaciones_table.sql`

```sql
CREATE TABLE configuracion_vacaciones (
    id                        BIGINT PRIMARY KEY,
    dias_anuales_defecto      SMALLINT      NOT NULL DEFAULT 23,
    updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    actualizado_por_admin_id  BIGINT        NULL,

    CONSTRAINT fk_configuracion_vacaciones_admin FOREIGN KEY (actualizado_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT chk_configuracion_vacaciones_id_unico CHECK (id = 1)
);

-- Fila única, sembrada en 23 (el mismo valor que ya era la constante Java por defecto):
-- el despliegue de esta migración no cambia el comportamiento observable del sistema.
INSERT INTO configuracion_vacaciones (id, dias_anuales_defecto) VALUES (1, 23);
```

### `V21__hacer_nullable_dias_vacaciones_anuales.sql`

```sql
ALTER TABLE empleados
    MODIFY COLUMN dias_vacaciones_anuales SMALLINT NULL;

-- Todas las filas existentes tienen 23 unicamente porque era el DEFAULT de V14
-- (fase1-2-bbdd.md); nadie lo ha personalizado todavia, no existia como hacerlo. Se limpian
-- a NULL para que, a partir de ahora, sigan dinamicamente el valor global de
-- configuracion_vacaciones en vez de quedar clavadas en el 23 que tenian por accidente de
-- la migracion original.
UPDATE empleados SET dias_vacaciones_anuales = NULL;
```

### `V22__corregir_tipo_dias_anuales_a_int.sql`

```sql
ALTER TABLE configuracion_vacaciones
    MODIFY COLUMN dias_anuales_defecto INT NOT NULL DEFAULT 23;

ALTER TABLE empleados
    MODIFY COLUMN dias_vacaciones_anuales INT NULL;
```

**Mapeo de columnas ↔ campo de entidad JPA / DTO** (debe coincidir con `back/fase1-4-back.md`; tipos ya con la corrección de `V22` aplicada):

| Tabla | Columna | Campo entidad/DTO | Tipo Java | Tipo TS (front) |
|---|---|---|---|---|
| `configuracion_vacaciones` | `dias_anuales_defecto` | `diasAnualesDefecto` | `Integer` (columna `INT`, no `SMALLINT` — ver `V22`) | `number` |
| `configuracion_vacaciones` | `updated_at` | `updatedAt` | `LocalDateTime` | `string` |
| `configuracion_vacaciones` | `actualizado_por_admin_id` | `actualizadoPorAdminId` | `Long` (nullable) | — |
| `empleados` | `dias_vacaciones_anuales` | `diasVacacionesAnuales` | `Integer` (nullable, columna `INT` desde `V22`; antes `short` no nulo sobre `SMALLINT`) | `number \| null` |

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase1-2-bbdd.md`: modifica (relaja) la columna que esa fase creó como `NOT NULL DEFAULT 23`.
- **Dependencia** con `bbdd/fase1-3-bbdd.md`: `configuracion_vacaciones` reutiliza el mismo patrón de fila única que `configuracion_ubicacion` — si ese patrón cambia (p. ej. se decide permitir varias sedes en el futuro), revisar ambas tablas a la vez.
- **Riesgo bajo**: al poner todos los `dias_vacaciones_anuales` existentes a `NULL`, se pierde la posibilidad de distinguir "un empleado que tenía 23 porque el Admin se lo asignó explícitamente" de "un empleado que tenía 23 porque era el valor por defecto" — como hoy no existe ningún mecanismo para lo primero, no hay ningún caso real que se pierda, pero se documenta como asunción explícita del `UPDATE` incondicional.
- **Riesgo bajo**: si el valor global configurado baja por debajo del `dias_vacaciones_anuales` explícito de algún empleado (que ya estaba permitido tener más que el nuevo global, por ejemplo por convenio), esa fila NO se toca automáticamente — el Admin sigue viendo ese empleado con más días que el nuevo estándar hasta que lo edite a mano. Comportamiento asumido como correcto (bajar el global no debe recortar acuerdos individuales ya concedidos sin que el Admin lo decida explícitamente).

## Criterios de aceptación técnicos

- Tras `V20`, existe una única fila en `configuracion_vacaciones` con `id=1` y `dias_anuales_defecto=23`; insertar una segunda fila falla por `chk_configuracion_vacaciones_id_unico`.
- Tras `V21`, `empleados.dias_vacaciones_anuales` acepta `NULL`, y todos los empleados existentes en ese momento lo tienen a `NULL`.
- Insertar/actualizar un empleado con `dias_vacaciones_anuales = NULL` o con un entero positivo se persiste correctamente en ambos casos.
