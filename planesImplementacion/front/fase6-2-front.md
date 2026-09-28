# Plan de Implementación — Fase 6.2 (Frontend): Modo oscuro, cabecera con título centrado, footer corporativo y marca Mavero

**Documento retroactivo**: a diferencia del resto de planes de este directorio (escritos antes de implementar), este bloque se implementó a petición directa del usuario en el chat, en varias peticiones sueltas dentro del mismo periodo de trabajo que la Fase 6.1, sin un plan previo. Se documenta aquí después de implementado, con el mismo formato que el resto de fases, para que quede localizable y mantenible igual que cualquier otra entrega. No depende de ningún backend nuevo — es un bloque íntegramente frontend.

## Contexto y objetivo

Cuatro peticiones de interfaz, relacionadas entre sí porque todas tocan el layout común de la aplicación (`Shell`/`Topbar`/`Footer`) y los tokens de diseño (`_tokens.scss`):

1. Sustituir el icono de Angular por defecto de la pestaña del navegador por el logo de la empresa gestora del software, y el título "Gestion Restaurante" por su nombre ("Mavero").
2. Un footer común a todas las vistas, con logo + nombre de la empresa, eslogan, y aviso de derechos reservados.
3. Un icono de modo oscuro/claro en la cabecera, junto al bloque de usuario ("Admin Sistema / Administrador").
4. Los títulos de cada vista ("Nóminas (Admin)", "Empleados", etc., que hasta entonces cada vista mostraba como un `<h1>` dentro de su propio contenido) trasladados a la cabecera, centrados.

**Importante — no confundir "Mavero" con el nombre del restaurante gestionado**: `configuracion_empresa.nombre_empresa` (`back/fase5-1-back.md`, hoy sembrado como "Sidrería La Zapica") es el negocio que usa la aplicación, y sigue mostrándose en el login y en la cabecera del `Sidebar` tal cual ya lo hacía. "Mavero" es el nombre de la empresa que **desarrolla y gestiona el software** — aparece solo en el favicon, el título de la pestaña del navegador, y el footer nuevo. Son dos marcas distintas conviviendo en la misma interfaz a propósito.

## Análisis y decisiones técnicas

