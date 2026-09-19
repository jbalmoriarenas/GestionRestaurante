# Plan de Implementación — Fase 3.2 (Frontend): Nóminas — subida masiva por ZIP y asignación manual

Consume el contrato de [`../back/fase3-2-back.md`](../back/fase3-2-back.md). Extiende `NominaService`/`NominasAdminComponent`/`ui-file-input` de [`fase3-1-front.md`](./fase3-1-front.md), sin componentes `ui-*` nuevos.

## Contexto y objetivo

Añadir, junto al botón de subida individual (`fase3-1-front.md`), un botón "Subir ZIP" que sube varias nóminas de golpe; tras procesarlo, un panel muestra cuántas se asignaron automáticamente y, si queda alguna sin matchear, permite asignarlas una a una eligiendo el empleado desde un `ui-select`, sin tener que volver a seleccionar el archivo (el PDF ya está subido, solo falta decidir a quién pertenece).

## Desglose de subtareas

1. **[Frontend, baja]** Extender `NominaService`: `subirZip(archivo: File): Observable<ProcesamientoZip>`, `asignarPendiente(tempId: string, empleadoId: number, periodoMes: number, periodoAnio: number): Observable<NominaListado>`.
2. **[Frontend, baja]** Modelos: `ProcesamientoZip {asignadosAutomaticamente: number; pendientes: NominaPendiente[]}`, `NominaPendiente {tempId: string; nombreArchivoOriginal: string; motivo: 'NO_ES_PDF' | 'NOMBRE_NO_VALIDO' | 'EMPLEADO_NO_ENCONTRADO'}`.
3. **[Frontend, media]** Botón "Subir nóminas (ZIP)" (junto al de subida individual) → `ui-modal` con `ui-file-input[accept]="'application/zip'"`. Al confirmar, llama `subirZip(archivo)` y, con la respuesta, muestra un resumen ("Se asignaron N nóminas automáticamente") y, si `pendientes.length > 0`, mantiene el modal abierto mostrando el panel de pendientes (subtarea 4) en vez de cerrarlo.
4. **[Frontend, media]** Panel "Sin asignar" (dentro del mismo modal, tras procesar el ZIP): una fila por pendiente con su `nombreArchivoOriginal`, el motivo traducido (i18n, uno por cada valor de `motivo`), un `ui-select` de empleado (mismo `empleadoOptions()` compartido) y un `ui-input type="month"` de periodo (vacío por defecto — el motivo por el que quedó pendiente puede ser precisamente que el nombre no traía un periodo reconocible), y un botón "Asignar" por fila. Al asignar con éxito, esa fila desaparece del panel y se refresca la tabla principal de nóminas (la fila del empleado recién asignado pasa a "Adjuntada").
5. **[Frontend, baja]** Si el Admin intenta cerrar el modal con pendientes sin resolver, se muestra un aviso de confirmación ("Quedan N nóminas sin asignar; si cierras ahora, tendrás que volver a subir esos archivos") antes de cerrar — coherente con que el backend tampoco conserva esos archivos indefinidamente más allá de la carpeta temporal (`fase3-2-back.md`).
6. **[Frontend, baja]** Nuevas claves i18n bajo `nominas.admin.zip`: `titulo`, `subir`, `resumen` (con interpolación del número asignado), `pendientes.{titulo,motivo.NO_ES_PDF,motivo.NOMBRE_NO_VALIDO,motivo.EMPLEADO_NO_ENCONTRADO,asignar}`, `confirmarCierre.{titulo,mensaje,confirmar,cancelar}`.

## Decisiones técnicas

- **La lista de pendientes vive en un `signal` del componente, nunca se persiste en el backend más allá del propio archivo físico temporal** (coherente con la decisión de `back/fase3-2-back.md` de no crear una tabla para esto): si el Admin recarga la página a mitad de asignar pendientes, la lista desaparece del front (los archivos siguen en disco, pero sin ninguna referencia visible hasta que se vuelva a subir el ZIP o se limpien manualmente) — de ahí el aviso de confirmación al cerrar (subtarea 5).
- **Un único modal para "subir ZIP" y "resolver pendientes"**, en vez de dos pantallas separadas: el flujo natural es subir y resolver en el mismo momento, sin necesitar navegar a otro sitio.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase3-2-back.md`: sin sus dos endpoints, esta capacidad no se puede probar end-to-end.
- **Riesgo bajo — UX de "pendientes se pierden al cerrar"**: puede sorprender a un Admin que no lea el aviso de confirmación; mitigado con el modal de confirmación de la subtarea 5, pero sigue siendo una limitación de diseño heredada de la decisión de no persistir los pendientes en BBDD (`back/fase3-2-back.md`).

## Criterios de aceptación técnicos

- Subir un ZIP donde todos los archivos siguen la convención de nombre asigna todas las nóminas automáticamente y cierra el modal mostrando el resumen, sin mostrar ningún panel de pendientes.
- Subir un ZIP con alguna entrada que no matchea mantiene el modal abierto con el panel de pendientes, mostrando el motivo correcto para cada una.
- Asignar un pendiente a un empleado y periodo lo hace desaparecer del panel y actualiza la tabla principal de nóminas sin recargar la página.
- Intentar cerrar el modal con pendientes sin resolver muestra un aviso antes de cerrar de verdad.
