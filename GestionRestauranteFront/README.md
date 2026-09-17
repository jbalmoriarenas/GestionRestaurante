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
| Tests unitarios    | Vitest           | 4.1.11   |
| Formateo de código | Prettier         | 3.9.7    |
| Estilos            | SCSS             | —        |
| Routing            | @angular/router  | 22.1.x   |

Node y npm están **fijados con [Volta](https://volta.sh) exclusivamente para este proyecto** (ver el bloque `"volta"` en `package.json`), sin afectar la versión global de Node de la máquina. Al entrar en esta carpeta con Volta instalado, se activan automáticamente las versiones correctas.

### Librerías principales (`dependencies`)

- `@angular/core`, `@angular/common`, `@angular/compiler`, `@angular/forms`, `@angular/platform-browser`, `@angular/router` — `^22.1.0`
- `rxjs` — `~7.8.0`
- `tslib` — `^2.3.0`

### Herramientas de desarrollo (`devDependencies`)

- `@angular/cli`, `@angular/build`, `@angular/compiler-cli` — `^22.1.8` / `^22.1.0`
- `typescript` — `~6.0.2`
- `vitest`, `jsdom` — testing
- `prettier` — formateo

## Servidor de desarrollo

```bash
ng serve
```

Abre `http://localhost:4200/`. La aplicación se recarga automáticamente al modificar el código fuente.

## Generar código

```bash
ng generate component nombre-componente
ng generate --help
```

## Build

```bash
ng build
```

Los artefactos de build se generan en `dist/`. Por defecto la build de producción optimiza rendimiento y tamaño.

## Tests

```bash
ng test
```

Ejecuta las pruebas unitarias con [Vitest](https://vitest.dev/).

## Recursos adicionales

- [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli)
- [Volta](https://volta.sh)
