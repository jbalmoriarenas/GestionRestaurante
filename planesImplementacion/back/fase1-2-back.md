# Plan de Implementación — Fase 1.2 (Backend): Vacaciones

Depende de [`../bbdd/fase1-2-bbdd.md`](../bbdd/fase1-2-bbdd.md) y de la base transversal de [`fase0-back.md`](./fase0-back.md). Expone el contrato que consume [`../front/fase1-2-front.md`](../front/fase1-2-front.md). Reutilizado por [`fase1-4-back.md`](./fase1-4-back.md) (vista Admin de vacaciones) sin cambios de esquema.

## Contexto y objetivo

Endpoints para que un empleado consulte su calendario de vacaciones (festivos + aprobadas + pendientes), su resumen anual (total / disponibles / pendientes) y pueda solicitar (y cancelar, si sigue pendiente) días de vacaciones.

## Análisis y supuestos

- El `empleadoId` de todas las operaciones de este documento se obtiene del JWT, igual que en Fichajes (`fase1-1-back.md`) — un empleado nunca opera sobre las vacaciones de otro.
- **Cupo anual prorrateado el año de alta** (confirmado con el usuario): si `empleado.fechaAlta` cae dentro del año consultado, el cupo de ese año no es `dias_vacaciones_anuales` completo, sino prorrateado por los meses que le quedan de año desde su mes de alta (inclusive). Para cualquier año posterior al de alta, se aplica siempre el valor completo de `dias_vacaciones_anuales`. No se prorratea al dar de baja (`fecha_baja`) — fuera de alcance de esta fase, no solicitado.
- "Días disponibles" = cupo del año (completo o prorrateado, ver arriba) − suma de `dias_solicitados` de sus solicitudes en estado `PENDIENTE` o `APROBADA` cuyo `fecha_inicio` cae en el año consultado. Se recalcula siempre contra las filas reales, sin contador cacheado, para no desincronizarse si dos solicitudes se resuelven casi a la vez.
- Días solicitados = días laborables (lunes–viernes) entre `fecha_inicio` y `fecha_fin` (inclusive), **excluyendo** los que estén en `dias_festivos` — cálculo centralizado en un único método de servicio, reutilizado tanto al crear una solicitud como (en `fase1-4-back.md`) al construir el calendario del Admin. Solo se admiten días completos (confirmado con el usuario): no hay concepto de medio día en esta fase.

## Estructura (se añade a los paquetes de `fase0-back.md`)

```
entity/      DiaFestivo, SolicitudVacaciones, EstadoSolicitud (enum)
repository/  DiaFestivoRepository, SolicitudVacacionesRepository
service/     VacacionesService
controller/  VacacionesController
dto/         ResumenVacacionesDTO, DiaCalendarioDTO, SolicitudVacacionesCreateDTO, SolicitudVacacionesResponseDTO
exception/   DiasVacacionesInsuficientesException, SolicitudVacacionesNoEditableException
```

Rutas nuevas agrupadas en `Wuris.Vacaciones`.

## Desglose de subtareas

1. **[Backend, baja]** Entidades `DiaFestivo`, `SolicitudVacaciones` (+ enum `EstadoSolicitud{PENDIENTE,APROBADA,RECHAZADA}`); añadir `diasVacacionesAnuales` a la entidad `Empleado` ya existente.
2. **[Backend, baja]** `DiaFestivoRepository.findByFechaBetween(desde, hasta): List<DiaFestivo>`.
3. **[Backend, baja]** `SolicitudVacacionesRepository`: `findByEmpleado_IdAndEstadoIn(Long, List<EstadoSolicitud>)`, `findByEmpleado_IdAndFechaInicioBetween(Long, LocalDate, LocalDate)`, `findByEmpleado_Id(Long, Pageable)`.
4. **[Backend, alta]** `VacacionesService`:
   - `calcularDiasLaborables(LocalDate inicio, LocalDate fin): int` — itera el rango, cuenta días de lunes a viernes que no estén en `DiaFestivoRepository.findByFechaBetween(inicio, fin)`. Método reutilizado también en `fase1-4-back.md`.
   - `calcularCupoAnual(Empleado empleado, int anio): int` — si `empleado.fechaAlta.getYear() == anio`, calcula `mesesRestantes = 12 - fechaAlta.getMonthValue() + 1` (el mes de alta cuenta completo) y devuelve `Math.round(diasVacacionesAnuales * mesesRestantes / 12.0)`; si `fechaAlta.getYear() < anio`, devuelve `diasVacacionesAnuales` sin prorratear; si `fechaAlta.getYear() > anio` (año consultado anterior al alta), devuelve `0`. Único método fuente de verdad del prorrateo, usado por `obtenerResumenAnual` y por la validación de `solicitar`.
   - `solicitar(Long empleadoId, LocalDate inicio, LocalDate fin): SolicitudVacacionesResponseDTO` — valida `fin >= inicio`; calcula `diasSolicitados`; valida contra `obtenerResumenAnual(...).disponibles` (que ya incorpora el prorrateo del año de `fechaInicio`); si no hay suficientes, `DiasVacacionesInsuficientesException` (409); si ok, crea con `estado=PENDIENTE`.
   - `cancelar(Long empleadoId, Long solicitudId)` — solo si la solicitud es del propio empleado y sigue `PENDIENTE` (confirmado con el usuario: una vez `APROBADA`, la cancelación queda fuera del autoservicio y la gestiona el Admin manualmente); si no, `SolicitudVacacionesNoEditableException` (409).
   - `obtenerResumenAnual(Long empleadoId, int anio): ResumenVacacionesDTO {total, aprobados, pendientes, disponibles}` — `total = calcularCupoAnual(empleado, anio)`.
   - `obtenerCalendario(Long empleadoId, int anio): List<DiaCalendarioDTO>` — combina `dias_festivos` del año + días de las solicitudes `APROBADA`/`PENDIENTE` del empleado (expandiendo cada rango día a día), cada uno con su `tipo` (`FESTIVO`/`APROBADO`/`PENDIENTE`).
   - `listarMisSolicitudes(Long empleadoId, Pageable): Page<SolicitudVacacionesResponseDTO>`.
