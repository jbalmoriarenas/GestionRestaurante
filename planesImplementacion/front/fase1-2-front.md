# Plan de Implementación — Fase 1.2 (Frontend): Vacaciones

Consume el contrato de [`../back/fase1-2-back.md`](../back/fase1-2-back.md). Reutiliza layout/guards/`ui-*` de [`fase0-front.md`](./fase0-front.md). El componente `ui-calendar` creado aquí lo reutiliza sin cambios [`fase1-4-front.md`](./fase1-4-front.md) (vista Admin de vacaciones).

## Contexto y objetivo

Vista "Mis vacaciones": resumen anual (total/disponibles/pendientes), calendario mensual con festivos y días propios marcados, formulario para solicitar días nuevos y listado de solicitudes propias (con opción de cancelar las pendientes).

## Estructura (se añade a `features/` y `shared/ui/`)

```
core/models/vacaciones.model.ts
core/services/vacaciones.service.ts
shared/ui/ui-calendar/ui-calendar.ts / .html / .scss   ← nuevo, reutilizable
features/vacaciones/mis-vacaciones/mis-vacaciones.ts / .html / .scss
```

Ruta `/vacaciones` protegida con `permissionGuard('VACACIONES', 'EDICION')`; añadir `VACACIONES` a `menu-items.ts` como un nuevo `MenuGroup` (o como `MenuChild` de uno ya existente, si para entonces cuelga de un grupo compartido) siguiendo el modelo `MenuLeaf | MenuGroup` introducido en `fase1-1-front.md` (ya no es el mapa plano `CodigoVista → {ruta, label, icono}` de Fase 0), para que aparezca en el sidebar de ambos roles.

## Modelos (`core/models/vacaciones.model.ts`)

```typescript
export type EstadoSolicitud = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA';
export type TipoDiaCalendario = 'FESTIVO' | 'APROBADO' | 'PENDIENTE';

export interface ResumenVacaciones {
  total: number;
  aprobados: number;
  pendientes: number;
  disponibles: number;
}

export interface DiaCalendario {
  fecha: string;           // yyyy-MM-dd
  tipo: TipoDiaCalendario;
}

export interface SolicitudVacaciones {
  id: number;
  fechaInicio: string;
  fechaFin: string;
  diasSolicitados: number;
  estado: EstadoSolicitud;
  fechaSolicitud: string;
  motivoRechazo: string | null;
}

export type SolicitudVacacionesRequest = Pick<SolicitudVacaciones, 'fechaInicio' | 'fechaFin'>;
```

## Desglose de subtareas

### Componente reutilizable `ui-calendar` (usado también por la vista Admin, `fase1-4-front.md`)
1. **[Frontend, alta]** `ui-calendar`: recibe `mes`/`anio` (o un `Date` de referencia) y `marcadores: {fecha: string, variante: 'festivo' | 'aprobado' | 'pendiente'}[]` vía `input()`; pinta una cuadrícula mensual (7 columnas, cabecera L-M-X-J-V-S-D) con cada día marcado con un `ui-badge`/punto de color según su variante (reutiliza los tokens de color ya existentes: `--color-danger` para festivo, `--color-success` para aprobado, `--color-warning` para pendiente — sin introducir colores nuevos). Emite `mesChange` (navegación mes anterior/siguiente) y, opcionalmente, `rangoSeleccionado` (para que `mis-vacaciones` pueda usarlo como selector de fechas si se decide en implementación, en vez de dos `ui-input type="date"` sueltos).
2. **[Frontend, baja]** `ui-calendar.scss`: grid CSS, día actual resaltado con `--color-primary-light`, días fuera del mes en curso atenuados.

