# Plan de Implementación — Fase 1.1 (Frontend): Fichaje

Consume el contrato de [`../back/fase1-1-back.md`](../back/fase1-1-back.md). Reutiliza el layout, guards y catálogo `ui-*` de [`fase0-front.md`](./fase0-front.md).

## Contexto y objetivo

Vista "Mi Fichaje" estilo Woffu para el empleado: un único botón grande que alterna entre "Iniciar" y "Finalizar turno", con un cronómetro visible mientras el turno está en curso.

## Análisis y supuestos

- Esta app es **zoneless** (sin `zone.js`, ver `plan-implementacion-general.md`): Angular solo repinta automáticamente lo que depende de una **signal**. El cronómetro (que se actualiza vía `setInterval`, es decir, fuera de cualquier ciclo de detección de cambios disparado por un evento de plantilla) **debe** guardarse en una `signal<number>` (segundos transcurridos), nunca en un campo de clase plano — es exactamente el mismo bug que se corrigió en `ui-input`/`ui-select` durante la Fase 0 (ver historial de esa corrección): un campo plano mutado desde un callback asíncrono no dispara repintado en esta app.
- El cronómetro se reconstruye siempre desde `GET /api/fichajes/actual` al entrar en la vista (no se asume que el usuario nunca recargue la página).
- **Verificación de ubicación (decisión confirmada con el usuario, ver `back/fase1-1-back.md`)**: al pulsar "Iniciar", el front pide la posición GPS del dispositivo (`navigator.geolocation.getCurrentPosition`) y envía `{lat, lng}` al backend, que valida la geovalla. Si el navegador deniega el permiso o el GPS no está disponible, el front **no llega a llamar al backend**: muestra un error claro y no fichaje (el empleado tiene como red de seguridad la corrección manual del Admin, ya prevista en `fase1-3-front.md`).
- **"Fichajes de hoy" en vez de histórico paginado** (decisión revisada a petición del usuario tras la primera implementación): la sección bajo el botón no muestra el histórico completo del empleado, solo los fichajes de la fecha de hoy, con un pie que suma las horas ya imputadas y las compara contra las horas teóricas del turno — usa el endpoint dedicado `GET /api/fichajes/mios/hoy` (`fase1-1-back.md`), no `GET /api/fichajes/mios` (que sigue existiendo en el backend para un futuro histórico completo, pero esta vista ya no lo consume).

## Estructura (se añade a `features/`)

```
core/models/fichaje.model.ts
core/services/fichaje.service.ts
core/services/geolocation.service.ts
features/fichajes/mi-fichaje/mi-fichaje.ts / .html / .scss
```

Ruta `/fichajes` protegida con `permissionGuard('FICHAJES', 'EDICION')` (ya existente desde Fase 0), sustituye el `FichajesPlaceholder` actual.

**El enlace de menú ya no es una entrada plana**: `menu-items.ts` (Fase 0) se amplió a un modelo de grupos desplegables (`MenuLeaf | MenuGroup`, ver "Sidebar desplegable" más abajo) porque el usuario pidió que "Fichajes" y las vistas que cuelguen de él (esta y, en el futuro, la vista Admin de `fase1-3-front.md`) se accedan desde un desplegable en el sidebar, no como enlaces sueltos. `FICHAJES` pasó de ser un `MenuLeaf` a un `MenuGroup` con un único hijo por ahora (`{ ruta: '/fichajes', labelKey: 'fichajesMio', vista: 'FICHAJES', nivelMinimo: 'EDICION' }`).

## Modelos (`core/models/fichaje.model.ts`)

```typescript
export interface FichajeActual {
  enCurso: boolean;
  horaEntrada: string | null;   // ISO 8601, solo si enCurso = true
}

export interface Fichaje {
  id: number;
  fecha: string;                // yyyy-MM-dd
  horaEntrada: string;          // ISO 8601
  horaSalida: string | null;
  horasCalculadas: number | null;
  observaciones: string | null;
}

export interface FichajeResumenHoy {
  fichajes: Fichaje[];
  horasImputadas: number;
  horasTeoricas: number;
}
```

## Desglose de subtareas

