# Plan de Implementación — Fase 3.1 (Frontend): Nóminas — listado, subida individual y descarga

Consume el contrato de [`../back/fase3-1-back.md`](../back/fase3-1-back.md). Reutiliza `EmpleadoService`/`ui-select`/`ui-table`/`ui-modal`/`ui-badge`/`ui-button` de [`fase0-front.md`](./fase0-front.md). La ruta `/nominas`, la entrada de menú (`labelKey: 'nominas'`) y el icono `'nominas'` **ya existen** desde Fase 0 (`menu-items.ts`, `ui-icon`), solo falta el componente al que apuntan y darle contenido a `app.routes.ts`. Introduce un componente nuevo, `ui-file-input`, sin precedente en el catálogo `ui-*` actual (no existe ninguno para subir ficheros).

## Contexto y objetivo

Vista Admin `/nominas`: filtros por empleado y periodo (mes/año), tabla paginada con nombre del empleado, email y estado de su nómina para ese periodo (**pendiente** / **adjuntada** / **enviada**), subida individual de un PDF asignándolo a un empleado concreto, y descarga del PDF ya subido para verificar su contenido. El envío por email es [`fase3-3-front.md`](./fase3-3-front.md); la subida masiva vía ZIP es [`fase3-2-front.md`](./fase3-2-front.md) — ambos extienden este mismo componente.

## Estructura (se añade a `features/`)

```
core/models/nomina.model.ts
core/services/nomina.service.ts
shared/ui/ui-file-input/ui-file-input.ts / .html / .scss   ← nuevo, reutilizable
features/nominas/nominas-admin/nominas-admin.ts / .html / .scss
```

Ruta `/nominas` protegida con `permissionGuard('NOMINAS', 'EDICION')` (alineado con la authority que exige `back/fase3-1-back.md`), sustituyendo el placeholder/redirect actual (la ruta no existe todavía, ver `back/fase3-1-back.md`).

## Modelos (`core/models/nomina.model.ts`)

```typescript
export type EstadoNomina = 'PENDIENTE' | 'ADJUNTADA' | 'ENVIADA';

export interface NominaListado {
  empleadoId: number;
  nombreEmpleado: string;
  email: string;
  periodoMes: number;
  periodoAnio: number;
  estado: EstadoNomina;
  nominaId: number | null;
  nombreArchivoOriginal: string | null;
  fechaSubida: string | null;
  fechaEnvio: string | null;
}
```

## Desglose de subtareas

### Componente reutilizable `ui-file-input`
1. **[Frontend, media]** `ui-file-input` (nuevo, `shared/ui/`): envuelve un `<input type="file">` nativo con el mismo patrón `ControlValueAccessor` que `ui-select`/`ui-input`, pero con una diferencia importante — un input de tipo `file` **no admite** que se le asigne un valor por programación (restricción de seguridad del navegador), así que `writeValue(value: File | null)` solo puede limpiar visualmente el campo cuando `value` es `null` (p. ej. al resetear el formulario tras subir con éxito), nunca "rellenarlo". `handleChange` lee `event.target.files?.[0] ?? null` y llama `onChange(file)` con el objeto `File`, no con una cadena. Recibe `[label]`, `[accept]` (p. ej. `"application/pdf"`), `[errorMessage]`; muestra el nombre del archivo elegido (o un placeholder "Ningún archivo seleccionado").
2. **[Frontend, baja]** `ui-file-input.scss`: mismo lenguaje visual que `ui-input`/`ui-select` (borde, radio, colores de foco/error reutilizando los tokens existentes), sin CSS nuevo más allá de adaptar esos mismos valores al elemento nativo `<input type="file">` (que el navegador estiliza de forma distinta a un `<input type="text">` — el botón "Elegir archivo" no es completamente estilizable de forma consistente entre navegadores; se acepta esa limitación en vez de reconstruir el control desde cero con un `<label>`+input oculto, que sería sobre-ingeniería para el alcance de esta fase).

### Listado con filtros
3. **[Frontend, baja]** `NominaService`: `listar(empleadoId: number | null, periodoMes: number, periodoAnio: number, estado: EstadoNomina | null, page: number, size: number): Observable<Page<NominaListado>>`.
4. **[Frontend, media]** `NominasAdminComponent`:
   - Filtros: `ui-select` de empleado (reutiliza `EmpleadoService.listar()`, mismo patrón `empleadoOptions()` que `fichajes-admin`/`vacaciones-admin`, con una opción adicional "Todos" — mismo sentinel `'TODOS'` corregido en `vacaciones-admin.ts` tras su propia revisión de código, ver `fase1-4-front.md`), `ui-input type="month"` para el periodo (un único campo combinado mes+año, en vez de dos `ui-select` separados — el propio input HTML5 `type="month"` devuelve directamente `"AAAA-MM"`, que se separa en `periodoMes`/`periodoAnio` antes de llamar al servicio), `ui-select` de estado (Pendiente/Adjuntada/Enviada/Todos, mismo sentinel `'TODOS'`).
   - `ui-table` paginada: columnas Empleado / Email / Estado (`ui-badge`, variante según estado: `warning` para pendiente, `neutral`/`info` para adjuntada, `success` para enviada) / Acciones.
   - Periodo por defecto = mes/año actual (mismo criterio que `back/fase3-1-back.md`).
