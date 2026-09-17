# Plan de Implementación — Fase 0 (Frontend)

Consume el contrato definido en [`../back/fase0-back.md`](../back/fase0-back.md), que a su vez respeta el esquema de [`../bbdd/fase0-bbdd.md`](../bbdd/fase0-bbdd.md).

## Contexto y objetivo

Construir en `GestionRestauranteFront` el login, el layout base de la aplicación (según rol) y la pantalla de gestión de empleados, sentando además el **sistema de diseño** y el catálogo de **componentes reutilizables** (`ui-*`) que usarán todos los módulos de las fases siguientes (Fichajes, Facturas, Nóminas).

## Stack

Angular 22 (standalone, zoneless), TypeScript 6, RxJS, SCSS (ya montado). No se añade ninguna librería de componentes de terceros (Material, PrimeNG...) — se construye un **design system propio y ligero** en SCSS con variables CSS, para tener control total sobre el aspecto visual (ver sección de diseño) sin arrastrar el "look" por defecto de un kit de terceros.

## Diseño visual — inspiración Woffu, look profesional y claro

**Principios**: fondo claro, mucho aire (whitespace), tarjetas con esquinas redondeadas y sombra suave, un único color de acento fuerte (turquesa/teal, como Woffu) usado con moderación para acciones primarias y estados activos, tipografía sans-serif limpia, iconografía simple (outline).

### Paleta de colores (variables CSS en `src/styles.scss`)

```scss
:root {
  /* Superficie */
  --color-bg:              #F5F7F8;   /* fondo general de la app */
  --color-surface:         #FFFFFF;   /* tarjetas, tablas, modales */
  --color-surface-hover:   #F0F3F4;

  /* Texto */
  --color-text-primary:    #1F2933;
  --color-text-secondary:  #5B6B73;
  --color-text-disabled:   #9AA5AB;

  /* Marca / acento (inspirado en el turquesa de Woffu) */
  --color-primary:         #0FB5AE;
  --color-primary-hover:   #0D9C96;
  --color-primary-light:   #E3F7F6;   /* fondos sutiles, hover de menú, badges */

  /* Estados semánticos */
  --color-success:         #22C55E;
  --color-success-light:   #E7F9EE;
  --color-warning:         #F59E0B;
  --color-warning-light:   #FEF3E2;
  --color-danger:          #EF4444;
  --color-danger-light:    #FDECEC;

  /* Bordes y sombras */
  --color-border:          #E4E8EA;
  --shadow-card:           0 1px 3px rgba(16, 24, 32, 0.06), 0 1px 2px rgba(16, 24, 32, 0.04);
  --shadow-modal:          0 12px 32px rgba(16, 24, 32, 0.16);

  /* Radios y espaciado */
  --radius-sm: 6px;
  --radius-md: 10px;
  --radius-lg: 16px;
}
```

- **Tipografía**: `Inter` (Google Fonts, ya permitida como fuente web) como fuente base, pesos 400/500/600/700. Fallback: `-apple-system, Segoe UI, Roboto, sans-serif`.
- **Layout general**: sidebar fijo a la izquierda (240px, fondo `--color-surface`, item activo con fondo `--color-primary-light` y texto `--color-primary`) + topbar superior (nombre/avatar del usuario + botón logout) + área de contenido sobre `--color-bg` con tarjetas blancas.
- **Botones primarios**: fondo `--color-primary`, texto blanco, `border-radius: var(--radius-sm)`, hover `--color-primary-hover`. Botones secundarios: fondo transparente, borde `--color-border`, texto `--color-text-primary`.
- **Tablas**: cabecera con texto `--color-text-secondary` en mayúsculas pequeñas, filas separadas por `--color-border` (sin zebra striping agresivo, al estilo Woffu), hover sutil `--color-surface-hover`.
- **Badges de estado** (p.ej. `activo`/`inactivo`, futuros estados de factura/nómina): pastilla redondeada con fondo `-light` y texto del color semántico correspondiente (`--color-success` / `--color-danger` / `--color-warning`).

## Estructura de carpetas