1. **[Frontend, baja]** `FichajeService`: `obtenerActual()`, `iniciar(lat: number, lng: number)`, `finalizar()`, `listarMios(page, size)` (histórico completo, sin consumidor todavía — se deja listo para una vista futura), `obtenerResumenHoy()`.
2. **[Frontend, media]** `GeolocationService` (nuevo, pequeño wrapper en `core/services/`): `obtenerPosicionActual(): Promise<{lat: number; lng: number}>` — envuelve `navigator.geolocation.getCurrentPosition({ enableHighAccuracy: true, timeout: 10000, maximumAge: 0 })` en una `Promise`, traduciendo los códigos de error del navegador (`PERMISSION_DENIED`, `POSITION_UNAVAILABLE`, `TIMEOUT`) a un error tipado que el componente pueda mostrar con un mensaje i18n específico por caso. Aislar esto en un servicio propio (en vez de llamar a la API del navegador directamente desde el componente) permite testear `MiFichajeComponent` sin depender de `navigator.geolocation` real.
3. **[Frontend, alta]** `MiFichajeComponent`:
   - `ngOnInit`: llama `obtenerActual()`; si `enCurso`, calcula los segundos ya transcurridos desde `horaEntrada` y arranca el intervalo. Llama también `cargarResumenHoy()` (ver punto 4). Se suscribe a `document.addEventListener('visibilitychange', ...)` para refrescar `fechaHoy` si el empleado deja la pestaña abierta pasada la medianoche (ver punto 5); se limpia junto al intervalo en `DestroyRef.onDestroy`.
   - `signal segundosTranscurridos = signal(0)`; `computed tiempoFormateado` → `HH:MM:SS`. `signal obteniendoUbicacion = signal(false)` para el estado `loading` del botón "Iniciar" mientras se espera el GPS. **`signal finalizandoTurno = signal(false)`** (añadido en revisión de MR): mismo tipo de estado de carga pero para el botón "Finalizar turno", que originalmente no lo tenía.
   - Botón único (`ui-button`, tamaño grande): `variant="primary"` + `[loading]="obteniendoUbicacion()"` + texto "Iniciar" si no hay turno en curso; `variant="secondary"` + `[loading]="finalizandoTurno()"` + texto "Finalizar turno" + el cronómetro visible encima si lo hay.
   - `iniciar()`: `obteniendoUbicacion.set(true)` → `geolocationService.obtenerPosicionActual()` → si falla, notificación de error i18n, `obteniendoUbicacion.set(false)`, **no** se llama al backend. Si tiene éxito, llama `fichajeService.iniciar(lat, lng)`: si 201, resetea `segundosTranscurridos`, arranca el intervalo y refresca el resumen de hoy; si 403 (fuera de la geovalla), el toast ya lo muestra el `errorInterceptor` global; si 409 (doble pestaña), `resincronizar()` (vuelve a llamar `obtenerActual()`).
   - `finalizar()`: `finalizandoTurno.set(true)` → `fichajeService.finalizar()`. En éxito: apaga el loading, para el intervalo, `enCurso=false`, notificación con las horas del turno, refresca el resumen de hoy. **En error** (añadido en revisión de MR, no estaba en la versión original): solo apaga `finalizandoTurno`, sin tocar `enCurso` — si la llamada falla, el turno sigue realmente abierto, así que dejar el estado como estaba es lo correcto; el toast del error ya lo muestra el `errorInterceptor` global.
