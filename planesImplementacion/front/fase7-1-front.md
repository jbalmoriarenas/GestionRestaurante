# Plan de Implementación — Fase 7.1 (Frontend): Pantalla de Ayuda (icono en cabecera + guía de vistas con vídeo)

No depende de ningún backend nuevo — es un bloque íntegramente frontend, mismo criterio que la Fase 6.2 (modo oscuro): contenido estático (descripción + enlace a vídeo) por cada vista de la aplicación, sin necesidad de persistir nada en BBDD.

## Contexto y objetivo

Petición del usuario: un icono de "Ayuda" en la cabecera, junto al icono de modo oscuro/claro ya existente (`Topbar`, Fase 6.2), que lleve a una vista nueva (`/ayuda`) con un listado de todas las vistas de la aplicación. Por cada vista se muestra una breve descripción de su funcionamiento y un enlace a un vídeo de YouTube que lo explica — los vídeos **todavía no existen**, así que cada enlace nace como un valor placeholder fácil de localizar y sustituir vista a vista, según se vayan grabando, sin tocar el resto del código.

Es un desarrollo atómico: icono, ruta, componente y contenido forman una única entrega con sentido — una pantalla de ayuda sin el icono que lleva a ella, o el icono sin destino, no aportan valor por separado. No se propone dividir en varias entregas.

## Análisis y decisiones técnicas

- **Qué vistas se listan y con qué contenido**: se listan las 9 entradas hoja ya definidas en `layout/menu-items.ts` (`MENU_ITEMS`) — `fichajesMio`, `fichajesAdmin`, `vacacionesMias`, `vacacionesAdmin`, `empleados`, `facturas`, `nominas`, `valoraciones`, `configuracion` — porque son, letra a letra, las vistas reales navegables de la aplicación (mismo inventario que ya usa el propio menú lateral).
- **Filtrado por permisos del usuario, no lista fija para todos**: la pantalla de Ayuda muestra solo las vistas a las que el usuario logueado tiene acceso — igual que ya oculta el propio `Sidebar` (`Sidebar.tieneAcceso()`, `layout/sidebar/sidebar.ts:119-123`) los grupos/entradas para las que no tiene permiso. Mostrar ayuda de una vista que ni siquiera puede abrir sería confuso y, en el caso de "Fichajes/Vacaciones (Admin)" o "Facturas"/"Nóminas"/"Valoraciones" (sin acceso para `EMPLEADO`, ver `plan-implementacion-general.md`), filtraría exactamente lo mismo que ya filtra el menú. **Alternativa descartada**: listar siempre las 9 vistas para cualquier usuario, indicando cuáles no tiene disponibles — más completo como documentación, pero inconsistente con lo que el usuario realmente ve en el resto de la interfaz y no pedido explícitamente; se prefiere el mismo criterio ya establecido en `Sidebar`.
- **Reutilizar `vista`/`nivelMinimo` de `MENU_ITEMS`, no inventar un catálogo de permisos aparte**: la comprobación de acceso (`entry.nivelMinimo === 'EDICION' ? puedeEditar(vista) : tieneAcceso(vista)`) ya existe como método privado `Sidebar.tieneAcceso()` (`sidebar.ts:119-123`). Se extrae a una función pura exportada, `tieneAccesoItem(authService, entry)`, en `layout/menu-items.ts` (mismo fichero que ya define `CodigoVista`/`NivelAcceso` y los tipos `MenuItem`), y `Sidebar` pasa a usarla en vez de su copia privada — evita tener la misma lógica de permisos duplicada en dos sitios (`Sidebar` y `Ayuda`) según se vaya ampliando el proyecto.
- **`AYUDA_ITEMS` como array propio, no se amplía `MenuItem`/`MENU_ITEMS`**: `MenuItem` (`MenuLeaf`/`MenuGroup`) está diseñado para el renderizado del `Sidebar` (agrupación visual con `children`, icono de grupo, etc.), no para contenido de ayuda. Añadirle `descripcionKey`/`videoUrl` mezclaría dos responsabilidades distintas en la misma interfaz y obligaría a `Sidebar` a ignorar campos que no usa. Se define un array nuevo y plano, `AYUDA_ITEMS: AyudaItem[]`, con una fila por cada vista hoja (sin agrupar "mío"/"admin" en un padre, a diferencia del menú) — pequeña duplicación de `ruta`/`vista`/`nivelMinimo` (9 filas) frente a mezclar conceptos en un tipo ya usado en producción por el menú lateral.
- **Enlace, no vídeo embebido**: la petición es un "vídeo enlazado", no un reproductor incrustado — cada tarjeta muestra un enlace (`<a>`, `target="_blank" rel="noopener"`) que abre el vídeo de YouTube en una pestaña nueva. Más simple que un `<iframe>` embebido (sin necesidad de tocar la política de seguridad de contenido del proyecto para permitir `youtube.com` en `frame-src`) y evita mostrar un reproductor roto/en blanco mientras el vídeo real no existe.
- **Placeholder de vídeo explícito, uno por vista**: cada entrada de `AYUDA_ITEMS` nace con `videoUrl: ''` (cadena vacía, no una URL de ejemplo) y un comentario `// TODO vídeo: <vista>` al lado — así la plantilla puede ocultar el enlace de forma natural (`@if (item.videoUrl)`) en vez de llevar a cualquiera a una URL de YouTube que no existe o a un vídeo genérico que no es el suyo, hasta que cada vídeo real se grabe y el valor se sustituya uno a uno.
- **Sin `permissionGuard` en la ruta `/ayuda`**: es contenido puramente informativo, disponible para cualquier usuario autenticado (Admin o Empleado) — el filtrado relevante no es "quién puede ver la pantalla de Ayuda" sino "qué vistas aparecen dentro de ella" (ver primer punto). Sigue detrás de `authGuard` (dentro de `Shell`), igual que el resto de rutas.

