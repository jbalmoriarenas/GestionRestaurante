# Plan de Implementación — Cumplimiento Registro Horario 2027 (Backend)

Reutiliza el modelo de datos de [`../bbdd/fichajes_vacaciones_2027-bbdd.md`](../bbdd/fichajes_vacaciones_2027-bbdd.md). Modifica `FichajeService`/`FichajeAdminController` (`back/fase1-1-back.md`, `back/fase1-3-back.md`) y `VacacionesService`/`VacacionesController` (`back/fase1-2-back.md`, `back/fase1-4-back.md`) — **no** crea módulos nuevos, endurece los existentes. Expone el contrato que consume [`../front/fichajes_vacaciones_2027-front.md`](../front/fichajes_vacaciones_2027-front.md).

## Contexto y objetivo

Adaptar `Fichajes` a los requisitos técnicos del nuevo Real Decreto de registro horario digital (en tramitación, entrada en vigor estimada 2027): **inmutabilidad y trazabilidad completa** de cualquier corrección (hoy `FichajeService.corregir` sobrescribe `hora_entrada`/`hora_salida` sin dejar rastro del valor original — ver conversación de planificación), **acceso remoto/exportación** para el empleado y para la Inspección de Trabajo, e **identificación del centro de trabajo** en el registro exportado. Sobre `Vacaciones`, reforzar el mismo principio (`VacacionesService.cancelar` hoy hace un `DELETE` real) y ofrecer, junto con Fichajes, un informe combinado jornada+ausencias por empleado — no porque la norma regule las vacaciones directamente, sino porque es lo que en la práctica se necesita mostrar de forma coherente ante una inspección o el propio empleado.

## Análisis y supuestos

- **El anteproyecto de Real Decreto sigue en tramitación** a fecha de este documento — los requisitos aquí implementados (inmutabilidad, trazabilidad con snapshot antes/después, acceso remoto, custodia 4 años, identificación de centro de trabajo) son los que se han hecho públicos de los borradores conocidos; si el texto final varía en algún detalle, se ajusta sobre esta misma base sin rediseño completo (todo el cambio queda acotado a `FichajeService`, no se toca `Fichaje`/`FichajeRepository` en su forma de fichar/consultar).
- **No se prohíbe corregir un fichaje, se exige que la corrección quede trazada** — el flujo de negocio de `corregir` (un Admin ajusta un fichaje con datos erróneos) no cambia; lo que cambia es que ya no se pierde el valor anterior.
- **El "acceso remoto para la ITSS" se interpreta como una exportación descargable por el Admin** (Excel, mismo patrón que `FacturaExportService`), no como una integración/API directa con ningún sistema de la Inspección — no existe ninguna especificación pública de una API de ese tipo; el Admin descarga y entrega el informe si se le requiere, igual que ya se hace con las nóminas/facturas.
- **El informe combinado jornada+ausencias no es un requisito legal explícito sobre Vacaciones**, es una extensión razonable aprovechando que ambos módulos ya existen — se documenta así para no sobrevender esta parte como obligación normativa.

## Desglose de subtareas

### Fichajes — historial de correcciones
1. **[Backend, baja]** Entidad `FichajeHistorialCorreccion`: campos según `bbdd/fichajes_vacaciones_2027-bbdd.md`. `FichajeHistorialCorreccionRepository extends JpaRepository<FichajeHistorialCorreccion, Long>`: `findByFichaje_IdOrderByFechaCorreccionDesc(Long): List<FichajeHistorialCorreccion>`.
2. **[Backend, alta]** `FichajeService.corregir(...)` (modifica el método existente, `FichajeService.java:196-214`): **antes** de aplicar los nuevos valores sobre la entidad `Fichaje`, construye y persiste un `FichajeHistorialCorreccion` con los valores actuales (`horaEntrada`/`horaSalida`/`observaciones` de la entidad, **tal como están antes de tocarlos**) como "anterior" y los valores recibidos en la petición como "nuevo". Solo después de guardar esa fila de historial se muta y persiste la entidad `Fichaje`, dentro de la misma transacción (`@Transactional`, ya presente) — si el historial no se pudiera guardar, la corrección tampoco se aplica.
3. **[Backend, baja]** `FichajeService.obtenerHistorialCorrecciones(Long fichajeId): List<FichajeHistorialCorreccionDTO>` — lee `FichajeHistorialCorreccionRepository`, resuelve el nombre del Admin (`corregidoPorAdminId` → `Empleado.nombre + apellidos`, mismo patrón que `VacacionesService.toAdminResponse`).
4. **[Backend, baja]** `GET /api/fichajes/admin/{id}/historial` (`Wuris.Fichajes.ADMIN_HISTORIAL`, nueva constante), `@PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")` (mismo permiso que el resto de `FichajeAdminController`) → `List<FichajeHistorialCorreccionDTO> {horaEntradaAnterior, horaSalidaAnterior, observacionesAnterior, horaEntradaNueva, horaSalidaNueva, observacionesNueva, corregidoPorNombre, fechaCorreccion}`.

