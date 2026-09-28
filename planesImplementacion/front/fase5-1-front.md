# Plan de Implementación — Fase 5.1 (Frontend): Configuración — vista de datos de empresa, ubicación y vacaciones

Consume el contrato de [`../back/fase5-1-back.md`](../back/fase5-1-back.md). Introduce la primera vista dedicada `/configuracion` del proyecto — hasta ahora, `configuracion_ubicacion` y `configuracion_vacaciones` se editaban como formularios embebidos dentro de `fichajes-admin` y `vacaciones-admin` respectivamente (`core/services/configuracion.service.ts` ya existe y los sirve).

## Contexto y objetivo

Petición del usuario: una vista de Configuración donde cambiar el nombre y el logo de la empresa. Se aprovecha para **consolidar en un único sitio** los formularios de configuración que ya existían pero estaban dispersos: ubicación/radio de la geovalla (hoy dentro de `fichajes-admin`) y días de vacaciones por defecto (hoy dentro de `vacaciones-admin`) se **mueven** a esta nueva vista `/configuracion`, junto con la sección nueva de datos de empresa. Además, el nombre y logo de la empresa se muestran en la cabecera (`layout`) de toda la aplicación, visibles para cualquier usuario, no solo dentro de esta vista de Admin.

## Estructura

```
core/models/configuracion-empresa.model.ts
core/services/configuracion.service.ts   (se amplía, ya existe)
features/configuracion/configuracion-admin/configuracion-admin.ts / .html / .scss
layout/topbar/... (o el componente de cabecera existente, se amplía)
```

Ruta `/configuracion` protegida con `permissionGuard('EMPLEADOS', 'EDICION')` (mismo permiso que ya exige editar ubicación/vacaciones, ver `back/fase5-1-back.md`). Nueva entrada en `layout/menu-items.ts`: `{ vista: 'EMPLEADOS', ruta: '/configuracion', labelKey: 'configuracion', icono: 'configuracion' }` (entrada de nivel superior, no un grupo — no tiene sub-rutas). Nuevo icono `'configuracion'` en el catálogo `ui-icon`.

## Modelos

### `core/models/configuracion-empresa.model.ts`
```typescript
export interface ConfiguracionEmpresa {
  nombreEmpresa: string;
  logoUrl: string | null;
  emailRemitente: string | null; // Añadido (ampliación posterior, junto con la Fase 6.1 — ver back/fase5-1-back.md)
}
```

## Desglose de subtareas

### Servicio
1. **[Frontend, baja]** `ConfiguracionService` (se amplía, ya existe): añadir `obtenerEmpresa(): Observable<ConfiguracionEmpresa>`; ~~`actualizarEmpresa(nombreEmpresa: string, logo: File | null): Observable<ConfiguracionEmpresa>`~~ **firma ampliada (ampliación posterior)**: `actualizarEmpresa(nombreEmpresa: string, logo: File | null, emailRemitente: string | null): Observable<ConfiguracionEmpresa>` (`FormData`, adjunta `logo` solo si no es `null`; `emailRemitente` siempre se manda, como `''` si es `null`, para poder vaciar el campo ya guardado).

### Vista de Configuración (nueva)
2. **[Frontend, media]** `ConfiguracionAdminComponent` — tarjeta **"Datos de empresa"**: formulario reactivo con `nombreEmpresa` (`@NotBlank` reflejado con `Validators.required`) y un selector de imagen con **previsualización** (muestra el logo actual, o el nuevo seleccionado antes de guardar, vía `URL.createObjectURL` sobre el `File` elegido); botón "Guardar" deshabilitado mientras la petición está en curso (`ui-spinner`). **Añadido (ampliación posterior)**: campo `emailRemitente` (`Validators.email`, opcional — un string vacío pasa la validación, igual que en el backend) para elegir desde qué dirección salen los correos de la app (nóminas y recuperación de contraseña); se precarga con el valor ya guardado al cargar la vista.
3. **[Frontend, media]** Tarjeta **"Ubicación de fichaje"** — **movida tal cual** desde `fichajes-admin.ts`/`.html` (mismo formulario `lat`/`lng`/`radioMetros`, mismos validadores, mismo servicio `ConfiguracionService.obtenerUbicacion`/`actualizarUbicacion` ya existentes): se elimina de `fichajes-admin` y se re-implementa aquí sin cambios de comportamiento.
4. **[Frontend, media]** Tarjeta **"Vacaciones — días anuales por defecto"** — **movida tal cual** desde `vacaciones-admin.ts`/`.html` (mismo formulario, mismo servicio `ConfiguracionService.obtenerVacaciones`/`actualizarVacaciones` ya existentes): se elimina de `vacaciones-admin` y se re-implementa aquí.
5. **[Frontend, baja]** Nuevas claves i18n bajo `configuracion.admin`: `titulo`, `empresa.{titulo,nombreEmpresa,logo,logoActual,sinLogo,seleccionarArchivo,guardar}`, y reutilizar (moviéndolas de sus claves actuales) las de ubicación/vacaciones ya existentes bajo el nuevo namespace `configuracion.admin.ubicacion`/`configuracion.admin.vacaciones`. **Añadidas (ampliación posterior)**: `configuracion.admin.empresa.{emailRemitente,emailRemitentePlaceholder,emailRemitenteError}`.
6. **[Frontend, baja]** Actualizar `app.routes.ts`: añadir `/configuracion` → `ConfiguracionAdminComponent`; quitar de `fichajes-admin`/`vacaciones-admin` el HTML/lógica movidos en los puntos 3 y 4 (sin quitar nada más de esos componentes).