## Estructura

```
layout/menu-items.ts                              (se amplía: exporta tieneAccesoItem(...))
layout/sidebar/sidebar.ts                         (se amplía: usa tieneAccesoItem en vez de su método privado)
layout/topbar/topbar.html / .scss                 (se amplía: botón de ayuda junto al de modo oscuro)
shared/ui/ui-icon/ui-icon.ts / .html               (se amplía: icono 'ayuda')
features/ayuda/ayuda-items.ts                     (nuevo: interfaz AyudaItem + AYUDA_ITEMS)
features/ayuda/ayuda/ayuda.ts / .html / .scss     (nuevo: vista /ayuda)
app.routes.ts                                     (se amplía: ruta 'ayuda' dentro de Shell)
public/i18n/{es,en}.json                          (se amplía: namespace 'ayuda', clave 'topbar.ayuda')
```

## Modelos

### `features/ayuda/ayuda-items.ts`
```typescript
import { CodigoVista, NivelAcceso } from '../../core/models/permiso.model';

export interface AyudaItem {
  ruta: string;
  vista: CodigoVista;
  nivelMinimo?: NivelAcceso;
  tituloKey: string;       // reutiliza las mismas claves i18n que layout/menu-items.ts
  descripcionKey: string;  // nueva, bajo 'ayuda.items.<clave>.descripcion'
  videoUrl: string;        // '' hasta que exista el vídeo real de esa vista
}

export const AYUDA_ITEMS: AyudaItem[] = [
  { ruta: '/fichajes', vista: 'FICHAJES', nivelMinimo: 'EDICION', tituloKey: 'menu.fichajesMio', descripcionKey: 'ayuda.items.fichajesMio.descripcion', videoUrl: '' }, // TODO vídeo: Mi fichaje
  { ruta: '/fichajes/admin', vista: 'EMPLEADOS', nivelMinimo: 'EDICION', tituloKey: 'fichajes.admin.titulo', descripcionKey: 'ayuda.items.fichajesAdmin.descripcion', videoUrl: '' }, // TODO vídeo: Fichajes (Admin)
  { ruta: '/vacaciones', vista: 'VACACIONES', nivelMinimo: 'EDICION', tituloKey: 'menu.vacacionesMias', descripcionKey: 'ayuda.items.vacacionesMias.descripcion', videoUrl: '' }, // TODO vídeo: Mis vacaciones
  { ruta: '/vacaciones/admin', vista: 'EMPLEADOS', nivelMinimo: 'EDICION', tituloKey: 'vacaciones.admin.titulo', descripcionKey: 'ayuda.items.vacacionesAdmin.descripcion', videoUrl: '' }, // TODO vídeo: Vacaciones (Admin)
  { ruta: '/empleados', vista: 'EMPLEADOS', tituloKey: 'empleados.title', descripcionKey: 'ayuda.items.empleados.descripcion', videoUrl: '' }, // TODO vídeo: Empleados
  { ruta: '/facturas', vista: 'FACTURAS', tituloKey: 'facturas.admin.titulo', descripcionKey: 'ayuda.items.facturas.descripcion', videoUrl: '' }, // TODO vídeo: Facturas
  { ruta: '/nominas', vista: 'NOMINAS', tituloKey: 'nominas.admin.titulo', descripcionKey: 'ayuda.items.nominas.descripcion', videoUrl: '' }, // TODO vídeo: Nóminas
  { ruta: '/valoraciones', vista: 'VALORACIONES', tituloKey: 'valoraciones.admin.titulo', descripcionKey: 'ayuda.items.valoraciones.descripcion', videoUrl: '' }, // TODO vídeo: Valoraciones
  { ruta: '/configuracion', vista: 'EMPLEADOS', nivelMinimo: 'EDICION', tituloKey: 'configuracion.admin.titulo', descripcionKey: 'ayuda.items.configuracion.descripcion', videoUrl: '' }, // TODO vídeo: Configuración
];
```

