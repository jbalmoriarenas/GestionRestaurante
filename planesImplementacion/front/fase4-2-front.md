# Plan de Implementación — Fase 4.2 (Frontend): Valoraciones — listado y resumen de reseñas

Consume el contrato de [`../back/fase4-2-back.md`](../back/fase4-2-back.md). Extiende `ValoracionesAdminComponent` de [`fase4-1-front.md`](./fase4-1-front.md) — sin componentes nuevos.

## Contexto y objetivo

Con la conexión activa (`fase4-1-front.md`), el panel de `/valoraciones` gana una cabecera de resumen (media de puntuación, total de reseñas, distribución por estrellas) y una tabla paginada con las reseñas ya sincronizadas, filtrable por puntuación.

## Modelos

### `core/models/valoracion.model.ts` (ampliación de `fase4-1-front.md`)
```typescript
export interface Valoracion {
  id: number;
  autorNombre: string;
  autorFotoUrl: string | null;
  puntuacion: number; // 1-5
  comentario: string | null;
  fechaPublicacion: string;
  fechaActualizacion: string | null;
}

export interface ResumenValoraciones {
  media: number | null;
  total: number;
  distribucion: Record<number, number>; // claves 1..5
}
```

## Desglose de subtareas

1. **[Frontend, baja]** `ValoracionService` (`fase4-1-front.md`): `listar(puntuacion: number | null, page: number, size: number): Observable<Page<Valoracion>>`; `obtenerResumen(): Observable<ResumenValoraciones>`.
2. **[Frontend, media]** `ValoracionesAdminComponent` (extensión, solo visible cuando `conectado = true`):
   - Cabecera de 3 `ui-card` (mismo patrón que `mis-vacaciones`/`vacaciones-admin`): "Media" (`media` con una estrella, `'—'` si `null` — sin reseñas todavía), "Total de reseñas", y una cuarta zona con la distribución por estrellas (5 barras/filas simples, `distribucion[5]` a `distribucion[1]`, o reutilizando `ui-card` una vez por puntuación si no se justifica un componente nuevo de gráfico de barras — sin introducir una librería de gráficos para 5 valores).
   - `ui-select` de filtro por puntuación (1 a 5 + "Todas", sentinel `'TODAS'`, mismo patrón que el resto de filtros de selección única del proyecto).
   - `ui-table` paginada, `PAGE_SIZE = 10` (igual que `facturas-admin`): columnas Autor (con `autorFotoUrl` como avatar pequeño si está informado, o inicial del nombre si no), Puntuación (estrellas, p. ej. `★★★★☆`, o un `ui-badge` con el número — a decidir en implementación cuál se lee mejor en la tabla), Comentario (truncado con `text-overflow: ellipsis` + `title` con el texto completo, `'—'` si `null`), Fecha (`fechaPublicacion`, formateada igual que el resto del proyecto).
   - Sin columna de acciones: el listado es de solo lectura (`back/fase4-2-back.md`), no hay nada que el Admin pueda editar sobre una reseña desde aquí.
3. **[Frontend, baja]** Nuevas claves i18n bajo `valoraciones.admin`: `resumen.{media,total,sinResenas}`, `filtros.{puntuacion,todas}`, `tabla.{autor,puntuacion,comentario,fecha}`.

## Decisiones técnicas

- **Sin librería de gráficos para la distribución de estrellas**: 5 valores fijos se representan perfectamente con barras/filas simples de CSS (`width` proporcional al `%` sobre el total) — añadir una librería de charts para esto sería sobreingeniería, coherente con el criterio general del proyecto de no introducir dependencias sin necesidad clara (`plan-implementacion-general.md`, Fase 2.3).
- **Reutiliza `PAGE_SIZE = 10` y el patrón de `ui-select` con sentinel `'TODAS'`** ya establecidos en `facturas-admin`/`vacaciones-admin`, sin inventar una convención nueva solo para este módulo.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase4-2-back.md` y con que exista una conexión activa (`fase4-1-front.md`) — sin reseñas sincronizadas, el resumen y la tabla se muestran vacíos (`total = 0`, tabla con el mensaje vacío estándar de `ui-table`), no como un error.
- **Riesgo bajo**: reseñas con comentarios muy largos podrían desbordar la celda de la tabla si el truncado CSS no se aplica correctamente en todos los anchos de columna — a verificar visualmente en implementación, mismo tipo de ajuste ya hecho en `facturas-admin.scss` para evitar colapsos de columna.

## Criterios de aceptación técnicos

- Con reseñas sincronizadas, la cabecera muestra la media correcta (redondeada), el total, y la distribución por estrellas suma exactamente el total.
- Sin ninguna reseña sincronizada, la cabecera muestra un estado vacío claro (`'—'` en media, `0` en total) en vez de un error o una tabla rota.
- Filtrar por una puntuación concreta (p. ej. 5 estrellas) reduce la tabla a solo esas reseñas.
- Un comentario largo se trunca visualmente en la tabla sin romper el layout de la fila ni de la columna.
- La tabla no muestra ninguna columna ni botón de acciones (listado de solo lectura).