- **Todo el proyecto ya consumía color exclusivamente a través de variables CSS** (`var(--color-*)`, definidas en `src/styles/_tokens.scss`) — verificado con un grep de colores hexadecimales hardcodeados en `.scss`/`.html` antes de empezar (cero resultados fuera del propio `_tokens.scss`). Esto permitió implementar el modo oscuro como un único bloque `:root[data-theme="dark"]` que redefine esos tokens, sin tocar ningún componente — la superficie de cambio real fue mínima para el alcance visual conseguido.
- **`data-theme` en `<html>`, no una clase en `<body>` ni un servicio de temas por componente**: es el mecanismo más simple que funciona con CSS puro (`:root[data-theme="dark"] { ... }`), sin necesitar que cada componente sepa nada del tema activo.
- **Persistencia en `localStorage` con fallback a `prefers-color-scheme`**: si el usuario nunca ha tocado el icono, se respeta la preferencia del sistema operativo/navegador; en cuanto lo toca una vez, esa elección manda siempre, sin volver a mirar la preferencia del sistema (mismo criterio que la mayoría de apps con modo oscuro).
- **`ThemeService` inyectado también en `App` (componente raíz), no solo en `Topbar`**: los servicios `providedIn: 'root'` de Angular son *lazy* — si nada los inyecta, no se instancian. Si solo `Topbar` lo inyectara, el tema no se aplicaría en `/login` ni en `/restablecer-contrasena` (fuera del `Shell`) hasta la primera vez que el usuario entrara a una vista autenticada. Se inyecta en `App` (junto a `Title`/`TranslateService`, mismo patrón ya usado ahí) solo para que su efecto arranque desde el primer render, en cualquier ruta.
- **Título de vista vía `data.title` en cada ruta (Angular Router), no un servicio de "breadcrumbs" nuevo**: se reutiliza el mecanismo estándar de Angular Router para metadatos de ruta; `Topbar` recorre el árbol de `ActivatedRoute` hasta el nodo más profundo (`while (ruta.firstChild)`) y lee `data['title']`, reaccionando a `NavigationEnd` — mismo patrón ya usado por `Sidebar.currentUrl` para saber qué elemento del menú resaltar (`toSignal` sobre `router.events`), no una abstracción nueva.
- **Las claves i18n de `data.title` reutilizan, no duplican, las que ya existían** como `<h1>` de cada vista (`nominas.admin.titulo`, `fichajes.admin.titulo`, etc.) — el título no cambia de texto, solo de ubicación en la pantalla. Para `mi-fichaje`/`mis-vacaciones` (que no tenían `<h1>` propio) se reutilizan las claves ya existentes del menú (`menu.fichajesMio`, `menu.vacacionesMias`).
- **CSS Grid (`grid-template-columns: auto 1fr auto`) para centrar de verdad, no un `flex` con `justify-content: space-between`**: tanto en `Topbar` (icono de menú / título / bloque de usuario) como en `Footer` (logo+nombre / eslogan / derechos), un centrado con flexbox y espaciadores (`.spacer { flex: 1 }`) solo centra el elemento del medio si los dos lados pesan exactamente igual — con contenido de ancho variable (nombre de usuario más largo o más corto, con o sin sesión iniciada) el centrado se desplaza. Grid con tres columnas explícitas centra el elemento central de verdad, con independencia del ancho de los otros dos.
- **El `<h1>` de cada vista se elimina, no se duplica**: mantenerlo en el contenido además de en la cabecera habría mostrado el mismo texto dos veces en la misma pantalla. Se quitó de `fichajes-admin`, `vacaciones-admin`, `nominas-admin`, `facturas-admin`, `valoraciones-admin`, `configuracion-admin` (aquí solo el `<h1>` de nivel de página; el `<h2>` de la tarjeta "Datos de empresa" se mantiene, es un título de sección distinto) y `empleados-list` (aquí, además, el botón "+ Nuevo empleado" pasó de `justify-content: space-between` a `flex-end` en su contenedor, al quedarse solo).

## Estructura

```
core/services/theme.service.ts                          (nuevo)
layout/footer/footer.ts / .html / .scss                  (nuevo)
layout/topbar/topbar.ts / .html / .scss                  (se amplía, ya existe)
layout/shell/shell.ts / .html                             (se amplía: añade <app-footer>)
shared/ui/ui-icon/ui-icon.ts / .html                      (se amplía: iconos 'sun'/'moon')
features/auth/login/login.ts / .html / .scss              (se amplía: envuelve en -shell, añade <app-footer>)
features/auth/restablecer-password/*.ts / .html / .scss   (se amplía: igual que login)
app.routes.ts                                             (se amplía: data.title en cada ruta)
app.ts                                                    (se amplía: inyecta ThemeService)
src/index.html                                            (favicon + <title>)
public/logoEmpresa.png                                    (nuevo, logo de Mavero)
src/styles/_tokens.scss                                   (se amplía: bloque :root[data-theme="dark"])
```

## Desglose de subtareas

### Marca Mavero (favicon + título de pestaña)
1. **[Frontend, baja]** Copiar el logo de la empresa (`img/logoEmpresa.png` del repo, fuera del proyecto Angular) a `GestionRestauranteFront/public/logoEmpresa.png` — todo lo que vive en `public/` se copia tal cual a la raíz del build de Angular, así que la ruta (`logoEmpresa.png`, relativa) funciona igual en local que en cualquier servidor de despliegue, sin depender de la ruta absoluta local donde se generó la imagen.
2. **[Frontend, baja]** `src/index.html`: `<link rel="icon" type="image/png" href="logoEmpresa.png">` (antes, `favicon.ico`) y `<title>Mavero</title>` estático (evita un parpadeo con el título anterior antes de que arranque Angular).
3. **[Frontend, baja]** `public/i18n/{es,en}.json`: `app.brand` pasa de `"La Zapica"` a `"Mavero"` — la clave ya existía (`App` la usa vía `Title` de Angular para fijar `document.title` dinámicamente, reaccionando al idioma) y se reutiliza tal cual, sin crear una clave nueva.