### `layout/menu-items.ts` (ampliación)
```typescript
export function tieneAccesoItem(
  authService: Pick<AuthService, 'tieneAcceso' | 'puedeEditar'>,
  entry: { vista: CodigoVista; nivelMinimo?: NivelAcceso },
): boolean {
  return entry.nivelMinimo === 'EDICION' ? authService.puedeEditar(entry.vista) : authService.tieneAcceso(entry.vista);
}
```
(`Sidebar.tieneAcceso()` se elimina y se sustituye por una llamada a esta función.)

## Desglose de subtareas

### Permisos compartidos (refactor previo, base de todo lo demás)
1. **[Frontend, baja]** `layout/menu-items.ts`: extraer y exportar `tieneAccesoItem(authService, entry)` (ver "Modelos") a partir de la lógica hoy privada en `Sidebar.tieneAcceso()`.
2. **[Frontend, baja]** `layout/sidebar/sidebar.ts`: sustituir el método privado `tieneAcceso()` por una llamada a `tieneAccesoItem(this.authService, entry)`; sin cambio de comportamiento (mismo test manual: el menú debe verse exactamente igual que antes tras el refactor).

### Icono de Ayuda
3. **[Frontend, baja]** `shared/ui/ui-icon/ui-icon.ts`: añadir `'ayuda'` al union type `IconName`.
4. **[Frontend, baja]** `shared/ui/ui-icon/ui-icon.html`: nuevo `@case ('ayuda')` con un icono de interrogación dentro de un círculo (mismo estilo de trazo `stroke="currentColor"` que el resto del catálogo, ej. círculo + trazo de "?" + punto).

### Vista `/ayuda`
5. **[Frontend, baja]** `features/ayuda/ayuda-items.ts` (nuevo): interfaz `AyudaItem` + `AYUDA_ITEMS` con las 9 entradas (ver "Modelos").
6. **[Frontend, media]** `features/ayuda/ayuda/ayuda.ts` (nuevo, standalone): `readonly items = computed(() => AYUDA_ITEMS.filter((item) => tieneAccesoItem(this.authService, item)))`, mismo patrón `inject(AuthService)` + `computed()` ya usado en el resto de features.
7. **[Frontend, media]** `features/ayuda/ayuda/ayuda.html` (nuevo): un `<ui-card>` por cada `items()`, con el título (`tituloKey | translate`), la descripción (`descripcionKey | translate`) y, solo si `videoUrl` no está vacío (`@if (item.videoUrl)`), un enlace `<a [href]="item.videoUrl" target="_blank" rel="noopener">{{ 'ayuda.verVideo' | translate }}</a>`; si `videoUrl` está vacío, se muestra en su lugar un texto discreto (`ayuda.videoProximamente`, ej. "Vídeo próximamente") en vez de un enlace roto.
8. **[Frontend, baja]** `app.routes.ts`: nueva ruta hija de `Shell`, `{ path: 'ayuda', data: { title: 'ayuda.titulo' }, loadComponent: () => import('./features/ayuda/ayuda/ayuda').then((m) => m.Ayuda) }` — sin `canActivate: [permissionGuard(...)]` (ver "Análisis y decisiones técnicas").

