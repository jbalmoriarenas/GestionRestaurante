# Plan de Implementación — Fase 4.1 (Frontend): Valoraciones — panel de conexión con Google

Consume el contrato de [`../back/fase4-1-back.md`](../back/fase4-1-back.md). Reutiliza `ui-card`/`ui-button`/`ui-badge`/`ui-modal`/`ui-spinner` de [`fase0-front.md`](./fase0-front.md). La vista de listado/resumen de reseñas es [`fase4-2-front.md`](./fase4-2-front.md), que extiende este mismo componente.

## Contexto y objetivo

Vista Admin nueva `/valoraciones` (ruta, entrada de menú e icono nuevos — a diferencia de Facturas/Nóminas, no existían desde Fase 0). Mientras no haya conexión con Google, la vista muestra únicamente una tarjeta explicativa con un botón "Conectar con Google"; una vez conectada, muestra el estado de la conexión (ubicación, última sincronización, posible error) y las acciones "Sincronizar ahora" y "Desconectar". El listado de reseñas en sí (`fase4-2-front.md`) solo aparece cuando hay conexión activa.

## Estructura (se añade a `features/`)

```
core/models/valoracion.model.ts
core/services/valoracion.service.ts
features/valoraciones/valoraciones-admin/valoraciones-admin.ts / .html / .scss
```

Ruta `/valoraciones` protegida con `permissionGuard('VALORACIONES', 'EDICION')` (alineado con `back/fase4-1-back.md`). Nueva entrada en `layout/menu-items.ts`: `{ vista: 'VALORACIONES', ruta: '/valoraciones', labelKey: 'valoraciones', icono: 'valoraciones' }`, junto a las de Facturas/Nóminas. Nuevo icono `'valoraciones'` en el catálogo `ui-icon` (`shared/ui/ui-icon/ui-icon.ts`, ampliando el `type IconName`) y nueva entrada `'VALORACIONES'` en `CodigoVista` (`core/models/permiso.model.ts`).

## Modelos

### `core/models/valoracion.model.ts`
```typescript
export interface ConexionEstado {
  conectado: boolean;
  nombreUbicacion: string | null;
  fechaConexion: string | null;
  fechaUltimaSincronizacion: string | null;
  ultimoError: string | null;
}

export interface ResultadoSincronizacion {
  nuevas: number;
  actualizadas: number;
  total: number;
}
```

## Desglose de subtareas

### Servicio
1. **[Frontend, baja]** `ValoracionService`: `obtenerEstadoConexion(): Observable<ConexionEstado>`; `obtenerUrlConexion(): Observable<{urlConsentimiento: string}>`; `desconectar(): Observable<void>`; `sincronizar(): Observable<ResultadoSincronizacion>`.

### Componente
2. **[Frontend, alta]** `ValoracionesAdminComponent`:
   - Al iniciar, llama a `obtenerEstadoConexion()` para decidir qué renderizar; también lee los query params `conectado`/`error` de la URL (`ActivatedRoute`, `snapshot.queryParamMap`) — los deja Google al redirigir tras el callback del backend (`back/fase4-1-back.md`, `GET /conexion/callback`) — y, si están presentes, muestra una notificación de éxito/error y **limpia los query params de la URL** (`Router.navigate` con los mismos `queryParams` a `{}`) para que un refresco de página no repita la notificación.
   - **Sin conexión** (`conectado = false`): `ui-card` con texto explicativo y botón "Conectar con Google" que llama a `obtenerUrlConexion()` y, en éxito, hace `window.location.href = urlConsentimiento` — navegación de página completa a Google, **no** una llamada `HttpClient` seguida de renderizado SPA (ver "Decisiones técnicas").
   - **Con conexión** (`conectado = true`): `ui-card` de estado con `nombreUbicacion`, `fechaConexion`, `fechaUltimaSincronizacion` (`'—'` si `null`, no debería serlo con conexión activa salvo justo tras conectar y antes de que termine la primera sincronización), y un `ui-badge` de aviso (`variant="danger"`) con `ultimoError` cuando no es `null`. Botón "Sincronizar ahora" (`ui-spinner` mientras la petición está en curso, deshabilitado durante la llamada para evitar doble clic) que llama a `sincronizar()` y, en éxito, refresca el estado de conexión y notifica el resultado (`nuevas`/`actualizadas`). Botón "Desconectar" que abre un `ui-modal` de confirmación (mismo patrón que "Dar de baja" en `empleados-list.html`) y, al confirmar, llama a `desconectar()` y vuelve al estado "Sin conexión".