### Footer corporativo
4. **[Frontend, media]** Componente nuevo `Footer` (`layout/footer/`): logo (`logoEmpresa.png`) + nombre (`app.brand`) a la izquierda, eslogan centrado (`footer.eslogan`, nueva clave), año calculado en el propio componente (`new Date().getFullYear()`) + derechos reservados (`footer.derechosReservados`) a la derecha. Layout de tres columnas con CSS Grid (ver "Análisis y decisiones técnicas"); en móvil (`≤768px`) las tres partes se apilan centradas en una columna.
5. **[Frontend, baja]** `Shell` (`layout/shell/`): añade `<app-footer />` dentro de `.shell-main`, debajo de `.shell-content` — cubre todas las vistas autenticadas.
6. **[Frontend, media]** `Login` y `RestablecerPassword`: envuelven su contenido existente en un contenedor `-shell` (`flex-direction: column`, `height: 100%`) con el contenido de la página en `flex: 1` y `<app-footer />` como último hijo — necesario porque ambas vistas ya usaban `height: 100%` directamente en su elemento raíz (cascada desde `html`/`body`/`app-root`), y añadir el footer como hermano sin este envoltorio habría desbordado esa altura fija.

### Modo oscuro
7. **[Frontend, media]** `ThemeService` (`core/services/`, `providedIn: 'root'`): signal `theme: 'light' | 'dark'`, inicializado desde `localStorage` (clave `gestion-restaurante.theme`) con fallback a `matchMedia('(prefers-color-scheme: dark)')`; un único `effect()` en el constructor aplica `document.documentElement.setAttribute('data-theme', tema)` y persiste en `localStorage` en cada cambio (incluida la primera ejecución). Método `toggle()`.
8. **[Frontend, baja]** `App` (componente raíz): inyecta `ThemeService` (sin usarlo directamente más que para forzar su instanciación) junto a `Title`/`TranslateService`, para que su efecto arranque desde el primer render en cualquier ruta, no solo dentro del `Shell` — ver "Análisis y decisiones técnicas".
9. **[Frontend, baja]** `ui-icon`: dos iconos nuevos, `sun` y `moon` (mismo estilo de línea que el resto del catálogo).
10. **[Frontend, media]** `Topbar`: botón nuevo (icono `sun` en modo oscuro / `moon` en modo claro — el icono siempre indica a qué modo se va a cambiar, no el modo actual) entre el icono de menú y el bloque de usuario, `aria-label` traducido (`topbar.modoOscuro`/`topbar.modoClaro`).
11. **[Frontend, alta]** `src/styles/_tokens.scss`: bloque `:root[data-theme="dark"]` que redefine superficie, texto, bordes, sombras y las variantes de éxito/aviso/error (como chips translúcidos en vez de los pasteles pensados para fondo blanco) — ver "Análisis y decisiones técnicas" sobre por qué esto basta para todo el proyecto sin tocar componentes.