### Icono en la cabecera
9. **[Frontend, media]** `layout/topbar/topbar.html`: nuevo `<a routerLink="/ayuda" class="help-toggle" [attr.aria-label]="'topbar.ayuda' | translate"><ui-icon name="ayuda" /></a>` dentro de `.topbar-right`, inmediatamente antes del botón de modo oscuro/claro ya existente (`.theme-toggle`) — mismo bloque, mismo tamaño de icono.
10. **[Frontend, baja]** `layout/topbar/topbar.ts`: añadir `RouterLink` a `imports` (no hace falta ningún método nuevo, el enlace usa `routerLink` directamente en la plantilla).
11. **[Frontend, baja]** `layout/topbar/topbar.scss`: estilo del nuevo `.help-toggle` igual que `.theme-toggle` (mismo tamaño de icono, mismo estado `:hover`/`:focus-visible`).

### Traducciones
12. **[Frontend, baja]** `public/i18n/{es,en}.json`: clave `topbar.ayuda` ("Ayuda" / "Help"); namespace nuevo `ayuda.{titulo, verVideo, videoProximamente, items.<clave>.descripcion}` con una descripción breve por cada una de las 9 vistas.

## Decisiones técnicas

- **Filtrar por permiso, no listar siempre las 9 vistas**: ver "Análisis y decisiones técnicas" — coherente con lo que el usuario ya ve en el `Sidebar`, a costa de que un Empleado vea una pantalla de Ayuda más corta que la de un Admin (aceptado, es lo esperado).
- **`AYUDA_ITEMS` propio en vez de ampliar `MenuItem`**: algo más de duplicación de `ruta`/`vista`/`nivelMinimo` (9 filas) a cambio de no mezclar contenido de ayuda con el modelo que ya usa `Sidebar` en producción.
- **Enlace externo, no `<iframe>` embebido**: más simple y evita cualquier cambio en la política de seguridad de contenido del proyecto; encaja además con que hoy no existe ningún vídeo real que embeber.
- **Placeholder `''` explícito por vídeo, no una URL de ejemplo**: hace evidente en el propio código qué vídeos faltan (`grep videoUrl: ''`) y evita enlaces rotos o, peor, todos apuntando por error al mismo vídeo de ejemplo si alguien olvida sustituirlo.

## Riesgos y dependencias

- **Riesgo bajo** — el refactor de `Sidebar.tieneAcceso()` a `tieneAccesoItem()` (subtareas 1-2) toca un componente ya en producción; revisar `sidebar.spec.ts` (si existe) y probar manualmente que el menú se sigue viendo igual para Admin y Empleado tras el cambio, antes de construir la Ayuda sobre esa base.
- **Riesgo bajo** — contenido sin mantenimiento centralizado: si en el futuro se añade una vista nueva a `MENU_ITEMS`, hay que recordar añadir también su entrada correspondiente en `AYUDA_ITEMS` (no hay una comprobación automática que las mantenga sincronizadas, al ser dos arrays independientes).
- **Dependencia de negocio, no técnica**: los 9 vídeos de YouTube no existen todavía — la funcionalidad se entrega completa y funcional con el mensaje "Vídeo próximamente" en cada tarjeta, y cada `videoUrl` se va rellenando de forma independiente, sin desplegar código nuevo, según se vayan grabando y publicando.
- **Sin dependencia con el backend** — bloque íntegramente frontend.

## Criterios de aceptación técnicos

- La cabecera muestra un icono de Ayuda junto al de modo oscuro/claro, en cualquier vista dentro de `Shell`.
- Pulsarlo navega a `/ayuda`, accesible tanto para `ADMIN` como para `EMPLEADO`.
- La pantalla de Ayuda solo lista las vistas a las que el usuario logueado tiene acceso (un `EMPLEADO` no ve, por ejemplo, "Facturas" ni "Nóminas (Admin)"; un `ADMIN` ve las 9).
- Cada vista listada muestra su título, una breve descripción de su funcionamiento y, si tiene `videoUrl` configurada, un enlace que abre el vídeo de YouTube en una pestaña nueva; si no la tiene, muestra "Vídeo próximamente" en su lugar, sin ningún enlace roto.
- El menú lateral (`Sidebar`) se comporta exactamente igual que antes del refactor de `tieneAcceso()`/`tieneAccesoItem()`.
