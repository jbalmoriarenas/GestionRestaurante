# Plan de Implementación — Fase 1.1 (Frontend): Fichaje

Consume el contrato de [`../back/fase1-1-back.md`](../back/fase1-1-back.md). Reutiliza el layout, guards y catálogo `ui-*` de [`fase0-front.md`](./fase0-front.md).

## Contexto y objetivo

Vista "Mi Fichaje" estilo Woffu para el empleado: un único botón grande que alterna entre "Iniciar" y "Finalizar turno", con un cronómetro visible mientras el turno está en curso.

## Análisis y supuestos

- Esta app es **zoneless** (sin `zone.js`, ver `plan-implementacion-general.md`): Angular solo repinta automáticamente lo que depende de una **signal**. El cronómetro (que se actualiza vía `setInterval`, es decir, fuera de cualquier ciclo de detección de cambios disparado por un evento de plantilla) **debe** guardarse en una `signal<number>` (segundos transcurridos), nunca en un campo de clase plano — es exactamente el mismo bug que se corrigió en `ui-input`/`ui-select` durante la Fase 0 (ver historial de esa corrección): un campo plano mutado desde un callback asíncrono no dispara repintado en esta app.
- El cronómetro se reconstruye siempre desde `GET /api/fichajes/actual` al entrar en la vista (no se asume que el usuario nunca recargue la página).
- **Verificación de ubicación (decisión confirmada con el usuario, ver `back/fase1-1-back.md`)**: al pulsar "Iniciar", el front pide la posición GPS del dispositivo (`navigator.geolocation.getCurrentPosition`) y envía `{lat, lng}` al backend, que valida la geovalla. Si el navegador deniega el permiso o el GPS no está disponible, el front **no llega a llamar al backend**: muestra un error claro y no fichaje (el empleado tiene como red de seguridad la corrección manual del Admin, ya prevista en `fase1-3-front.md`).

## Estructura (se añade a `features/`)

```
core/models/fichaje.model.ts
core/services/fichaje.service.ts
features/fichajes/mi-fichaje/mi-fichaje.ts / .html / .scss
```

Ruta `/fichajes` protegida con `permissionGuard('FICHAJES', 'EDICION')` (ya existente desde Fase 0), sustituye el `FichajesPlaceholder` actual.

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
```

## Desglose de subtareas

1. **[Frontend, baja]** `FichajeService`: `obtenerActual()`, `iniciar(lat: number, lng: number)`, `finalizar()`, `listarMios(page, size)` — mismo patrón que `EmpleadoService`.
2. **[Frontend, media]** `GeolocationService` (nuevo, pequeño wrapper en `core/services/`): `obtenerPosicionActual(): Promise<{lat: number; lng: number}>` — envuelve `navigator.geolocation.getCurrentPosition({ enableHighAccuracy: true, timeout: 10000, maximumAge: 0 })` en una `Promise`, traduciendo los códigos de error del navegador (`PERMISSION_DENIED`, `POSITION_UNAVAILABLE`, `TIMEOUT`) a un error tipado que el componente pueda mostrar con un mensaje i18n específico por caso. Aislar esto en un servicio propio (en vez de llamar a la API del navegador directamente desde el componente) permite testear `MiFichajeComponent` sin depender de `navigator.geolocation` real.
3. **[Frontend, alta]** `MiFichajeComponent`:
   - `ngOnInit`: llama `obtenerActual()`; si `enCurso`, calcula los segundos ya transcurridos desde `horaEntrada` y arranca el intervalo.
   - `signal segundosTranscurridos = signal(0)`; `computed tiempoFormateado` → `HH:MM:SS`. `signal obteniendoUbicacion = signal(false)` para el estado `loading` del botón mientras se espera el GPS (puede tardar 1–2 s).
   - `setInterval(() => this.segundosTranscurridos.update(s => s + 1), 1000)`, limpiado en `ngOnDestroy` (o `DestroyRef.onDestroy`).
   - Botón único (`ui-button`, tamaño grande, `[loading]="obteniendoUbicacion()"`): `variant="primary"` + texto "Iniciar" si no hay turno en curso; `variant="secondary"` + texto "Finalizar turno" + el cronómetro visible al lado/encima si lo hay.
   - `iniciar()`: `obteniendoUbicacion.set(true)` → `geolocationService.obtenerPosicionActual()` → si falla (permiso denegado/timeout/no disponible), notificación de error con el mensaje i18n correspondiente al tipo de fallo, `obteniendoUbicacion.set(false)`, **no** se llama al backend. Si tiene éxito, llama `fichajeService.iniciar(lat, lng)`: si 201, resetea `segundosTranscurridos` a 0 y arranca el intervalo; si 403 (fuera de la geovalla), la notificación de error ya la muestra el `errorInterceptor` global con el mensaje del backend, solo hay que `obteniendoUbicacion.set(false)`; si 409 (edge case de doble pestaña), vuelve a llamar `obtenerActual()` para resincronizar el estado real.
   - `finalizar()`: sin geolocalización (no se requiere para cerrar turno, ver "Análisis y supuestos"); para el intervalo, muestra notificación de éxito con las horas de ese turno (`horasCalculadas` de la respuesta).
4. **[Frontend, baja]** Sección "Mis últimos fichajes": `ui-table` simple (fecha, entrada, salida, horas) alimentada por `listarMios()`, sin filtros (los filtros por fecha/empleado son de la vista Admin, `fase1-3-front.md`).
5. **[Frontend, baja]** Nuevas claves i18n (`es.json`/`en.json`, bajo `fichajes.miFichaje.errores`, siguiendo el patrón ya establecido en la revisión UI de Fase 0 — nada de texto hardcodeado en la interfaz): `permisoDenegado`, `posicionNoDisponible`, `tiempoAgotado` (para los tres tipos de error de `GeolocationService`).
6. **[Frontend, baja]** Actualizar `app.routes.ts` y `menu-items.ts` (creado en Fase 0) para que `FICHAJES` apunte a `MiFichajeComponent` en vez del placeholder.

## Decisiones técnicas

- **Signal para el cronómetro**, no campo plano — ver "Análisis y supuestos". Aplica también al estado `obteniendoUbicacion` y a cualquier otro estado mutado desde un callback asíncrono (GPS incluido).
- **`GeolocationService` como capa de aislamiento**: nunca se llama a `navigator.geolocation` directamente desde el componente — permite testear `MiFichajeComponent` con un servicio mockeado y centraliza la traducción de los códigos de error del navegador a mensajes i18n.
- **Sin `lat`/`lng` en los modelos expuestos al propio empleado** (`FichajeActual`, `Fichaje`): son datos de auditoría interna (ver `bbdd/fase1-1-bbdd.md`), el empleado no necesita verlos.
- **Un único componente, dos estados visuales** (no dos rutas ni dos componentes): el botón y el cronómetro son la misma vista con clases condicionales, igual de simple que el patrón Woffu real.
- **Reutiliza `ui-button`, `ui-card`, `ui-table`, `ui-badge`** ya construidos en Fase 0; no se necesita ningún componente `ui-*` nuevo para esta vista.

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