### Título de vista en la cabecera, centrado
12. **[Frontend, baja]** `app.routes.ts`: añadir `data: { title: '<clave-i18n>' }` a cada ruta hija de `Shell`, reutilizando las claves i18n ya existentes (`fichajes.admin.titulo`, `menu.fichajesMio`, `nominas.admin.titulo`, `facturas.admin.titulo`, `valoraciones.admin.titulo`, `configuracion.admin.titulo`, `empleados.title` — esta última también para `empleados/nuevo` y `empleados/:id`, la cabecera muestra el nombre de la sección, no el título dinámico "nuevo"/"editar" que sigue mostrando el propio formulario).
13. **[Frontend, media]** `Topbar`: signal `pageTitle` (`toSignal` sobre `router.events` filtrado a `NavigationEnd`, con `initialValue` calculado igual al construirse) que recorre `ActivatedRoute.root` hasta el nodo más profundo y lee `snapshot.data['title']`.
14. **[Frontend, media]** `Topbar`/`Footer` reestructurados a `display: grid; grid-template-columns: auto 1fr auto` (ver "Análisis y decisiones técnicas"); el título se muestra centrado (`justify-self: center`, con `text-overflow: ellipsis` si no cupiera).
15. **[Frontend, media]** Quitar el `<h1>`/`section-heading` de cada vista (ver lista completa en "Análisis y decisiones técnicas") y ajustar el layout donde hiciera falta (`empleados-list`).

## Riesgos y dependencias

- **Bug real encontrado y corregido durante la propia verificación manual**: en la primera construcción de `Topbar` (justo al entrar al `Shell` tras el login, mientras el componente de la ruta hija todavía se está resolviendo vía `loadComponent`, que es asíncrono), el nodo más profundo del árbol de `ActivatedRoute` puede no tener `snapshot` todavía — `ruta.snapshot.data[...]` lanzaba `TypeError: Cannot read properties of undefined`, dejando toda la aplicación en una pantalla en blanco justo después de iniciar sesión. Corregido con encadenamiento opcional (`ruta.snapshot?.data?.['title']`): el propio evento `NavigationEnd`, que llega justo después, recalcula el valor correcto una vez la navegación termina de resolverse, así que no se pierde el título, solo se evita el *crash* en el instante de la construcción.
- **Riesgo bajo — paleta oscura sin verificación formal de contraste WCAG**: los tonos del bloque `:root[data-theme="dark"]` se eligieron a criterio (mismo patrón de "solid color + variante translúcida" que ya usaba el proyecto en claro para success/warning/danger) y se verificaron visualmente navegando por varias vistas (incluidos los badges de estado de Empleados y Nóminas), pero no se calculó el ratio de contraste real de cada combinación texto/fondo como sí se hizo en su día para la paleta clara (ver comentario en `_tokens.scss` sobre `--color-primary`/`--color-danger`/`--color-success`, "tonos verificados... calculado sobre la fórmula WCAG"). Si se quisiera el mismo nivel de rigor, sería una revisión aparte.
- **Riesgo bajo — sin tests para `ThemeService` ni para el título de cabecera**: ninguno de los dos tiene test unitario propio (`theme.service.spec.ts` no existe; `topbar.spec.ts` tampoco existía antes de esta fase y sigue sin existir). El comportamiento se verificó manualmente en el navegador, no con un test automatizado — si se detecta una regresión aquí en el futuro, no habrá un test que la señale primero.
- **Dependencia**: ninguna con el backend — todo el bloque es frontend puro (persistencia en `localStorage`, sin ningún endpoint nuevo).

## Criterios de aceptación técnicos

- La pestaña del navegador muestra el logo de Mavero como icono y "Mavero" como título, en cualquier ruta (incluido `/login`).
- El footer (logo, nombre, eslogan, año y derechos reservados) aparece en todas las vistas, autenticadas o no.
- El icono de modo oscuro/claro está visible en la cabecera junto al nombre/rol del usuario autenticado; alternarlo cambia toda la interfaz (sidebar, tarjetas, tablas, badges de estado) sin recargar la página.
- La preferencia de tema persiste tras recargar la página y tras cerrar/reabrir el navegador (mismo dispositivo).
- Sin preferencia guardada, el tema inicial respeta `prefers-color-scheme` del sistema/navegador.
- El título de cada vista aparece centrado en la cabecera (p. ej. "Nóminas (Admin)", "Mi fichaje", "Empleados") y ya no se duplica dentro del contenido de la página.
- Iniciar sesión no produce ningún error en consola ni pantalla en blanco (ver el bug de `Topbar` corregido arriba).
