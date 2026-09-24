# Plan de Implementación — Fase 4.2 (Backend): Valoraciones — listado y resumen de reseñas

Depende de [`fase4-1-back.md`](./fase4-1-back.md) (tabla `valoraciones` ya poblada por la sincronización). Sin migraciones nuevas, reutiliza el esquema de `bbdd/fase4-1-bbdd.md`.

## Contexto y objetivo

Dos endpoints de solo lectura sobre las reseñas ya sincronizadas: un listado paginado filtrable por puntuación, y un resumen agregado (media, total, distribución por número de estrellas) para la cabecera del panel del front. Ninguno de los dos llama a la API de Google — leen exclusivamente de `valoraciones` (BBDD), coherente con la interpretación de "tiempo real" ya fijada en `fase4-1-back.md`.

## Desglose de subtareas

1. **[Backend, baja]** `ValoracionRepository` (`fase4-1-back.md`): añadir `findByPuntuacion(Integer, Pageable): Page<Valoracion>` y `findAll(Pageable): Page<Valoracion>` (ya heredado de `JpaRepository`, solo se usa explícitamente aquí) — ordenados por `fechaPublicacion DESC` vía el `Sort` del propio `Pageable` que construye el controller, no hardcodeado en la query.
2. **[Backend, baja]** DTO `ValoracionDTO {id, autorNombre, autorFotoUrl, puntuacion, comentario, fechaPublicacion, fechaActualizacion}` — sin `googleReviewId` ni `fechaSincronizacion` (detalles internos de sincronización, sin valor para el Admin).
3. **[Backend, media]** `ValoracionService.listar(Integer puntuacionOpcional, Pageable): Page<ValoracionDTO>`.
4. **[Backend, baja]** `GET /api/valoraciones?puntuacion=&page=&size=&sort=fechaPublicacion,desc` → `Page<ValoracionDTO>`, `@PreAuthorize("hasAuthority('VALORACIONES:EDICION')")`. `size` por defecto `10` (`@PageableDefault(size = 10, sort = "fechaPublicacion", direction = DESC)`), mismo tamaño ya usado en `facturas-admin` (`fase2-1-front.md`).
5. **[Backend, media]** `ValoracionService.obtenerResumen(): ResumenValoracionesDTO` — una única consulta agregada (`@Query` con `AVG(puntuacion)`, `COUNT(*)` y `COUNT(*) GROUP BY puntuacion`) en vez de traer todas las filas a memoria para calcularlo en Java; si no hay ninguna reseña sincronizada todavía, devuelve `media = null`, `total = 0`, `distribucion` con las 5 puntuaciones a `0` (nunca un error).
6. **[Backend, baja]** DTO `ResumenValoracionesDTO {media: BigDecimal (nullable), total: int, distribucion: Map<Integer, Integer>}` (claves `1`..`5`, siempre las 5 presentes aunque el conteo sea `0`).
7. **[Backend, baja]** `GET /api/valoraciones/resumen` → `ResumenValoracionesDTO`, `@PreAuthorize("hasAuthority('VALORACIONES:EDICION')")`.
8. **[Backend, media]** Tests `@WebMvcTest`: token `EMPLEADO` recibe 403 en ambos endpoints; `GET /api/valoraciones?puntuacion=5` devuelve solo reseñas de 5 estrellas; `GET /api/valoraciones/resumen` sin ninguna reseña en BBDD devuelve `total = 0` y `media = null` sin lanzar excepción.

## Decisiones técnicas

- **Resumen calculado con una `@Query` agregada, no en memoria**: evita traer todas las reseñas a Java solo para promediar/contar — mismo criterio de eficiencia ya aplicado al paginar `facturas`/`nominas` a nivel de BBDD en vez de en memoria cuando el volumen no está acotado (aquí, con el tiempo, el número de reseñas de un restaurante activo tampoco lo está).
- **`size` por defecto 10, igual que Facturas**: consistencia de la paginación por defecto entre módulos que muestran listados largos, sin que el usuario tenga que aprender un tamaño distinto por vista.
- **Solo lectura en esta fase**: no hay endpoint de edición/borrado de una `Valoracion` — el contenido pertenece a Google (`fase4-1-back.md` ya sincroniza sin persistir nada que el Admin edite), cualquier corrección real se hace desde la propia cuenta de Google, no desde esta app.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase4-1-back.md`: sin la tabla `valoraciones` poblada por la sincronización, estos endpoints solo devuelven listas/resúmenes vacíos (comportamiento correcto, no un error, pero sin utilidad real).
- **Riesgo bajo**: el resumen no distingue periodo (siempre es sobre el histórico completo de reseñas sincronizadas) — si en el futuro se quiere un resumen "de los últimos 30 días" o comparativas por mes, es una extensión de `obtenerResumen` con un filtro de fecha, no construida aquí sin caso de uso confirmado.

## Criterios de aceptación técnicos

- `GET /api/valoraciones` sin filtro devuelve todas las reseñas sincronizadas, paginadas de 10 en 10, ordenadas de más reciente a más antigua.
- `GET /api/valoraciones?puntuacion=3` devuelve únicamente reseñas con `puntuacion = 3`.
- `GET /api/valoraciones/resumen` con reseñas de puntuaciones 5, 5, 3 devuelve `media = 4.33` (redondeado), `total = 3`, `distribucion = {1: 0, 2: 0, 3: 1, 4: 0, 5: 2}`.
- `GET /api/valoraciones/resumen` sin ninguna reseña sincronizada devuelve `total = 0` y `media = null`, sin error 500.
- Un token `EMPLEADO` recibe 403 en ambos endpoints.
