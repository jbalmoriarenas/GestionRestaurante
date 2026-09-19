# Plan de Implementación — Fase 3.2 (Backend): Nóminas — subida masiva por ZIP y asignación manual

Reutiliza el esquema de [`../bbdd/fase3-1-bbdd.md`](../bbdd/fase3-1-bbdd.md) **sin migraciones nuevas** y las clases de [`fase3-1-back.md`](./fase3-1-back.md) (`Nomina`, `NominaService`, `FileStorageService`, `EstadoNomina`). Expone el contrato que consume [`../front/fase3-2-front.md`](../front/fase3-2-front.md).

## Contexto y objetivo

Permitir al Admin subir un único ZIP con las nóminas de varios empleados de golpe (en vez de una a una): el backend intenta asociar automáticamente cada PDF del ZIP a un empleado por convención de nombre de fichero; los que no se puedan matchear (nombre no válido, o DNI que no corresponde a ningún empleado activo) quedan disponibles para que el Admin los asigne manualmente, sin perder el archivo ni tener que volver a subirlo.

## Análisis y supuestos

- **Convención de nombre de fichero** (confirmada como la más simple y ya prevista en `plan-implementacion-general.md`): `{dniNie}_{periodoAnio}-{periodoMes de 2 dígitos}.pdf`, p. ej. `12345678A_2026-01.pdf`. El patrón de `dniNie` es el mismo que ya valida `EmpleadoCreateDTO` (`fase0-back.md`): 8 dígitos + letra, o `X`/`Y`/`Z` + 7 dígitos + letra.
- **El periodo se extrae del propio nombre de fichero, no de un campo del formulario de subida del ZIP**: permite que un mismo ZIP contenga nóminas de meses distintos si hiciera falta (el caso normal seguirá siendo "un ZIP por mes", pero no se fuerza esa restricción en el backend).
- **Toda entrada del ZIP que no se pueda asociar automáticamente se conserva, nunca se descarta silenciosamente**: se distinguen tres motivos posibles — no es un `.pdf`, el nombre no sigue la convención, o el DNI del nombre no corresponde a ningún empleado activo. Cada uno se reporta por separado en la respuesta.
- **No se crea ninguna tabla ni entidad nueva para "pendientes de asignar"**: los PDFs sin matchear se guardan igualmente en disco (subcarpeta `uploads/nominas/pendientes/`, con nombre físico `{uuid}.pdf`, mismo generador de nombres que `fase3-1-back.md`), pero **sin** fila en `nominas` — la respuesta de la subida del ZIP devuelve la lista de pendientes (`tempId` = el UUID del nombre físico) y el front la mantiene en memoria mientras dure esa pantalla (`fase3-2-front.md`). Asignar uno de esos pendientes llama a un segundo endpoint que mueve/renombra el archivo a su ubicación definitiva y crea la fila real, igual que una subida individual. Si el Admin cierra la pantalla sin asignar todos los pendientes, esos ficheros temporales quedan huérfanos en disco — se documenta como limitación conocida del MVP (ver "Riesgos"), sin limpieza automática en esta fase.
- **Reutiliza exactamente la misma lógica de "upsert" de `fase3-1-back.md`** para las entradas que sí matchean: si el empleado ya tenía nómina de ese periodo, se reemplaza el archivo (y si estaba `ENVIADA`, vuelve a `ADJUNTADA`).

## Desglose de subtareas

1. **[Backend, alta]** `NominaService.procesarZip(MultipartFile zip, Long adminId): ProcesamientoZipDTO` — recorre el ZIP con `ZipInputStream`; por cada entrada:
   - Si no termina en `.pdf` (ignorando mayúsculas/minúsculas): se añade a los pendientes con motivo `NO_ES_PDF`.
   - Si termina en `.pdf` pero el nombre no cumple `{dniNie}_{periodoAnio}-{periodoMes}.pdf`: pendiente con motivo `NOMBRE_NO_VALIDO`.
   - Si el nombre es válido pero el `dniNie` extraído no corresponde a ningún empleado activo (`EmpleadoRepository.findByDniNieAndActivoTrue(...)`): pendiente con motivo `EMPLEADO_NO_ENCONTRADO`.
   - Si matchea: guarda el PDF vía `FileStorageService.guardar(..., "nominas")` y hace el mismo upsert que `NominaService.subir(...)` (`fase3-1-back.md`), incrementando el contador de asignados.
   - Las entradas sin matchear se guardan vía `FileStorageService.guardar(..., "nominas/pendientes")` y se añaden a la lista de pendientes de la respuesta.