```
src/app/
├── core/
│   ├── models/          empleado.model.ts, auth.model.ts, page.model.ts, api-error.model.ts, permiso.model.ts
│   ├── services/        auth.service.ts, empleado.service.ts, notification.service.ts
│   ├── interceptors/    jwt.interceptor.ts, error.interceptor.ts   (functional interceptors)
│   └── guards/          auth.guard.ts, permission.guard.ts         (functional guards)
├── shared/
│   └── ui/              ui-button, ui-input, ui-select, ui-card, ui-table, ui-badge, ui-modal, ui-spinner
├── layout/
│   └── shell/           shell.component.ts (sidebar + topbar + <router-outlet>), sidebar.component.ts, topbar.component.ts
└── features/
    ├── auth/
    │   └── login/       login.component.ts
    └── empleados/
        ├── empleados-list/   empleados-list.component.ts
        └── empleado-form/    empleado-form.component.ts (crea y edita, según :id de la ruta)
```

## Modelos (`core/models`) — deben ser un espejo exacto de los DTOs del backend

El front ya no maneja un `rol` fijo (`'ADMIN' | 'EMPLEADO'`) en el modelo de autorización: consume el **mapa de permisos por vista** que devuelve el login (ver `back/fase0-back.md`), lo que permite añadir tipos de usuario nuevos en el futuro sin tocar el código del front.

```typescript
// permiso.model.ts
export type NivelAcceso = 'EDICION' | 'VISUALIZACION';
export type CodigoVista = 'EMPLEADOS' | 'FICHAJES' | 'FACTURAS' | 'NOMINAS';

export interface Permiso {
  vista: CodigoVista;
  nivelAcceso: NivelAcceso;
}

// empleado.model.ts
export interface TipoUsuario { codigo: string; nombre: string; }

export interface Empleado {
  id: number;
  nombre: string;
  apellidos: string;
  email: string;
  dniNie: string;
  telefono: string | null;
  puesto: string | null;
  fechaAlta: string;   // ISO yyyy-MM-dd
  fechaBaja: string | null;
  activo: boolean;
  tipoUsuario: TipoUsuario;
}
export type EmpleadoRequest = Omit<Empleado, 'id' | 'fechaBaja' | 'tipoUsuario'> & { tipoUsuarioCodigo: string };

// auth.model.ts
export interface LoginRequest { email: string; password: string; }
export interface LoginResponse {
  token: string;
  empleadoId: number;
  nombre: string;
  apellidos: string;
  tipoUsuario: TipoUsuario;
  permisos: Permiso[];
}

// page.model.ts — espejo de Page<T> de Spring Data
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;   // página actual (0-index)
  size: number;
}

// api-error.model.ts — espejo del ApiError del GlobalExceptionHandler
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
```

## Desglose de subtareas

### Núcleo (auth, guards, interceptors)
1. **[Frontend, media]** `AuthService`: `login()`, `logout()`, signal `currentUser` (`LoginResponse | null` persistido en `localStorage` para sobrevivir a un refresh), `computed permisos` (`Permiso[]`), y helpers `tieneAcceso(vista: CodigoVista): boolean` / `puedeEditar(vista: CodigoVista): boolean` derivados del mapa de permisos — **nunca** un `isAdmin` hardcodeado.
2. **[Frontend, baja]** `jwtInterceptor` (functional, `HttpInterceptorFn`): añade `Authorization: Bearer <token>` si hay sesión.
3. **[Frontend, baja]** `errorInterceptor`: captura errores HTTP, parsea el `ApiError` del backend y los reenvía a `NotificationService` (toast), sin que cada componente tenga que repetir ese manejo.
4. **[Frontend, baja]** `authGuard` (redirige a `/login` si no hay sesión) y `permissionGuard(vista: CodigoVista, nivelMinimo: NivelAcceso = 'VISUALIZACION')` (comprueba `AuthService.tieneAcceso`/`puedeEditar` según el nivel requerido; sin ese permiso, redirige) — se aplican en `app.routes.ts` por ruta.
5. **[Frontend, baja]** `NotificationService`: wrapper simple de toasts de éxito/error, usado por todos los módulos futuros.

### Componentes reutilizables (`shared/ui`) — catálogo base para toda la app
6. **[Frontend, media]** `ui-button`: variantes `primary | secondary | danger`, tamaños, estado `loading` (spinner interno + disabled).
7. **[Frontend, baja]** `ui-input` / `ui-select`: envuelven `<input>`/`<select>` con label, mensaje de error y binding vía `ControlValueAccessor` para usarlos directamente con Reactive Forms.
8. **[Frontend, baja]** `ui-card`: contenedor con el estilo de tarjeta (fondo blanco, `--shadow-card`, `--radius-md`).
9. **[Frontend, alta]** `ui-table<T>`: tabla genérica reutilizable — recibe columnas (config: label, accessor, template opcional), datos paginados (`Page<T>`), emite eventos de cambio de página/orden; incluye estados vacíos y de carga. **Este componente es el que usarán también Fichajes, Facturas y Nóminas**, por eso se construye genérico desde ya en vez de una tabla ad-hoc solo para empleados.
10. **[Frontend, baja]** `ui-badge`: pastilla de estado, recibe texto + variante semántica (`success|warning|danger|neutral`).
11. **[Frontend, media]** `ui-modal`: modal genérico (confirmación / formulario embebido), usado para confirmar la baja de un empleado y reutilizable en fases futuras.
12. **[Frontend, baja]** `ui-spinner`: indicador de carga consistente en toda la app.

