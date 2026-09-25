# GestionRestauranteFront

Frontend de GestionRestaurante, generado con [Angular CLI](https://github.com/angular/angular-cli) (standalone components, sin `zone.js` / change detection zoneless por defecto).

## Tecnologías y versiones

| Categoría          | Tecnología       | Versión  |
|--------------------|------------------|----------|
| Framework          | Angular          | 22.1.7   |
| CLI / build        | Angular CLI      | 22.1.8   |
| Build system       | @angular/build   | 22.1.8   |
| Lenguaje           | TypeScript       | 6.0.3    |
| Reactividad        | RxJS             | 7.8.2    |
| Runtime            | Node.js          | 24.21.0  |
| Gestor de paquetes | npm              | 11.19.1  |
| Internacionalización (i18n) | @ngx-translate/core + http-loader | 18.0.0 |
| Tests unitarios    | Vitest           | 4.1.11   |
| Formateo de código | Prettier         | 3.9.7    |
| Estilos            | SCSS (tokens + utilidades propias, sin Angular Material) | — |
| Routing            | @angular/router  | 22.1.x   |

Node y npm están **fijados con [Volta](https://volta.sh) exclusivamente para este proyecto** (ver el bloque `"volta"` en `package.json`), sin afectar la versión global de Node de la máquina. Al entrar en esta carpeta con Volta instalado, se activan automáticamente las versiones correctas.

### Librerías principales (`dependencies`)

- `@angular/core`, `@angular/common`, `@angular/compiler`, `@angular/forms`, `@angular/platform-browser`, `@angular/router` — `^22.1.0`
- `@ngx-translate/core`, `@ngx-translate/http-loader` — internacionalización ES/EN (ficheros en `public/i18n/*.json`) — `^18.0.0`
- `rxjs` — `~7.8.0`
- `tslib` — `^2.3.0`

### Arquitectura de estilos

Sistema de diseño propio en `src/styles/` (sin librería de componentes de terceros): `_tokens.scss` define la paleta/espaciado/tipografía como variables CSS, `_utilities.scss` reúne clases atómicas de layout (`flex`, `gap-*`, `items-center`...) reutilizadas en toda la app, y cada componente Angular trae su propio `.scss` con nomenclatura BEM (`.bloque__elemento--modificador`) solo para estilos con identidad propia. Componentes de UI compartidos (botones, tarjetas, tablas, modales, selects...) viven en `src/app/shared/ui/`.

### Herramientas de desarrollo (`devDependencies`)

- `@angular/cli`, `@angular/build`, `@angular/compiler-cli` — `^22.1.8` / `^22.1.0`
- `typescript` — `~6.0.2`
- `vitest`, `jsdom` — testing
- `prettier` — formateo

## Servidor de desarrollo

```bash
npm start
# equivalente: ng serve
```

Abre `http://localhost:4200/`. La aplicación se recarga automáticamente al modificar el código fuente. Necesita el backend corriendo en `http://localhost:8080` (ver README del backend) — `API_BASE_URL` en `src/app/core/api-base-url.ts` ya apunta ahí por defecto.

## Generar código

```bash
ng generate component nombre-componente
ng generate --help
```

## Build

Desarrollo (sin minificar, con sourcemaps):

```bash
npm run watch
```

Producción — `ng build` usa la configuración `production` por defecto (`defaultConfiguration` en `angular.json`), que minifica y optimiza el bundle:

```bash
npm run build
# equivalente explícito: ng build --configuration production
```

Los artefactos quedan en `dist/GestionRestauranteFront/browser/` (son ficheros estáticos: `index.html`, JS/CSS con hash, `public/i18n/*.json`), listos para servir con cualquier servidor web (Nginx, S3+CloudFront, etc.) — no hace falta Node.js en el servidor donde se sirven, solo para compilarlos.

**Importante antes de compilar para producción**: la URL base de la API (`API_BASE_URL` en `src/app/core/api-base-url.ts`) está fijada en el código en vez de leerse de una variable de entorno en tiempo de ejecución — hay que editarla a la URL real del backend en producción (p. ej. `https://tudominio.com/api`) **antes** de ejecutar el build; los ficheros generados en `dist/` ya llevan esa URL incrustada. Ver [`planesImplementacion/guia-despliegue-aws.md`](../planesImplementacion/guia-despliegue-aws.md) para el despliegue completo (Nginx + dominio + HTTPS).

## Tests

```bash
ng test
```

Ejecuta las pruebas unitarias con [Vitest](https://vitest.dev/).

## Recursos adicionales

- [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli)
- [Volta](https://volta.sh)