### Vista del empleado
3. **[Frontend, baja]** `VacacionesService`: `obtenerResumen(anio)`, `obtenerCalendario(anio)`, `solicitar(dto)`, `cancelar(id)`, `listarMisSolicitudes(page, size)`.
4. **[Frontend, media]** `MisVacacionesComponent`:
   - Cabecera con 3 `ui-card` (Total / Disponibles / Pendientes) alimentadas por `obtenerResumen(anioActual)`. El `total` ya llega prorrateado desde el backend si el empleado se dio de alta ese mismo año (ver `back/fase1-2-back.md`) — el front no hace ningún cálculo, solo lo muestra; opcionalmente, un texto de ayuda bajo la tarjeta "Total" cuando `total` no sea el `dias_vacaciones_anuales` completo del empleado, indicando que está prorrateado por su fecha de alta.
   - `ui-calendar` con los `marcadores` construidos a partir de `obtenerCalendario(anioActual)`.
   - Formulario de solicitud: dos `ui-input type="date"` (fechaInicio/fechaFin) + `ui-button` "Solicitar"; validación básica en el front (fechaFin ≥ fechaInicio) antes de llamar al backend, que es quien valida disponibilidad real.
   - **Revisión post-implementación (a petición del usuario)**: no tiene sentido solicitar vacaciones en un periodo ya pasado. Se añade el validador `fechaFinNoAnteriorAInicio`, que además comprueba `inicio < hoyLocalISO()` y marca el control con el error `{fechaPasada: true}` (mensaje `vacaciones.misVacaciones.form.errores.fechaPasada`); `fechaInicioMin()`/`fechaFinMin()` fijan el atributo `min` nativo de ambos `ui-input type="date"` a `hoyLocalISO()`, para que el propio selector de fecha del navegador ya desanime elegir una fecha pasada, no solo el mensaje de error al enviar. `hoyLocalISO()` (`core/date-utils.ts`, nuevo) calcula la fecha local `yyyy-MM-dd` a partir de `Date.getFullYear()/getMonth()/getDate()` en vez de `Date.toISOString()`, que siempre da la fecha en UTC — con `toISOString()`, en las horas en que la fecha local y la UTC difieren (p. ej. bien entrada la noche en horario de invierno español), "hoy" quedaría calculado mal y el validador podría rechazar (o aceptar) el día de hoy incorrectamente.
   - `ui-table` con el listado de `listarMisSolicitudes()` (fechas, días, estado vía `ui-badge`, motivo de rechazo si aplica) y botón "Cancelar" visible solo si `estado === 'PENDIENTE'`.
   - **Revisión post-implementación (a petición del usuario)**: se añade una columna **"Motivo"** dedicada a la tabla (antes el motivo de rechazo no se mostraba en ningún sitio del listado del empleado). Muestra `'—'` si `motivoRechazo` es `null`; si tiene valor, lo traduce a través de `motivoRechazoLabel(motivo)`, que comprueba si el valor pertenece al catálogo cerrado `MOTIVOS_RECHAZO_VACACIONES` (introducido en `fase1-4-front.md` para el selector de motivo del Admin) y, si es así, lo traduce vía `vacaciones.motivosRechazo.<código>`; si no pertenece al catálogo (una solicitud rechazada antes de ese cambio, con un motivo de texto libre), se muestra tal cual, sin intentar traducir una clave que no existe — evita imprimir una clave i18n sin resolver para datos anteriores al cambio.
5. **[Frontend, baja]** Tras solicitar/cancelar con éxito, refrescar resumen + calendario + listado (tres llamadas, sin estado compartido complejo — el volumen de datos no justifica más que eso).

## Decisiones técnicas

- **`ui-calendar` genérico desde esta fase**, igual razón que `ui-table` en Fase 0: evita reimplementar la cuadrícula de calendario en `fase1-4-front.md`.
- **El front valida solo lo obvio** (rango de fechas coherente); toda la lógica de negocio real (días laborables, disponibilidad, solapamientos) vive en el backend — evita duplicar `calcularDiasLaborables` en TypeScript y arriesgar que las dos implementaciones diverjan.
- **Reutiliza tokens de color existentes** para las variantes del calendario (festivo/aprobado/pendiente = danger/success/warning ya definidos en `_tokens.scss`), sin declarar ningún color nuevo — coherente con la corrección aplicada en la revisión UI de Fase 0.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase1-2-back.md`: sin los 5 endpoints de ese documento, esta vista no puede probarse end-to-end.
- **Dependencia (añadida en revisión posterior)** con `front/fase1-4-front.md`: la columna "Motivo" de esta vista traduce `motivoRechazo` usando el catálogo `MOTIVOS_RECHAZO_VACACIONES`, que vive en `vacaciones.model.ts` (este documento) pero cuyos valores los define el selector de rechazo de la vista Admin — si esa fase añade/renombra un motivo, esta columna hay que revisarla también.
- **Riesgo bajo**: si el empleado solicita un rango que abarca cambio de año (p.ej. 28 dic–3 ene), el resumen anual mostrado corresponde al año de `fechaInicio` (coherente con cómo lo calcula el backend) — dejar claro en el copy de la UI para que no resulte confuso.

## Criterios de aceptación técnicos

- El calendario de `/vacaciones` marca correctamente los festivos sembrados en BBDD sin que el empleado tenga que hacer nada.
- Solicitar un rango con más días de los disponibles muestra el error devuelto por el backend (409) sin romper el formulario ni perder las fechas introducidas.
- Intentar solicitar con una `fechaInicio` anterior a hoy (hora local) marca el formulario inválido con el error `fechaPasada`, sin llegar a llamar al backend; el selector de fecha nativo ya impide, vía `min`, elegir un día pasado.
- Tras una solicitud exitosa, el calendario y el resumen se refrescan automáticamente mostrando los nuevos días en estado "pendiente" (ámbar).
- Cancelar una solicitud pendiente la hace desaparecer del calendario y devuelve esos días a "disponibles" en el resumen, sin recargar la página.
- Una solicitud ya `APROBADA` o `RECHAZADA` no muestra el botón "Cancelar".
- Un empleado dado de alta a mitad del año en curso ve en la tarjeta "Total" el cupo ya prorrateado que devuelve el backend, no los días completos de `dias_vacaciones_anuales`.
- La columna "Motivo" muestra un guion cuando la solicitud no está rechazada, y el texto traducido del motivo (en el idioma activo) cuando sí lo está.