### Layout
13. **[Frontend, media]** `ShellComponent` + `SidebarComponent` + `TopbarComponent`: sidebar con ítems de menú **generados a partir de `AuthService.permisos`** (se itera la lista de permisos del usuario y se pinta un ítem de menú por cada `vista` presente, con su ruta y label asociados en un pequeño mapa `CodigoVista → {ruta, label, icono}` — así un tipo de usuario nuevo con acceso a una vista ya existente aparece en el menú sin tocar el componente). Topbar con nombre del usuario y tipo de usuario (`tipoUsuario.nombre`) y botón de logout.

### Pantallas de Fase 0
14. **[Frontend, media]** `LoginComponent`: formulario reactivo (email + password), usa `ui-input` y `ui-button`, muestra error si login falla (401 → mensaje genérico "Credenciales incorrectas").
15. **[Frontend, media]** `EmpleadosListComponent` (ruta protegida por `permissionGuard('EMPLEADOS', 'VISUALIZACION')`): usa `ui-table` con columnas nombre, email, puesto, tipo de usuario (`ui-badge`), activo (`ui-badge`); columna de acciones (editar / dar de baja vía `ui-modal` de confirmación) visible solo si `AuthService.puedeEditar('EMPLEADOS')` — misma vista, degradada a solo lectura para quien tenga `VISUALIZACION`; paginación conectada a `GET /api/empleados?page=&size=`.
16. **[Frontend, media]** `EmpleadoFormComponent`: formulario reactivo tipado para alta/edición (misma pantalla para ambos casos, según si la ruta trae `:id`), con un `<select>` de tipo de usuario poblado desde `GET /api/tipos-usuarios` (endpoint sencillo de solo lectura, backend) en vez de un enum fijo de 2 opciones — validaciones espejo de las del backend (email válido, DNI/NIE con formato, campos obligatorios).

## Decisiones técnicas

- **Sin librería de componentes de terceros**: para lograr el look "Woffu-like" con control total del diseño y sin sobrecargar el bundle con un kit de componentes genérico que luego hay que forzar visualmente. El coste es construir el catálogo `ui-*` propio, pero se amortiza en las 3 fases siguientes que lo reutilizan.
- **Signals + servicios en vez de NgRx**: el estado de esta app (sesión actual, listados paginados) es lo bastante simple como para no justificar un store global; cada `*.service.ts` expone signals/observables y los componentes standalone se suscriben con `input()`/`computed()`.
- **Formularios reactivos tipados** (Angular typed forms) en vez de template-driven, para detectar en compilación un desajuste entre el formulario y el modelo `Empleado`.
- **`ui-table` genérica desde Fase 0**: es la decisión de reutilización más importante de esta fase — evita reimplementar tabla+paginación+estados vacíos/carga tres veces más en Fichajes/Facturas/Nóminas.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase0-back.md`: sin `POST /api/auth/login` y `GET/POST/PUT /api/empleados` funcionando, esta fase no se puede validar end-to-end.
- **Riesgo bajo**: si el backend cambia la forma de `ApiError` o de `Page<T>`, hay que actualizar los modelos espejo (`core/models`) — mantenerlos como el único punto de esos tipos evita que el desajuste se disperse por varios componentes.

## Criterios de aceptación técnicos

- Un login correcto redirige al shell correspondiente al rol (Admin ve menú completo, Empleado ve solo "Mi Fichaje").
- Un login incorrecto muestra un mensaje de error sin recargar la página ni perder lo escrito en el email.
- Un usuario con rol `EMPLEADO` que navega manualmente a `/empleados` es redirigido/bloqueado por `roleGuard`, sin que la petición HTTP llegue siquiera a dispararse innecesariamente.
- La tabla de empleados pagina correctamente contra `GET /api/empleados` y refleja el estado `activo`/`inactivo` con el badge correcto.
- Todos los componentes de `shared/ui` no importan nada de `features/`, para garantizar que son reutilizables sin acoplamiento.