4. **[Frontend, media]** Sección **"Fichajes de hoy"** (rediseñada; en la primera versión era "Mis últimos fichajes" con histórico paginado): `ui-table` (entrada, salida, **tiempo** — sin columna de fecha, todas las filas son de hoy) alimentada por `obtenerResumenHoy()`. Bajo la tabla, un pie con dos cifras: "Horas imputadas" y "Horas teóricas del turno". El número de horas imputadas se pinta con `--color-danger` mientras sea menor que las teóricas (modificador BEM `mi-fichaje-page__resumen--bajo`), y vuelve al color normal en cuanto se alcanzan o superan.
   - **Revisión post-implementación (a petición del usuario, coincide con la revisión de `fase1-3-front.md`)**: la columna se llamaba "Horas" y mostraba el decimal crudo (`horasCalculadas`, p. ej. `7.5`); pasa a llamarse **"Tiempo"** y a mostrarse como duración `HH:mm:ss` (`07:30:00`), usando `horasATiempo(...)` del nuevo `shared/utils/tiempo.util.ts` (introducido y documentado en detalle en `fase1-3-front.md`, subtarea 9, porque la vista Admin de fichajes necesitaba lo mismo en varias columnas). Aprovechando el mismo util, el cronómetro del turno en curso (`tiempoFormateado`, antes una función privada de este componente) pasa a usar la función compartida `segundosATiempo(...)` en vez de su propia copia — mismo resultado, sin duplicar la lógica de formateo.
   - **Segunda revisión post-implementación (a petición del usuario, más tardía que la anterior)**: el pie de "Horas imputadas"/"Horas teóricas" seguía mostrando el decimal crudo (`0.06 h`) aunque la tabla de arriba ya usara `HH:mm:ss` — se corrige para que ambas cifras del pie pasen también por `formatearHorasComoTiempo` (= `horasATiempo`), quedando coherentes con el resto de la vista. Por el mismo motivo, la notificación de éxito al finalizar turno (`fichajes.miFichaje.turnoFinalizado`) deja de interpolar `fichaje.horasCalculadas` en crudo y pasa a interpolar `horasATiempo(fichaje.horasCalculadas)` (con `'—'` si viniera `null`); el texto i18n se ajusta de `"Turno finalizado: {{horas}} h trabajadas"` a `"Turno finalizado: {{horas}} trabajadas"` (la "h" ya no hace falta, el propio valor interpolado es una duración `HH:mm:ss`, no un número).
5. **[Frontend, baja]** Fecha de hoy sobre el cronómetro (`fechaHoy`, no estaba en el diseño original): `computed` con `Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })` — `locale` es `'en-GB'` o `'es-ES'` según `translateService.currentLang()` (no se usa el `DatePipe` de Angular para esto porque el proyecto no registra datos de locale; `DatePipe` con formatos con nombre como `'fullDate'` dependería de esos datos ausentes, mientras que `Intl.DateTimeFormat` no los necesita). Se recalcula si cambia el idioma o al volver a la pestaña (señal `fechaTick` incrementada en el listener de `visibilitychange`).
6. **[Frontend, baja]** Nuevas claves i18n (`es.json`/`en.json`, bajo `fichajes.miFichaje`, siguiendo el patrón ya establecido — nada de texto hardcodeado en la interfaz): `fichajesHoy`, `fichajesHoyVacio`, `tabla.{entrada,salida,horas}`, `resumen.{imputadas,teoricas}`, `errores.{permisoDenegado,posicionNoDisponible,tiempoAgotado}`.
7. **[Frontend, baja]** Actualizar `app.routes.ts` para que `FICHAJES` apunte a `MiFichajeComponent` en vez del placeholder.
8. **[Frontend, alta]** **Sidebar desplegable** (no estaba en el diseño original de esta fase; añadido a petición del usuario después de la primera implementación) — ver sección dedicada más abajo.
9. **[Frontend, media]** Tests (añadidos en la revisión de MR, no en la primera entrega): `fichaje.service.spec.ts` (HTTP mockeado con `HttpTestingController`, uno por método), `geolocation.service.spec.ts` (éxito, los tres tipos de error, navegador sin soporte), `mi-fichaje.spec.ts` (estado inicial con/sin turno abierto, `iniciar()` con fallo/éxito de geolocalización, `finalizar()` con éxito/error).

## Sidebar desplegable (cambio transversal, afecta a `fase0-front.md`)

El usuario pidió que "Fichajes" y las vistas que dependan de él se accedan desde el sidebar mediante un grupo desplegable, no como enlaces sueltos — y que el patrón sirva también para los grupos que añadan `fase1-2-front.md` (Vacaciones) y las vistas Admin de `fase1-3-front.md`/`fase1-4-front.md`. Esto amplía (no sustituye del todo) lo que `fase0-front.md` dejó como `menu-items.ts` plano:

- **`layout/menu-items.ts`**: `MenuItem` pasa a ser `MenuLeaf | MenuGroup` (discriminated union por la presencia de `children`). Un `MenuGroup` tiene `labelKey`, `icono` y `children: MenuChild[]`; cada `MenuChild` es `{ ruta, labelKey, vista, nivelMinimo? }`, exactamente los mismos campos que antes tenía un `MenuLeaf` (menos el icono, que solo lleva el grupo). `FICHAJES` es ahora un `MenuGroup` con un `MenuChild` (`fichajesMio` → `/fichajes`).
- **`layout/sidebar/sidebar.ts`**:
  - `items` (computed): resuelve visibilidad igual que antes para las hojas; para un grupo, filtra sus `children` por permiso y descarta el grupo entero si no le queda ninguno visible.
  - `currentUrl` (`toSignal` sobre `router.events`, filtrado a `NavigationEnd`): la ruta activa.
  - `openGroups` (computed): por cada grupo, `manualToggle[labelKey] ?? (algún hijo coincide con currentUrl)` — abierto automáticamente si la ruta activa es una de sus hijas, salvo que haya un cierre/apertura manual.
  - `toggleGroup(labelKey)`: invierte el estado actual en `manualToggle` (un `signal<Record<string, boolean>>`).
  - **`effect` de reseteo** (añadido en la revisión de MR — no estaba en la primera versión y era un bug real): sin él, cerrar manualmente un grupo lo deja cerrado para siempre, aunque luego se navegue a una de sus rutas hijas — el `effect` observa `currentUrl()` y limpia `manualToggle` en cada navegación, para que un cierre manual solo dure "mientras no navegues".
- **`layout/sidebar/sidebar.html`**: cada `<li>` de nivel superior es un `<a>` (hoja, como antes) o un `<button>` con `aria-expanded` + una `<ul>` de hijos condicional (grupo). El icono del grupo y el chevron de expandir/colapsar llevan `aria-hidden="true"` (corrección de la revisión UI: son decorativos, el texto adjunto ya comunica lo mismo).
- **`shared/ui/ui-icon`**: nuevo `IconName` `'chevron'`.
- **Tests**: `sidebar.spec.ts` — el grupo se abre solo si la ruta activa es suya; un cierre manual lo colapsa; navegar fuera y volver lo abre de nuevo (verifica el fix del `effect`).

## Decisiones técnicas

- **Signal para el cronómetro**, no campo plano — ver "Análisis y supuestos". Aplica también al estado `obteniendoUbicacion` y a cualquier otro estado mutado desde un callback asíncrono (GPS incluido).
- **Formateo de tiempo (`segundosATiempo`/`horasATiempo`) centralizado en `shared/utils/tiempo.util.ts`** (revisión post-implementación, ver punto 4): antes era una función privada de este componente; se extrajo a un util compartido en cuanto `fase1-3-front.md` necesitó la misma conversión decimal→duración en la vista Admin, para no mantener dos copias que pudieran divergir en el redondeo.
- **`GeolocationService` como capa de aislamiento**: nunca se llama a `navigator.geolocation` directamente desde el componente — permite testear `MiFichajeComponent` con un servicio mockeado y centraliza la traducción de los códigos de error del navegador a mensajes i18n.
- **Sin `lat`/`lng` en los modelos expuestos al propio empleado** (`FichajeActual`, `Fichaje`): son datos de auditoría interna (ver `bbdd/fase1-1-bbdd.md`), el empleado no necesita verlos.
- **Un único componente, dos estados visuales** (no dos rutas ni dos componentes): el botón y el cronómetro son la misma vista con clases condicionales, igual de simple que el patrón Woffu real.
- **Reutiliza `ui-button`, `ui-card`, `ui-table`, `ui-badge`** ya construidos en Fase 0; no se necesita ningún componente `ui-*` nuevo para esta vista.
- **Modificadores BEM siempre con doble guion** (`--modificador`, nunca `-modificador`), y la regla del modificador va en un selector plano, no anidada varios niveles dentro del bloque (corrección de la revisión UI sobre `mi-fichaje-page__resumen--bajo`, que originalmente se llamaba con un solo guion y vivía anidada 4 niveles).
- **Iconos decorativos siempre con `aria-hidden="true"`** cuando acompañan a un texto visible que ya comunica lo mismo (corrección de la revisión UI sobre los `<ui-icon>` del sidebar) — Angular pasa los atributos no reconocidos al elemento host, así que ponerlo directamente en `<ui-icon>` es suficiente y `aria-hidden` cascada a los descendientes (incluido el `<svg>` interno).
- **Ningún color nuevo en crudo**: cualquier fondo/color introducido reutiliza los tokens de `_tokens.scss` (p. ej. `.sidebar-backdrop` usa `var(--neutral-10)` + `opacity`, no un `rgba(...)` inventado).

## Riesgos y dependencias