### Fichajes — centro de trabajo y exportación
5. **[Backend, baja]** `ConfiguracionUbicacion`: añadir campo `nombreCentro` (nullable). `ConfiguracionUbicacionRequestDTO`/`ResponseDTO` y `ConfiguracionUbicacionService` (`back/fase1-1-back.md`): añadir el campo al *update* existente (mismo endpoint `PUT` ya existente, no uno nuevo).
6. **[Backend, alta]** `FichajeExportService` (nuevo, mismo patrón que `FacturaExportService`: genera un Excel vía `StreamingResponseBody`): `exportarRegistro(Long empleadoId, LocalDate desde, LocalDate hasta): StreamingResponseBody` — columnas: fecha, hora entrada, hora salida, horas calculadas, observaciones, "Corregido" (Sí/No — si Sí, indica cuántas veces y remite a `/historial`), nombre y NIF/DNI del empleado, `nombreCentro` de `configuracion_ubicacion` en la cabecera del documento (o "Centro de trabajo no configurado" si es `null`, ver `bbdd/fichajes_vacaciones_2027-bbdd.md`).
7. **[Backend, media]** `GET /api/fichajes/admin/exportar?empleadoId=&desde=&hasta=` (`Wuris.Fichajes.ADMIN_EXPORTAR`), `@PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")` — para que el Admin pueda "poner a disposición" el registro de un empleado concreto.
8. **[Backend, media]** `GET /api/fichajes/mios/exportar?desde=&hasta=` (`Wuris.Fichajes.MIOS_EXPORTAR`), autenticado sin permiso especial (cualquier empleado exporta **solo** su propio registro, `empleadoId` se toma de `CurrentUser`, nunca de un parámetro) — es el "acceso del propio empleado a su registro" que exige la norma, sin depender de que el Admin se lo facilite.

### Vacaciones — sin borrado
9. **[Backend, baja]** `EstadoSolicitud`: añadir valor `CANCELADA`.
10. **[Backend, alta]** `VacacionesService.cancelar(...)` (modifica el método existente, `VacacionesService.java:100-111`): en vez de `solicitudVacacionesRepository.delete(solicitud)`, hace `solicitud.setEstado(EstadoSolicitud.CANCELADA)`, `solicitud.setFechaCancelacion(LocalDateTime.now())`, `solicitud.setCanceladaPorEmpleadoId(empleadoId)` y `save(...)`. La validación existente (solo si `estado == PENDIENTE`, solo el propio empleado) no cambia. `CANCELADA` queda fuera de `ESTADOS_QUE_CONSUMEN_CUPO` (ya solo contiene `PENDIENTE`/`APROBADA`, no requiere cambio) — cancelar sigue liberando el cupo exactamente igual que antes, solo que ahora la solicitud sigue existiendo.
11. **[Backend, baja]** `SolicitudVacacionesResponseDTO`/`SolicitudVacacionesAdminResponseDTO`: añadir `fechaCancelacion` (nullable) a la respuesta — el resto de campos no cambia.
12. **[Backend, baja]** Endpoints existentes de listado (`listarMisSolicitudes`, `listarSolicitudesComoAdmin`) no cambian de firma — `CANCELADA` es simplemente un valor más de `estado`, ya soportado por el filtro opcional existente.

### Informe combinado (Fichajes + Vacaciones)
13. **[Backend, alta]** `RegistroJornadaExportService.generarInformeCombinado(Long empleadoId, LocalDate desde, LocalDate hasta): StreamingResponseBody` — por cada día del rango: si hay `Fichaje`(s) ese día, sus horas; si no hay fichaje pero hay una `SolicitudVacaciones` en estado `APROBADA` que lo cubre, marca el día como "Vacaciones"; si no hay ninguno de los dos, lo deja como "Sin registro" (día no trabajado sin justificar, visible tal cual para que el propio Admin lo detecte, no se oculta). Cabecera con `nombreCentro` y datos del empleado, igual que `FichajeExportService`.
14. **[Backend, media]** `GET /api/fichajes/admin/informe-combinado?empleadoId=&desde=&hasta=` (Admin, `EMPLEADOS:EDICION`) y `GET /api/fichajes/mios/informe-combinado?desde=&hasta=` (propio empleado, mismo criterio que el punto 8).

