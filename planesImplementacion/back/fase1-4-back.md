# Plan de Implementación — Fase 1.4 (Backend): Vacaciones (vista Admin)

Reutiliza sin cambios de esquema el modelo de datos de [`../bbdd/fase1-2-bbdd.md`](../bbdd/fase1-2-bbdd.md) y las clases de [`fase1-2-back.md`](./fase1-2-back.md) (entidades, `VacacionesService.calcularDiasLaborables`/`calcularCupoAnual`). Expone el contrato que consume [`../front/fase1-4-front.md`](../front/fase1-4-front.md).

## Contexto y objetivo

Endpoints para que el Admin: (a) vea, por empleado, su calendario y sus solicitudes y las apruebe/rechace; (b) tenga una **bandeja global** con todas las solicitudes pendientes de todos los empleados, sin tener que navegar empleado por empleado; (c) pueda **asignar días de vacaciones de oficio** a un empleado (ej. cierre obligatorio de la empresa), sin que este los solicite primero — confirmado con el usuario, las tres capacidades entran en esta fase.

## Análisis y decisión de autorización

Mismo razonamiento que en `fase1-3-back.md`: `VACACIONES:EDICION` la tienen **tanto** `ADMIN` como `EMPLEADO` (seed de `fase1-2-bbdd.md`), así que no protege por sí sola "ver/resolver las solicitudes de cualquier empleado". Los endpoints de este documento se protegen con **`hasAuthority('EMPLEADOS:EDICION')`**, igual que en `fase1-3-back.md`.

## Análisis y supuestos (asignación de oficio)

- Una asignación de oficio **consume cupo igual que una solicitud normal** (sigue siendo, a todos los efectos, días de vacaciones del empleado): se valida contra `calcularCupoAnual`/disponibilidad igual que `VacacionesService.solicitar`, y si el empleado no tiene suficiente cupo, el Admin no puede forzarla por esta vía — tendría que ajustar antes `dias_vacaciones_anuales` de ese empleado (`fase0-back.md`, ya existente) si de verdad quiere permitir superar el cupo estándar. Se documenta como asunción explícita, ya que el ticket original no lo precisaba.
- No se añade ninguna columna nueva para distinguir "solicitud del empleado" de "asignación de oficio": una asignación de oficio se persiste como una fila normal de `solicitudes_vacaciones` con `estado=APROBADA` desde el momento de creación, `fechaSolicitud = fechaResolucion = ahora`, `resueltaPorAdminId = adminId` — indistinguible en estructura de "una solicitud que el Admin aprobó al instante", que es semánticamente correcto y evita una migración adicional.

## Desglose de subtareas

### Resolución por empleado (ya prevista)
1. **[Backend, media]** Extender `SolicitudVacacionesRepository` (de `fase1-2-back.md`): `findByEmpleado_IdAndEstado(Long, EstadoSolicitud, Pageable)`.
2. **[Backend, media]** Extender `VacacionesService`:
   - `obtenerCalendarioComoAdmin(Long empleadoId, int anio): List<DiaCalendarioDTO>` — mismo método interno que `obtenerCalendario` de `fase1-2-back.md`, expuesto ahora para cualquier empleado.
   - `listarSolicitudesComoAdmin(Long empleadoId, EstadoSolicitud estadoOpcional, Pageable): Page<SolicitudVacacionesResponseDTO>`.
   - `aprobar(Long solicitudId, Long adminId): SolicitudVacacionesResponseDTO` — valida `estado == PENDIENTE` (si no, `SolicitudVacacionesNoEditableException`, 409); pone `estado=APROBADA`, `fechaResolucion=now()`, `resueltaPorAdminId=adminId`.
   - `rechazar(Long solicitudId, Long adminId, String motivo): SolicitudVacacionesResponseDTO` — misma validación de estado; `estado=RECHAZADA`, `motivoRechazo=motivo`.
3. **[Backend, baja]** `VacacionesController` (mismo controller de `fase1-2-back.md`), `@PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")`:
   - `GET /api/vacaciones/admin/calendario?empleadoId=&anio=` → `List<DiaCalendarioDTO>`
   - `GET /api/vacaciones/admin/solicitudes?empleadoId=&estado=&page=&size=` → `Page<SolicitudVacacionesResponseDTO>`
   - `PATCH /api/vacaciones/admin/solicitudes/{id}/aprobar` → `SolicitudVacacionesResponseDTO`
   - `PATCH /api/vacaciones/admin/solicitudes/{id}/rechazar` `{motivo}` → `SolicitudVacacionesResponseDTO`