2. **[Backend, media]** `NominaService.asignarPendiente(String tempId, Long empleadoId, int periodoMes, int periodoAnio, Long adminId): NominaResponseDTO` — localiza el fichero temporal (`uploads/nominas/pendientes/{tempId}.pdf`; `PendienteNoEncontradoException`, 404, si no existe — pudo haberse asignado ya, o el `tempId` no es válido), lo mueve a su ubicación definitiva (mismo `FileStorageService`, sin volver a leer el `MultipartFile` porque ya está en disco) y crea/actualiza la fila `Nomina` igual que una subida individual.
3. **[Backend, baja]** DTOs: `ProcesamientoZipDTO {asignadosAutomaticamente: int, pendientes: List<NominaPendienteDTO>}`, `NominaPendienteDTO {tempId: String, nombreArchivoOriginal: String, motivo: String ("NO_ES_PDF"|"NOMBRE_NO_VALIDO"|"EMPLEADO_NO_ENCONTRADO")}`, `AsignarPendienteRequestDTO {@NotNull empleadoId, @NotNull @Min(1) @Max(12) periodoMes, @NotNull periodoAnio}`.
4. **[Backend, baja]** `NominaController` (mismo controller de `fase3-1-back.md`), `@PreAuthorize("hasAuthority('NOMINAS:EDICION')")`:
   - `POST /api/nominas/zip` `multipart/form-data {archivo}` → `ProcesamientoZipDTO`
   - `POST /api/nominas/pendientes/{tempId}/asignar` `AsignarPendienteRequestDTO` → `NominaResponseDTO`
5. **[Backend, baja]** Nuevas excepciones: `ZipInvalidoException` (400, el archivo no es un ZIP válido o está vacío), `PendienteNoEncontradoException` (404) — registradas en `GlobalExceptionHandler`.
6. **[Backend, media]** Tests: un ZIP de prueba con 2 entradas que matchean, 1 sin convención de nombre y 1 con un DNI que no existe → `asignadosAutomaticamente = 2` y 2 pendientes con los motivos correctos; asignar un `tempId` inexistente responde 404; asignar un pendiente válido crea la fila y el fichero deja de estar en `pendientes/`; subir un ZIP vacío o corrupto responde 400; token `EMPLEADO` recibe 403 en ambos endpoints.

## Decisiones técnicas

- **Sin tabla nueva para "pendientes de asignar"**: se resuelve con el sistema de ficheros (carpeta `pendientes/`) más una respuesta transitoria al front, en vez de una migración y una entidad más para representar un estado que, por diseño, se espera resolver en la misma sesión de trabajo del Admin — evita complejidad de esquema para un caso de uso efímero.
- **El periodo se extrae del nombre de fichero, no de un campo de formulario para todo el ZIP**: permite ZIPs con nóminas de meses distintos sin ningún coste adicional, y evita que el Admin tenga que repetir un dato que el propio nombre de fichero ya contiene (misma convención pensada desde el principio en `plan-implementacion-general.md`).
- **Reutiliza el 100% de la lógica de "upsert" de la subida individual** (`fase3-1-back.md`): una entrada de ZIP que matchea automáticamente no es conceptualmente distinta de una subida manual — mismo método de servicio por debajo, sin duplicar la lógica de reemplazo/reset de estado.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase3-1-back.md`: reutiliza `Nomina`, `NominaService.subir` (indirectamente) y `FileStorageService` tal cual.
- **Riesgo medio — matching automático dependiente de una convención de nombre** (ya señalado en `plan-implementacion-general.md`): si quien prepara el ZIP no sigue exactamente `{dniNie}_{periodoAnio}-{periodoMes}.pdf`, esas nóminas caen en "pendientes" — el flujo de asignación manual (subtarea 2) es la red de seguridad, no un caso de error bloqueante.
- **Riesgo medio — ficheros huérfanos en `uploads/nominas/pendientes/`**: si el Admin no asigna todos los pendientes de una subida de ZIP, esos archivos quedan en disco indefinidamente sin ninguna fila que los referencie. Mitigación futura (fuera de alcance de esta fase): un job programado que borre archivos de esa carpeta con más de N días de antigüedad, o una pantalla que liste los archivos huérfanos existentes.
- **Riesgo bajo — tamaño del ZIP**: reutiliza el mismo límite `spring.servlet.multipart.max-request-size` de `fase3-1-back.md` (10MB); si se necesitan ZIPs de nóminas de plantillas grandes, ese límite habrá que revisarlo.

## Criterios de aceptación técnicos

- Subir un ZIP con archivos que siguen la convención de nombre para empleados activos existentes crea/actualiza sus nóminas directamente, sin ninguna acción manual del Admin.
- Subir un ZIP con archivos que no siguen la convención, o cuyo DNI no corresponde a ningún empleado, los reporta como pendientes con el motivo correcto, sin romper el procesamiento del resto del ZIP.
- Asignar manualmente un pendiente a un empleado y periodo crea su nómina en estado `ADJUNTADA`, igual que si se hubiera subido de forma individual.
- Un token `EMPLEADO` recibe 403 en ambos endpoints de este documento.