3. **[Frontend, baja]** Nuevas claves i18n bajo `valoraciones.admin`: `titulo`, `conexion.{explicacion,conectar,conectado,ubicacion,fechaConexion,ultimaSincronizacion,sinSincronizar,error,sincronizarAhora,desconectar}`, `conexion.modal.{titulo,mensaje,confirmar}`, `notificaciones.{conectadoExito,conexionError,sincronizando,sincronizacionExito,desconectadoExito}`.
4. **[Frontend, baja]** Nueva entrada en `layout/menu-items.ts` e icono `'valoraciones'` (`shared/ui/ui-icon/`, mismo patrón SVG *outline* que el resto del catálogo).
5. **[Frontend, baja]** Actualizar `app.routes.ts` para añadir `/valoraciones` → `ValoracionesAdminComponent`.

## Decisiones técnicas

- **Redirección de página completa (`window.location.href`) para iniciar el flujo OAuth, no una llamada AJAX**: el consentimiento de Google **tiene** que ocurrir en una navegación real del navegador (Google no permite iniciarlo dentro de un `iframe` ni devolver el resultado a una petición `fetch`/`HttpClient`) — es la única acción de todo el front que sale deliberadamente del ciclo de vida SPA habitual.
- **El callback lo gestiona el backend, no una ruta Angular**: `back/fase4-1-back.md` ya redirige el navegador de vuelta a `/valoraciones?conectado=true` (o `?error=`) tras completar el intercambio de tokens — el front solo necesita leer esos query params al cargar, no implementar ninguna lógica de `code`/`state` (eso vive enteramente en el backend, que es quien tiene el `client_secret`).
- **Limpiar los query params tras leerlos**: evita que un simple F5 sobre `/valoraciones?conectado=true` vuelva a mostrar la notificación de éxito como si se acabara de conectar de nuevo.
- **Un único componente para conexión (`4.1`) y listado (`4.2`)**, no dos vistas separadas: el estado de conexión condiciona qué se ve en la misma pantalla (tarjeta de conectar vs. panel completo) — separar en dos rutas obligaría a redirigir entre ellas según el estado, sin ninguna ventaja real.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase4-1-back.md`: sin sus endpoints, esta vista no puede probarse end-to-end (y sin que el proyecto de Google Cloud tenga acceso aprobado a la API de reseñas, tampoco se puede completar el flujo real contra la cuenta de producción, ver "Riesgos" de ese documento).
- **Riesgo bajo**: si el Admin cierra la pestaña de Google a mitad del consentimiento, la app queda esperando indefinidamente en el estado "Sin conexión" sin ningún mensaje de "cancelado" — aceptable (el Admin simplemente vuelve a pulsar "Conectar con Google"); no se implementa detección de cancelación en esta fase.

## Criterios de aceptación técnicos

- Sin conexión previa, `/valoraciones` muestra solo la tarjeta "Conectar con Google", sin ningún listado ni resumen.
- Pulsar "Conectar con Google" navega el navegador (no una llamada AJAX) a la URL de consentimiento de Google.
- Tras completar el consentimiento y volver a `/valoraciones?conectado=true`, la vista muestra el panel de conexión activa con el nombre de la ubicación y notifica el éxito una sola vez (un refresco posterior de la página no repite la notificación).
- Con conexión activa y un `ultimoError` informado, el panel muestra ese error de forma visible.
- Pulsar "Sincronizar ahora" muestra el spinner, deshabilita el botón mientras dura la petición, y al terminar refresca `fechaUltimaSincronizacion`.
- Pulsar "Desconectar" pide confirmación antes de ejecutar la acción; al confirmar, la vista vuelve a mostrar solo la tarjeta "Conectar con Google".
- Un usuario con rol `EMPLEADO` no ve el enlace a `/valoraciones` en el menú y, si navega ahí directamente, es bloqueado por el guard.