- **Dependencia dura** con `back/fase1-1-back.md`: sin `POST /entrada` (ahora con `{lat, lng}`), `POST /salida` y `GET /actual` funcionando, la vista no se puede probar end-to-end.
- **Riesgo medio — permiso de geolocalización denegado permanentemente**: si el empleado deniega el permiso del navegador de forma persistente ("no volver a preguntar"), no podrá fichar por la app hasta que lo reactive manualmente en los ajustes del navegador/móvil — el mensaje de error debe explicarlo (no solo "ubicación denegada"), y recordar que la corrección manual del Admin sigue disponible.
- **Riesgo bajo**: la app requiere HTTPS para `navigator.geolocation` en la mayoría de navegadores (ya cubierto en cualquier despliegue real; en `localhost` de desarrollo los navegadores lo permiten igualmente sin HTTPS).
- **Riesgo bajo**: si el reloj del dispositivo del empleado está desincronizado, el cronómetro visual puede desviarse ligeramente respecto al servidor — no es crítico porque `horasCalculadas` real siempre se recalcula en el backend al fichar salida.

## Criterios de aceptación técnicos

- Al entrar en `/fichajes` sin turno en curso, se ve el botón "Iniciar" y ningún cronómetro.
- Al pulsar "Iniciar" con permiso de ubicación concedido y dentro del radio del restaurante, el botón muestra brevemente estado de carga, luego cambia a "Finalizar turno" y el cronómetro empieza a avanzar cada segundo (verifica que la corrección de signals de `ui-input`/`ui-select` de Fase 0 se aplica también aquí).
- Al pulsar "Iniciar" habiendo denegado el permiso de ubicación, se muestra un mensaje de error traducido (i18n) sin llamar al backend, y el botón vuelve a su estado normal (no se queda cargando indefinidamente).
- Al pulsar "Iniciar" con ubicación concedida pero fuera del radio del restaurante, se muestra el mensaje de error 403 del backend y no se arranca ningún cronómetro.
- Recargar la página (F5) mientras hay un turno en curso reconstruye el cronómetro con el tiempo correcto ya transcurrido, no lo resetea a 0.
- Al pulsar "Finalizar turno" (sin pedir ubicación), el botón vuelve a "Iniciar", el cronómetro desaparece y se muestra una notificación con las horas trabajadas.
- Un empleado con dos pestañas abiertas que pulsa "Iniciar" en ambas (ambas dentro del radio) no rompe la vista: la segunda pestaña, tras el 409, se resincroniza mostrando el estado real (en curso, con la hora de entrada de la primera).
- Al pulsar "Finalizar turno" y que la llamada al backend falle (p. ej. error de red), el botón deja de mostrar carga pero **no** vuelve a "Iniciar": el turno sigue considerado en curso (porque realmente lo sigue estando en el backend) y se muestra el toast de error correspondiente.
- Encima del cronómetro/botón se muestra la fecha de hoy (`fechaHoy`) en el idioma activo; si el empleado deja la pestaña abierta y vuelve a ella al día siguiente, la fecha se actualiza sin necesidad de recargar la página.
- La sección "Fichajes de hoy" solo lista fichajes de la fecha actual (nunca días anteriores) y su pie muestra las horas imputadas y las teóricas del turno; mientras las imputadas sean menores que las teóricas, el número se muestra en color de aviso (`--color-danger`), y pasa al color normal en cuanto se igualan o superan.
- La columna "Tiempo" de "Fichajes de hoy", y las dos cifras del pie ("Horas imputadas"/"Horas teóricas"), muestran la duración como `HH:mm:ss` (p. ej. `07:30:00` para 7.5h, `00:03:36` para 0.06h), nunca el decimal crudo.
- La notificación al finalizar turno muestra el tiempo trabajado en formato `HH:mm:ss` (p. ej. "Turno finalizado: 07:30:00 trabajadas"), no un decimal.
- En el sidebar, "Fichajes" aparece como un grupo desplegable: se abre automáticamente si la ruta activa es una de sus hijas, un clic lo abre/cierra manualmente, y ese cierre manual no persiste al navegar a otra parte de la aplicación y volver.
- Todos los tests de `fichaje.service.spec.ts`, `geolocation.service.spec.ts`, `mi-fichaje.spec.ts` y `sidebar.spec.ts` pasan (`ng test`).
