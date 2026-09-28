# Plan de Implementación — Cumplimiento Registro Horario 2027 (Frontend)

Consume el contrato de [`../back/fichajes_vacaciones_2027-back.md`](../back/fichajes_vacaciones_2027-back.md). Modifica `features/fichajes/fichajes-admin`, `features/fichajes/mi-fichaje`, `features/vacaciones/mis-vacaciones` y `features/vacaciones/vacaciones-admin` ya existentes — no crea vistas nuevas, añade acciones/columnas a las actuales.

## Contexto y objetivo

Dar visibilidad en la interfaz a lo que el backend ya hace trazable: quién y cuándo corrigió un fichaje (y qué había antes), la posibilidad de que Admin y empleado exporten el registro de jornada, el nombre del centro de trabajo en la configuración de ubicación, y que cancelar unas vacaciones deje de "borrar" la solicitud de la vista para pasar a mostrarse como cancelada.

## Desglose de subtareas

### Fichajes — historial y exportación (Admin)
1. **[Frontend, baja]** `FichajeService` (`core/services/fichaje.service.ts`): añadir `obtenerHistorialCorreccion(fichajeId): Observable<HistorialCorreccion[]>`, `exportarComoAdmin(empleadoId, desde, hasta): Observable<Blob>`, `exportarInformeCombinadoComoAdmin(empleadoId, desde, hasta): Observable<Blob>`.
2. **[Frontend, media]** `fichajes-admin.ts`/`.html`: en la tabla de fichajes, cuando `corregidoPorAdmin = true` (ya visible hoy, `FichajeResponseDTO`), añadir acción "Ver historial" que abre un `ui-modal` con una tabla de solo lectura (una fila por corrección): hora entrada/salida **antes**, hora entrada/salida **después**, quién corrigió, cuándo — mismo patrón de modal de detalle ya usado en el proyecto (p. ej. ver detalle de factura).
3. **[Frontend, media]** `fichajes-admin.ts`/`.html`: añadir un formulario/selector (empleado + rango de fechas, reutilizando los mismos controles ya presentes para filtrar el listado) con dos botones: "Exportar registro" y "Exportar informe combinado (jornada + vacaciones)" — descargan el Excel devuelto por el servicio (mismo patrón de descarga de `blob` ya usado en `factura.service.ts`/`facturas-admin.ts` para exportar facturas).
4. **[Frontend, baja]** `fichajes-admin.ts`/`.html`: en el formulario de configuración de ubicación ya existente en este componente, añadir el campo `nombreCentro` (texto simple) junto a `lat`/`lng`/`radioMetros`.

### Fichajes — exportación (propio empleado)
5. **[Frontend, media]** `FichajeService`: `exportarMio(desde, fin): Observable<Blob>`, `exportarInformeCombinadoMio(desde, hasta): Observable<Blob>`.
6. **[Frontend, media]** `mi-fichaje.ts`/`.html`: añadir botón "Descargar mi registro de jornada" (selector de rango de fechas simple, reutilizando `ui-date-range` o el control equivalente ya usado en `mis-vacaciones`) que descarga el Excel del propio empleado — es el ejercicio real del derecho de acceso que exige la norma, sin depender del Admin.

### Vacaciones — cancelación sin borrado
7. **[Frontend, baja]** `vacaciones.model.ts`: añadir `'CANCELADA'` a la unión de `EstadoSolicitud`, y `fechaCancelacion?: string | null` a `SolicitudVacacionesResponse`.
8. **[Frontend, media]** `mis-vacaciones.ts`/`.html`: el botón "Cancelar" (hoy probablemente quita la fila de la lista al llamar al endpoint que hacía `DELETE`) pasa a: tras la llamada exitosa, la fila **permanece visible** con un `ui-badge` de estado "Cancelada" (nuevo color, distinto de `PENDIENTE`/`APROBADA`/`RECHAZADA`) y la fecha de cancelación, en vez de desaparecer de la tabla.
9. **[Frontend, baja]** `vacaciones-admin.ts`/`.html`: añadir `'CANCELADA'` como valor posible del filtro de estado ya existente (`ui-select`), y su `ui-badge` correspondiente en la tabla — el Admin puede ver el histórico completo de solicitudes, incluidas las canceladas por el empleado.
10. **[Frontend, baja]** Nuevas claves i18n: `vacaciones.estado.cancelada`, `vacaciones.mis.canceladaEl`, `fichajes.admin.historial.{titulo,columnas.*}`, `fichajes.admin.exportar.{titulo,seleccionarRango,descargar,descargarCombinado}`, `fichajes.mio.exportar.{titulo,descargar}`, `fichajes.admin.ubicacion.nombreCentro`.

## Decisiones técnicas

- **No se crea ninguna ruta/vista nueva**: todo el trabajo de esta fase amplía pantallas ya existentes (`fichajes-admin`, `mi-fichaje`, `vacaciones-admin`, `mis-vacaciones`) — no hay ningún concepto de producto nuevo que justifique una URL propia, a diferencia de VeriFactu (`integracion_verifactu-front.md`), que sí introducía una integración externa completamente nueva.
- **La cancelación de vacaciones deja de "desaparecer" en el front porque ya no desaparece en el backend**: mantener la fila visible con badge "Cancelada" es el reflejo directo de que `VacacionesService.cancelar` ya no hace un `DELETE` (`back/fichajes_vacaciones_2027-back.md`) — mostrarla y luego ocultarla en el front sería reintroducir en la UI la misma pérdida de trazabilidad que se corrigió en el backend.
- **Exportaciones como descarga de fichero (`Blob`), no como vista renderizada en pantalla**: mismo patrón ya establecido en Facturas — es un documento pensado para entregarse (al empleado, a la Inspección), no para consultarse dentro de la SPA.

## Riesgos y dependencias

- **Dependencia dura** con `back/fichajes_vacaciones_2027-back.md`: sin sus endpoints, ninguna de estas acciones tiene qué llamar.
- **Riesgo bajo**: si `nombreCentro` está vacío, los ficheros exportados mostrarán el aviso de "Centro de trabajo no configurado" (ver `back/fichajes_vacaciones_2027-back.md`) — el front no bloquea la exportación por esto, solo se refleja en el propio documento descargado.

## Criterios de aceptación técnicos

- Un fichaje corregido muestra la acción "Ver historial"; al abrirla, se listan todas las correcciones con sus valores antes/después, quién y cuándo.
- El Admin puede descargar el registro de jornada y el informe combinado de un empleado y rango de fechas concretos.
- Un empleado autenticado puede descargar su propio registro sin necesidad de que el Admin lo genere por él.
- Cancelar una solicitud de vacaciones `PENDIENTE` en "Mis vacaciones" deja la fila visible con el badge "Cancelada", sin necesidad de refrescar la página para verlo.
- El filtro de estado en la vista Admin de vacaciones incluye "Cancelada" y muestra correctamente las solicitudes canceladas.
- El formulario de configuración de ubicación permite guardar y ver el nombre del centro de trabajo.