5. **[Frontend, media]** Columna Acciones, condicional por estado:
   - `PENDIENTE`: botón "Subir" → abre el modal de subida individual con el empleado y periodo ya fijados (la fila los identifica, no hace falta que el Admin los vuelva a elegir).
   - `ADJUNTADA`/`ENVIADA`: botón "Reemplazar" (mismo modal de subida) + botón "Descargar".
   - El botón "Enviar"/"Reenviar" lo añade `fase3-3-front.md`.
6. **[Frontend, media]** Modal de subida individual (`ui-modal` + `ui-file-input[accept]="'application/pdf'"`): al confirmar, llama `NominaService.subir(empleadoId, periodoMes, periodoAnio, archivo)` (`FormData`, `multipart/form-data`); en éxito, notificación, cierra el modal y refresca la fila afectada (sin recargar toda la tabla); en error 400 (archivo no válido), el toast del `errorInterceptor` global ya lo muestra, el modal se mantiene abierto para reintentar con otro archivo.
7. **[Frontend, media]** `NominaService.descargar(nominaId): Observable<Blob>` (`responseType: 'blob'`) — al pulsar "Descargar", se suscribe, crea una URL temporal (`URL.createObjectURL`) y dispara la descarga mediante un `<a>` generado dinámicamente con el nombre de archivo original (`nombreArchivoOriginal` de la fila), revocando la URL (`URL.revokeObjectURL`) tras el `click` para no dejar memoria reservada. Patrón estándar sin precedente previo en el proyecto (es la primera descarga de archivo binario de la app).
8. **[Frontend, baja]** Nuevas claves i18n (`es.json`/`en.json`, bajo `nominas.admin`): `titulo`, `filtros.{empleado,periodo,estado,todos}`, `tabla.{empleado,email,estado,acciones}`, `estados.{PENDIENTE,ADJUNTADA,ENVIADA}`, `subida.{titulo,archivo,subir,reemplazar}`, `acciones.{subir,reemplazar,descargar}`, `notificaciones.subidaExito`.
9. **[Frontend, baja]** Actualizar `app.routes.ts` para que `/nominas` apunte a `NominasAdminComponent` en vez de caer en el redirect comodín (la ruta no tenía ningún componente asociado hasta ahora).

## Decisiones técnicas

- **`ui-input type="month"` para el filtro de periodo**, en vez de dos `ui-select` de mes y año por separado: un único campo, formato nativo `"AAAA-MM"` que se parte en dos antes de llamar al backend — más simple que replicar el patrón de dos `ui-select` de `fichajes-admin`/mensual.
- **El botón de subida vive en la fila de la tabla, no en un formulario aparte con su propio selector de empleado**: la fila ya identifica al empleado y al periodo, así que el modal de subida no necesita repetir esa elección — evita que el Admin tenga que volver a buscar al empleado que ya tiene delante.
- **`ui-file-input` no puede "recordar" el archivo elegido tras un error del backend** (restricción del navegador, no del componente): si la subida falla, el Admin debe volver a seleccionar el archivo antes de reintentar — se documenta en la ayuda del modal para que no resulte sorprendente.
- **Descarga vía blob + `URL.createObjectURL`**, no un enlace directo a la URL del backend: el endpoint de descarga requiere el token JWT (vía el interceptor HTTP existente), que un `<a href="...">` simple no puede adjuntar — hace falta pasar por `HttpClient` y construir la descarga en el propio front.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase3-1-back.md`: sin sus tres endpoints, esta vista no puede probarse end-to-end.
- **Riesgo bajo**: `ui-file-input` es un componente nuevo sin ningún test/uso previo en el proyecto — su cobertura de tests (`ui-file-input.spec.ts`) debe verificar en particular el comportamiento de `writeValue(null)` (reseteo visual) y que `handleChange` propaga el objeto `File`, no una cadena.
- **Riesgo bajo**: si el Admin sube un archivo muy grande, el único feedback antes del rechazo del backend (400/413) es el toast de error genérico — no hay validación de tamaño en el propio front en esta fase (se podría añadir como mejora, comparando `file.size` contra el límite conocido antes de enviar, para dar el error antes de esperar la respuesta del servidor).

## Criterios de aceptación técnicos

- Al entrar en `/nominas`, se ve el periodo actual por defecto con todos los empleados activos listados, cada uno con su estado real o "Pendiente" si no tiene nómina de ese mes.
- Filtrar por un empleado concreto reduce la tabla a esa única fila (para el periodo seleccionado).
- Cambiar el periodo (mes/año) refresca la tabla mostrando el estado de ese otro periodo para los mismos empleados.
- Subir un PDF a un empleado en estado "Pendiente" lo deja en "Adjuntada" sin recargar la página, y aparecen los botones "Reemplazar"/"Descargar" en su fila.
- Descargar una nómina ya subida abre/guarda el PDF correcto en el navegador.
- Un usuario con rol `EMPLEADO` no ve el enlace a `/nominas` en el menú y, si navega ahí directamente, es bloqueado por el guard.