### Cabecera de la aplicación (nombre y logo visibles para todos)
7. **[Frontend, media]** Componente de cabecera existente (`layout/`, el que hoy muestra el nombre fijo de la app o un icono estático — identificar el archivo exacto al implementar): al iniciar sesión, llama a `ConfiguracionService.obtenerEmpresa()` (sin necesitar ningún permiso concreto, alineado con `back/fase5-1-back.md`) y muestra `logoUrl` (si no es `null`, como `<img>`; si es `null`, mantiene el icono/marca por defecto actual) junto al `nombreEmpresa`. Se carga una sola vez por sesión (no en cada navegación) — mismo criterio de carga inicial ya usado para los datos del usuario autenticado.

## Decisiones técnicas

- **Se mueven, no se duplican, los formularios de ubicación y vacaciones**: mantenerlos también en `fichajes-admin`/`vacaciones-admin` obligaría a sincronizar dos sitios que editan la misma fila de BBDD — mejor tener un único lugar de edición ahora que existe una vista de Configuración con sentido propio.
- **La cabecera lee `ConfiguracionEmpresa` de forma independiente de la vista `/configuracion`**: un Empleado sin acceso a `/configuracion` (bloqueado por el guard) sigue viendo el logo/nombre en la cabecera — son dos consumidores distintos del mismo endpoint de lectura, coherente con que `GET /api/configuracion/empresa` no exige ningún permiso especial (`back/fase5-1-back.md`).
- **Previsualización del logo antes de guardar, con `URL.createObjectURL`**: da confirmación visual inmediata de qué imagen se va a subir, sin necesidad de guardar primero para verla — mismo tipo de mejora de UX ya aplicada en otras partes del proyecto (p. ej. vista previa de un PDF generado a partir de una foto en Facturas).

## Riesgos y dependencias

- **Dependencia dura** con `back/fase5-1-back.md`: sin sus endpoints, ni la vista ni la cabecera tienen qué mostrar.
- **Riesgo bajo**: mover el HTML de ubicación/vacaciones fuera de `fichajes-admin`/`vacaciones-admin` toca esos dos componentes (aunque no su lógica de fichajes/vacaciones en sí) — revisar sus tests (`fichajes-admin.spec.ts`, `vacaciones-admin.spec.ts`) por si alguno asume la presencia de esos formularios embebidos y necesita actualizarse en consecuencia.
- **Riesgo bajo**: si el componente de cabecera cachea `ConfiguracionEmpresa` solo al iniciar sesión, un cambio de logo hecho por el Admin no se reflejará para otros usuarios ya conectados hasta que vuelvan a iniciar sesión — aceptable para el tamaño de este proyecto; si se quisiera ver "en caliente", habría que refrescarlo tras cada `actualizarEmpresa()` exitoso, cosa que sí ocurre de forma natural para el propio Admin que lo edita (misma sesión, mismo componente).

## Criterios de aceptación técnicos

- `/configuracion` no aparece en el menú ni es accesible para un usuario con rol `EMPLEADO`.
- Guardar un nuevo nombre de empresa se refleja de inmediato en la cabecera de la propia sesión del Admin.
- Subir un logo lo previsualiza antes de guardar y, tras guardar, la cabecera muestra el nuevo logo.
- Los formularios de ubicación de fichaje y de días de vacaciones por defecto funcionan igual que antes, ahora desde `/configuracion`, y ya no aparecen dentro de `fichajes-admin`/`vacaciones-admin`.
- Sin ningún logo configurado, la cabecera muestra el icono/marca por defecto en vez de una imagen rota.