5. **[Backend, baja]** `VacacionesController` (`/api/vacaciones`), `@PreAuthorize("hasAuthority('VACACIONES:EDICION')")`:
   - `GET /api/vacaciones/resumen?anio=` → `ResumenVacacionesDTO`
   - `GET /api/vacaciones/calendario?anio=` → `List<DiaCalendarioDTO>`
   - `POST /api/vacaciones/solicitudes` `{fechaInicio, fechaFin}` → `SolicitudVacacionesResponseDTO` (201)
   - `GET /api/vacaciones/solicitudes?page=&size=` → `Page<SolicitudVacacionesResponseDTO>`
   - `DELETE /api/vacaciones/solicitudes/{id}` → 204 (cancelar, solo si `PENDIENTE` y propia)
6. **[Backend, baja]** DTOs: `ResumenVacacionesDTO {total, aprobados, pendientes, disponibles}`, `DiaCalendarioDTO {fecha, tipo}`, `SolicitudVacacionesCreateDTO {fechaInicio, fechaFin}`, `SolicitudVacacionesResponseDTO {id, fechaInicio, fechaFin, diasSolicitados, estado, fechaSolicitud, motivoRechazo}`.
7. **[Backend, baja]** `DiasVacacionesInsuficientesException` / `SolicitudVacacionesNoEditableException` → `GlobalExceptionHandler` (409).
8. **[Backend, media]** Test: unit test de `calcularDiasLaborables` (casos con festivos y fines de semana en medio) y de `calcularCupoAnual` (alta en enero → cupo completo; alta en julio → prorrateado; año anterior al alta → 0; año posterior al de alta → completo); `@WebMvcTest` de `VacacionesController` (solicitar más días de los disponibles → 409; cancelar una ya aprobada → 409).

## Decisiones técnicas

- **Cálculo de días laborables centralizado en el servicio**, nunca en el front ni replicado en la parte Admin — un único método fuente de verdad, para que el número que ve el empleado al solicitar coincida siempre con el que ve el Admin al aprobar.
- **Prorrateo con redondeo al entero más cercano** (`Math.round`, no truncado): consistente con la decisión de "solo días completos" — un cupo de 11.5 días redondea a 12, no se trunca a 11. Si en el futuro se quisiera un criterio distinto (p.ej. redondeo siempre a la baja, más conservador para la empresa), es un cambio de una línea en `calcularCupoAnual`, sin tocar esquema ni contrato.
- **El prorrateo se calcula al vuelo en cada consulta** (`calcularCupoAnual`), no se persiste un "cupo ya calculado" en `empleados` — si `dias_vacaciones_anuales` cambia más adelante, el prorrateo del año de alta se recalcula automáticamente con el nuevo valor, sin migración de datos.
- **Recalcular "disponibles" en cada solicitud** contra las filas reales de `solicitudes_vacaciones`, no un contador desnormalizado en `empleados` — evita el riesgo clásico de desincronización de un contador cacheado, a cambio de una query barata (tabla pequeña, indexada por `empleado_id, estado`).
- **`VACACIONES:EDICION` protege tanto el propio flujo del empleado como (en `fase1-4-back.md`) parte de la lectura del Admin** — la escritura de aprobar/rechazar usa una authority distinta, ver la nota de diseño en `fase1-4-back.md`.

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase1-2-bbdd.md`: sin las tablas y el seed de `VACACIONES` en `permisos`, ningún endpoint de este documento es alcanzable (403 para todos).
- **Riesgo medio** (heredado de `bbdd/fase1-2-bbdd.md`): si el catálogo de festivos no se mantiene actualizado año a año, `calcularDiasLaborables` contará como laborable un festivo no sembrado, sobreestimando ligeramente los días consumidos — no bloqueante, pero a vigilar operativamente.

## Criterios de aceptación técnicos

- Un empleado con `fechaAlta` de un año anterior (23 días completos) que solicita un rango de 5 días laborables ve `disponibles` bajar a 18 tras la solicitud (aunque siga `PENDIENTE`: los pendientes también restan disponibilidad, para no permitir sobre-reservar).
- Un empleado dado de alta el 1 de julio del año consultado ve `total = round(23 * 6/12) = 12` (meses de julio a diciembre inclusive), no 23.
- Consultar el resumen de un año anterior al de la `fechaAlta` de un empleado devuelve `total = 0` (no puede haber vacaciones antes de existir como empleado).
- Solicitar un rango cuyos días laborables superan los disponibles actuales responde 409, sin crear la solicitud.
- Un rango que incluye un sábado, un domingo y un festivo sembrado en `dias_festivos` calcula `diasSolicitados` excluyendo los tres.
- Cancelar una solicitud `PENDIENTE` propia libera de vuelta esos días en `disponibles`; cancelar una ya `APROBADA` o de otro empleado responde 409/403.
- `GET /api/vacaciones/calendario?anio=2026` devuelve tanto los festivos sembrados como los días de las solicitudes propias, con el `tipo` correcto por día.