### Transversal
15. **[Backend, media]** Tests unitarios de `FichajeService.corregir`: corregir un fichaje crea exactamente una fila en `FichajeHistorialCorreccionRepository` con los valores previos correctos (no los nuevos); corregir dos veces el mismo fichaje crea dos filas de historial independientes, cada una con su propio "antes/después".
16. **[Backend, media]** Tests unitarios de `VacacionesService.cancelar`: tras cancelar, la solicitud sigue existiendo (`findById` la sigue encontrando) con `estado = CANCELADA`; el cupo disponible del empleado vuelve a incluir esos días (igual que antes con el `DELETE`); cancelar una solicitud ya `APROBADA` sigue lanzando `SolicitudVacacionesNoEditableException` (comportamiento ya existente, no cambia).
17. **[Backend, baja]** Tests `@WebMvcTest`: `GET /api/fichajes/admin/{id}/historial` y `.../exportar` con token `EMPLEADO` (sin `EMPLEADOS:EDICION`) devuelven 403; `GET /api/fichajes/mios/exportar` con cualquier token autenticado devuelve 200 y solo contiene fichajes del propio `empleadoId` del token (nunca de otro, aunque se manipule cualquier parámetro de la petición — no se acepta `empleadoId` en esta ruta).

## Decisiones técnicas

- **El historial se escribe dentro de la misma transacción que la corrección** (punto 2), no de forma asíncrona ni en un `@EventListener` separado: si algo falla al guardar el historial, la corrección tampoco debe aplicarse — nunca puede quedar un fichaje modificado sin su rastro correspondiente.
- **Snapshot completo antes/después en cada fila de historial, no un diff de campos** (ya justificado en `bbdd/fichajes_vacaciones_2027-bbdd.md`): simplifica también el backend — no hay que calcular qué campos cambiaron, solo copiar el estado completo antes de mutar.
- **La exportación del propio empleado (`/mios/exportar`, `/mios/informe-combinado`) nunca acepta un `empleadoId` como parámetro**: el `CurrentUser` del token es la única fuente del identificador — a diferencia de las rutas `/admin/...`, que sí lo reciben porque las usa el Admin sobre cualquier empleado. Mismo criterio de separación ya aplicado entre `FichajeController` (rutas "mías") y `FichajeAdminController` (rutas de gestión).
- **`VacacionesService.cancelar` cambia de `DELETE` a cambio de estado, pero no se toca `aprobar`/`rechazar`**: esos dos ya dejan rastro completo (`fechaResolucion`, `resueltaPorAdminId`, `motivoRechazo`) desde su implementación original — solo `cancelar` tenía el defecto de borrar.
- **El "día sin registro" en el informe combinado se muestra explícitamente, no se omite**: ocultarlo sería menos útil que mostrarlo — es precisamente el tipo de día que una inspección querría poder identificar (¿trabajó sin fichar? ¿falta injustificada? ¿error de fichaje?), y corresponde al Admin investigarlo, no al sistema decidir por él.

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fichajes_vacaciones_2027-bbdd.md`: sin sus columnas/tabla, ningún punto de este documento tiene dónde persistir nada.
- **Riesgo alto — el Real Decreto sigue en tramitación**: los requisitos exactos (formato de exportación exigido, si la ITSS pedirá integración directa en vez de exportación manual, plazos definitivos) pueden variar respecto al borrador conocido en el momento de escribir este documento; todo el cambio queda acotado a los métodos de servicio señalados arriba, sin tocar el modelo de fichar/solicitar, para poder ajustar sin rediseño si el texto final difiere.
- **Riesgo bajo**: `nombreCentro` a `null` (antes de que el Admin lo configure) hace que los informes muestren "Centro de trabajo no configurado" — aceptable temporalmente, pero conviene que el Admin lo rellene antes de que el registro se necesite de verdad ante una inspección.
- **Riesgo bajo**: el informe combinado no contempla bajas médicas ni otros permisos (no modelados en el proyecto, fuera de alcance) — un día de baja aparecerá como "Sin registro" en vez de justificado; si se pide en el futuro, es una fase aparte con su propio modelo de ausencias.

## Criterios de aceptación técnicos

- Corregir un fichaje deja una fila nueva en el historial con los valores que tenía **antes** de la corrección (no los nuevos), visible en `GET /api/fichajes/admin/{id}/historial`.
- Corregir el mismo fichaje una segunda vez genera una segunda fila de historial independiente; ninguna fila anterior se modifica.
- `GET /api/fichajes/admin/exportar` y `/informe-combinado` devuelven un fichero con la cabecera del centro de trabajo (o el aviso de no configurado) y los datos del empleado solicitado.
- `GET /api/fichajes/mios/exportar` nunca devuelve datos de un empleado distinto al del token, sin importar qué parámetros se envíen.
- Cancelar una solicitud de vacaciones `PENDIENTE` la deja visible con `estado = CANCELADA` y `fechaCancelacion` informada, en vez de hacerla desaparecer; el cupo anual disponible del empleado se actualiza igual que antes.
- Cancelar una solicitud `APROBADA` sigue devolviendo 409, igual que antes de este cambio.
- Un token `EMPLEADO` recibe 403 en todas las rutas `/api/fichajes/admin/**` de este documento.
