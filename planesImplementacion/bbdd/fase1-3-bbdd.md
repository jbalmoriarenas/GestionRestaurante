# Plan de Implementación — Fase 1.3 (Base de datos): Fichajes (vista Admin)

Depende de [`fase0-bbdd.md`](./fase0-bbdd.md) (tabla `empleados`) y de [`fase1-1-bbdd.md`](./fase1-1-bbdd.md) (tabla `fichajes`, reutilizada sin cambios por esta vista). Fuente de verdad de nombres para [`../back/fase1-3-back.md`](../back/fase1-3-back.md).

## Contexto y objetivo

Añadir el soporte de **jornada diaria por empleado** (confirmado con el usuario: no todos los empleados tienen por qué trabajar 8h/día — jornada parcial 4h/6h, etc.), necesario para que la vista Admin de Fichajes resalte correctamente los días/semanas/meses por debajo del mínimo esperado de **cada** empleado, no de un valor único global.

## Análisis y decisiones técnicas

- Columna **nullable**: `NULL` significa "usa el valor global por defecto" (`app.fichajes.horas-jornada-default=8`, ver `back/fase1-3-back.md`), no `0`. Así, dar de alta un empleado sin rellenar este campo (caso más común, jornada completa estándar) no requiere que el formulario de alta obligue a escribir "8" a mano.
- Se añade sobre la tabla `empleados` ya creada en `fase0-bbdd.md` (Fase 0, ya cerrada) como una **migración incremental aditiva**: columna nullable, sin `DEFAULT` obligatorio, no rompe ninguna fila existente ni requiere backfill.
- Tipo `DECIMAL(4,2)` (no entero) para admitir jornadas como 6.5h sin necesitar otra migración después.

## Desglose de subtareas

1. **[BBDD, baja]** `V12__add_horas_jornada_a_empleados.sql`.

## DDL

### `V12__add_horas_jornada_a_empleados.sql`

```sql
ALTER TABLE empleados
    ADD COLUMN horas_jornada_diaria DECIMAL(4,2) NULL;
```

**Mapeo de columnas ↔ campo de entidad JPA / DTO** (debe coincidir con `back/fase1-3-back.md`):

| Columna | Campo entidad | Tipo Java | Tipo TS (front) |
|---|---|---|---|
| `horas_jornada_diaria` | `horasJornadaDiaria` | `BigDecimal` (nullable) | `number \| null` |

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase0-bbdd.md`: `ALTER TABLE` sobre `empleados`, ya en producción tras el cierre de Fase 0 — es una migración aditiva y reversible (no hay `DROP`/`NOT NULL` que pueda romper datos existentes).
- **Coordinación con Fase 0 ya cerrada**: esta columna se expone también en `EmpleadoCreateDTO`/`EmpleadoUpdateDTO`/`EmpleadoResponseDTO` (ya definidos en `fase0-back.md`) y en el formulario `EmpleadoForm` del front (ya definido en `fase0-front.md`) — ver el detalle de esa extensión puntual en `back/fase1-3-back.md` y `front/fase1-3-front.md`, para no tener que reabrir los documentos de Fase 0 ya cerrados.

## Criterios de aceptación técnicos

- Tras aplicar `V12`, todos los empleados existentes tienen `horas_jornada_diaria = NULL` sin ningún error de migración.
- Insertar/actualizar un empleado con `horas_jornada_diaria = 6.50` se persiste correctamente.