### Bandeja global de pendientes (confirmado con el usuario)
4. **[Backend, media]** `SolicitudVacacionesRepository.findByEstado(EstadoSolicitud, Pageable)`, con `@EntityGraph(attributePaths = "empleado")` (o `JOIN FETCH` en una `@Query`) para traer el empleado en la misma consulta — evita N+1 al construir el nombre del empleado en cada fila del listado global.
5. **[Backend, baja]** `VacacionesService.listarTodasPendientes(Pageable): Page<SolicitudVacacionesAdminResponseDTO>`.
6. **[Backend, baja]** `GET /api/vacaciones/admin/solicitudes/pendientes?page=&size=` → `Page<SolicitudVacacionesAdminResponseDTO>` (todas los empleados, sin filtro de `empleadoId`, ordenado por `fechaSolicitud` ascendente — las más antiguas primero).
7. **[Backend, baja]** DTO `SolicitudVacacionesAdminResponseDTO {id, empleadoId, empleadoNombre, fechaInicio, fechaFin, diasSolicitados, estado, fechaSolicitud, motivoRechazo}` (extiende la información de `SolicitudVacacionesResponseDTO` con el empleado, necesario porque esta vista no está ya filtrada por uno).

### Asignación de oficio (confirmado con el usuario)
8. **[Backend, media]** `VacacionesService.asignarDeOficio(Long empleadoId, LocalDate inicio, LocalDate fin, Long adminId): SolicitudVacacionesResponseDTO` — misma validación que `solicitar` (Fase 1.2: `fin >= inicio`, calcula `diasSolicitados`, valida disponibilidad con `calcularCupoAnual`); si ok, crea directamente con `estado=APROBADA`, `fechaSolicitud=fechaResolucion=now()`, `resueltaPorAdminId=adminId`.
9. **[Backend, baja]** `POST /api/vacaciones/admin/solicitudes` `{empleadoId, fechaInicio, fechaFin}` → `SolicitudVacacionesResponseDTO` (201), `@PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")`.

### Transversal
10. **[Backend, media]** Test `@WebMvcTest`: token `EMPLEADO` recibe 403 en los siete endpoints; aprobar una solicitud ya `RECHAZADA` responde 409; `GET /solicitudes/pendientes` devuelve solicitudes de más de un empleado en la misma respuesta; `POST /admin/solicitudes` (oficio) sin cupo suficiente responde 409 igual que `solicitar`.

## Decisiones técnicas

- **Reutiliza el mismo cálculo de días laborables y de cupo/disponibilidad** que `fase1-2-back.md` en los tres flujos (aprobar, bandeja global, asignación de oficio) — una única fuente de verdad.
- **La bandeja global no sustituye la vista "por empleado"**: son dos formas de llegar a la misma acción (aprobar/rechazar), sirviendo casos de uso distintos (revisar todo lo pendiente de un vistazo vs. auditar el historial completo de un empleado concreto).
- **Aprobar/rechazar no recalcula `diasSolicitados`**: ese valor quedó fijado en el momento de la solicitud (o de la asignación de oficio); si el catálogo de festivos cambiara después, la fila ya creada no se recalcula retroactivamente.

## Riesgos y dependencias

- **Dependencia dura** con `fase1-2-back.md`: reutiliza entidades, repositorio y los cálculos de días laborables/cupo tal cual.
- **Riesgo bajo**: si dos administradores intentan resolver la misma solicitud casi a la vez, el segundo `PATCH` fallará con `SolicitudVacacionesNoEditableException` (409) porque el estado ya cambió — comportamiento correcto.
- **Riesgo bajo (asignación de oficio)**: si el Admin asigna de oficio unos días que luego el empleado considera injustos, no hay un flujo de "reclamación" en esta fase — el único recurso es que el propio Admin la revierta manualmente (no hay endpoint de "deshacer" una asignación de oficio; se documenta como limitación conocida, no bloqueante para el MVP).

## Criterios de aceptación técnicos

- Un token `ADMIN` puede aprobar/rechazar la solicitud pendiente de cualquier empleado, ver la bandeja global de pendientes y asignar días de oficio; un token `EMPLEADO` recibe 403 en los siete endpoints de este documento.
- Aprobar una solicitud `PENDIENTE` la deja en `APROBADA` con `fechaResolucion` y `resueltaPorAdminId` rellenos, y esos días pasan de "pendiente" a "aprobado" en el calendario del empleado sin ninguna acción adicional de este.
- `GET /api/vacaciones/admin/solicitudes/pendientes` devuelve, en una sola llamada, las solicitudes pendientes de distintos empleados con su nombre, sin que el front tenga que hacer una llamada por empleado.
- Asignar de oficio un rango de días con cupo suficiente crea la solicitud directamente en `APROBADA`, visible de inmediato en el calendario del empleado; con cupo insuficiente responde 409 sin crear nada.
- Rechazar exige un `motivo` y lo persiste; el empleado ve ese motivo en su propio listado (`fase1-2-back.md`).
