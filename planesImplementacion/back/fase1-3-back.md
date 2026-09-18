# Plan de Implementación — Fase 1.3 (Backend): Fichajes (vista Admin)

Depende de [`../bbdd/fase1-3-bbdd.md`](../bbdd/fase1-3-bbdd.md) (columna `horas_jornada_diaria`). Reutiliza sin más cambios de esquema el modelo de [`../bbdd/fase1-1-bbdd.md`](../bbdd/fase1-1-bbdd.md) y las clases de [`fase1-1-back.md`](./fase1-1-back.md) (entidad `Fichaje`, `FichajeRepository`, `ConfiguracionUbicacion`/`ConfiguracionUbicacionService`). Reutiliza `VacacionesService.calcularDiasLaborables` de [`fase1-2-back.md`](./fase1-2-back.md) para el resumen mensual. Expone el contrato que consume [`../front/fase1-3-front.md`](../front/fase1-3-front.md).

## Contexto y objetivo

Endpoints para que el Admin: (a) consulte y corrija los fichajes de **cualquier** empleado, con tres niveles de resumen (diario, semanal, mensual) marcando si se llegó al mínimo de horas esperado (mínimo que puede ser distinto por empleado, jornada parcial); (b) **edite la ubicación y el radio de la geovalla** del restaurante (confirmado con el usuario: la ubicación debe poder cambiarla el Admin, sin redesplegar el backend).

## Análisis y decisión de autorización (importante)

La matriz de permisos de Fase 0 da `FICHAJES:EDICION` **tanto** a `ADMIN` como a `EMPLEADO` (ver `plan-implementacion-general.md`, nota: "la restricción 'solo los suyos' es lógica de negocio, no de la tabla de permisos"). Eso significa que `hasAuthority('FICHAJES:EDICION')` **no** sirve para proteger los endpoints "de todos los empleados" de este documento — un `EMPLEADO` también la tiene.

En vez de crear una vista/permiso nuevo solo para esto, estos endpoints se protegen con **`hasAuthority('EMPLEADOS:EDICION')`** (que hoy solo tiene `ADMIN`, según el seed de `fase0-bbdd.md`): semánticamente, "ver/corregir los fichajes de cualquier empleado" es una capacidad de gestión de empleados, no del módulo de fichaje en sí. Esto reutiliza la matriz ya sembrada sin ninguna migración adicional y sin tener que tocar `permisos` para este caso.

## Análisis y supuestos (jornada parcial y umbrales semanal/mensual)

- **Jornada por empleado** (confirmado con el usuario): `empleado.horasJornadaDiaria` (columna nullable de `bbdd/fase1-3-bbdd.md`) manda si tiene valor; si es `NULL`, se usa `app.fichajes.horas-jornada-default` — propiedad que **ya existe desde `fase1-1-back.md`** (se introdujo antes de lo previsto porque la vista del empleado la necesitó para mostrar "horas teóricas del turno"; aquí no se vuelve a declarar, solo se reutiliza). Un único método (`obtenerUmbralDiario(Empleado)`) resuelve esta prioridad en todo el documento, para no repetir el `if` en tres sitios distintos.
- **Umbral semanal** = `umbralDiario × 5` (semana laboral de lunes a viernes, asunción simple). **Umbral mensual** = `umbralDiario × diasLaborablesDelMes`, reutilizando `VacacionesService.calcularDiasLaborables` (Fase 1.2) para contar días laborables del mes (excluyendo fines de semana y festivos).
- **Simplificación conocida, documentada explícitamente**: ninguno de los dos umbrales agregados resta los días en que el empleado tuviera una solicitud de vacaciones `APROBADA` dentro del rango — un empleado que estuvo una semana de vacaciones aparecerá con esa semana "en rojo" aunque sea correcto. Se acepta como limitación de esta fase (cruzar Fichajes con Vacaciones en el cálculo del umbral es una mejora razonable pero no imprescindible para el MVP); el Admin revisa con criterio, no se bloquea nada automáticamente por esto. Ver "Riesgos".

## Estructura (se añade a los paquetes de `fase0-back.md`/`fase1-1-back.md`)

```
dto/   FichajeResumenDiaDTO, FichajeResumenSemanaDTO, FichajeResumenMesDTO
```

Además, extiende (no sustituye) las clases ya existentes: `Empleado` (nuevo campo), `EmpleadoCreateDTO`/`EmpleadoUpdateDTO`/`EmpleadoResponseDTO` (Fase 0), `FichajeService`/`FichajeRepository` (Fase 1.1).

## Desglose de subtareas

### Extensión puntual de Fase 0 (empleado) — necesaria para la jornada parcial
1. **[Backend, baja]** Añadir `horasJornadaDiaria: BigDecimal` (nullable) a la entidad `Empleado` (definida en `fase0-back.md`).
2. **[Backend, baja]** Añadir el mismo campo (opcional) a `EmpleadoCreateDTO`, `EmpleadoUpdateDTO` y `EmpleadoResponseDTO` (definidos en `fase0-back.md`) y a `EmpleadoMapper` — cambio aditivo y compatible con los clientes existentes del contrato (un campo `null`/ausente no rompe nada de lo ya construido en Fase 0).

### Resumen y corrección de fichajes
3. ~~Nueva propiedad `app.fichajes.horas-jornada-default=8`~~ — **ya existe desde `fase1-1-back.md`**, sin subtarea propia aquí.
4. **[Backend, media]** Extender `FichajeRepository` (de `fase1-1-back.md`): `findByFiltros(Long empleadoId, LocalDate desde, LocalDate hasta, Pageable)`; `findByEmpleado_IdAndFechaBetween(Long, LocalDate, LocalDate)` (sin paginar, base de los tres resúmenes).
5. **[Backend, alta]** Extender `FichajeService`:
   - `obtenerUmbralDiario(Empleado empleado): BigDecimal` — `empleado.horasJornadaDiaria` si no es `null`, si no `horasJornadaDefault`.
   - `listarComoAdmin(Long empleadoIdOpcional, LocalDate desde, LocalDate hasta, Pageable): Page<FichajeResponseDTO>`.
   - `obtenerResumenDiario(Long empleadoId, LocalDate desde, LocalDate hasta): List<FichajeResumenDiaDTO>` — agrupa por `fecha`, suma `horasCalculadas` del día (puede haber más de un fichaje: turno partido) y marca `cumpleMinimo = horasDelDia >= obtenerUmbralDiario(empleado)`.
   - `obtenerResumenSemanal(Long empleadoId, LocalDate desde, LocalDate hasta): List<FichajeResumenSemanaDTO>` — agrupa el resumen diario por semana ISO (`WeekFields.ISO`), suma horas, `cumpleMinimo = horasSemana >= obtenerUmbralDiario(empleado) * 5`.
   - `obtenerResumenMensual(Long empleadoId, int anio): List<FichajeResumenMesDTO>` — agrupa por mes del año, `cumpleMinimo = horasMes >= obtenerUmbralDiario(empleado) * vacacionesService.calcularDiasLaborables(inicioMes, finMes)`.
   - `corregir(Long fichajeId, Long adminId, LocalDateTime horaEntrada, LocalDateTime horaSalida, String observaciones): FichajeResponseDTO` — recalcula `horasCalculadas`, marca `corregidoPorAdmin=true`, `corregidoPorAdminId=adminId`, `fechaCorreccion=now()`.
6. **[Backend, baja]** `FichajeAdminController` (clase separada de `FichajeController` de `fase1-1-back.md`, para no mezclar en la misma clase dos anotaciones de autorización distintas), todos con `@PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")`:
   - `GET /api/fichajes/admin?empleadoId=&desde=&hasta=&page=&size=` → `Page<FichajeResponseDTO>`
   - `GET /api/fichajes/admin/resumen/diario?empleadoId=&desde=&hasta=` → `List<FichajeResumenDiaDTO>`
   - `GET /api/fichajes/admin/resumen/semanal?empleadoId=&desde=&hasta=` → `List<FichajeResumenSemanaDTO>`
   - `GET /api/fichajes/admin/resumen/mensual?empleadoId=&anio=` → `List<FichajeResumenMesDTO>`
   - `PUT /api/fichajes/admin/{id}` `{horaEntrada, horaSalida, observaciones}` → `FichajeResponseDTO`
7. **[Backend, baja]** DTOs: `FichajeResumenDiaDTO {fecha, horasCalculadas, cumpleMinimo}`, `FichajeResumenSemanaDTO {inicioSemana, finSemana, horasCalculadas, cumpleMinimo}`, `FichajeResumenMesDTO {mes, anio, horasCalculadas, cumpleMinimo}`.

### Edición de la ubicación del restaurante (confirmado con el usuario)
8. **[Backend, baja]** `GET /api/configuracion/ubicacion` → `ConfiguracionUbicacionResponseDTO {lat, lng, radioMetros, updatedAt}`, `@PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")` — mismo razonamiento de autorización que el resto de este documento: es una capacidad de Admin, no del módulo de fichajes en sí.
9. **[Backend, media]** `PUT /api/configuracion/ubicacion` `{lat, lng, radioMetros}` → `ConfiguracionUbicacionResponseDTO`, misma anotación. Llama a `ConfiguracionUbicacionService.actualizar(lat, lng, radioMetros, adminId)` (declarado en `fase1-1-back.md`); valida `lat ∈ [-90, 90]`, `lng ∈ [-180, 180]`, `radioMetros ∈ [10, 2000]` (metros) vía `@Valid` en el DTO de entrada, para evitar que un valor absurdo (radio negativo, coordenadas fuera de rango) deje el fichaje inservible por error de tecleo.
10. **[Backend, baja]** DTOs: `ConfiguracionUbicacionRequestDTO {lat: BigDecimal, lng: BigDecimal, radioMetros: Integer}` (con las validaciones de rango), `ConfiguracionUbicacionResponseDTO {lat, lng, radioMetros, updatedAt}`.
11. **[Backend, baja]** Test `@WebMvcTest`: token `EMPLEADO` recibe 403 en ambos endpoints; `PUT` con `lat=200` (fuera de rango) responde 400; `PUT` válido seguido de un `POST /api/fichajes/entrada` con esas nuevas coordenadas responde 201 (verifica end-to-end que el cambio del Admin se aplica de inmediato).

### Transversal
12. **[Backend, baja]** Test `@WebMvcTest` general: un token `EMPLEADO` (con `FICHAJES:EDICION` pero sin `EMPLEADOS:EDICION`) recibe 403 en los siete endpoints de este documento; unit test de `obtenerUmbralDiario` (con y sin `horasJornadaDiaria` seteado) y de los tres niveles de resumen con un empleado de jornada parcial (ej. 6h/día → semanal 30h, mensual `6 × diasLaborablesDelMes`).

## Decisiones técnicas

- **Reutilizar `EMPLEADOS:EDICION` en vez de crear una vista nueva**: mantiene la matriz de permisos de Fase 0 como única fuente de verdad de "quién puede ver datos de otros empleados".
- **`horasJornadaDiaria` nullable, no un valor por defecto en columna**: permite distinguir "no configurado, usa el global" de "configurado explícitamente a 8", útil si el valor global cambia más adelante y no se quiere que arrastre a todos los empleados que ya tenían 8h explícitos.
- **Un único método `obtenerUmbralDiario`** para los tres niveles de resumen: evita que el umbral diario, semanal y mensual puedan divergir por un descuido al calcular cada uno por separado.
- **Reutiliza `calcularDiasLaborables` de Vacaciones (Fase 1.2)** para el umbral mensual, en vez de reimplementar el conteo de días laborables de un mes — un único método fuente de verdad para "días laborables", compartido entre los dos módulos.
- **Corrección manual reutiliza el mismo cálculo de horas** que `FichajeService.finalizar()` (Fase 1.1).
- **La edición de ubicación reutiliza `ConfiguracionUbicacionService` de `fase1-1-back.md`** en vez de que este documento hable directamente con el repositorio — el controller de Admin es un cliente más de ese servicio, igual que `FichajeService.iniciar(...)`.
- **Validación de rangos en el DTO de entrada** (`lat`/`lng`/`radioMetros`), no solo confiar en que el Admin introduzca valores sensatos desde el front: un 400 claro es mejor que dejar el fichaje inservible para todo el restaurante por un valor mal tecleado.

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase1-3-bbdd.md`: sin la columna `horas_jornada_diaria`, no hay jornada parcial que resolver (todo caería al global, sin romper nada, pero sin cumplir el requisito confirmado).
- **Dependencia dura** con `fase1-1-back.md`: reutiliza `Fichaje`, `FichajeRepository`, el cálculo de horas, y ahora también `ConfiguracionUbicacion`/`ConfiguracionUbicacionService` para el endpoint de edición de ubicación.
- **Dependencia dura** con `fase1-2-back.md`: reutiliza `calcularDiasLaborables` para el umbral mensual — si ese método cambia de firma, revisar aquí también.
- **Riesgo medio (documentado arriba)**: los umbrales semanal/mensual no descuentan vacaciones aprobadas del período — puede generar falsos "en rojo" en semanas/meses con vacaciones. Mitigación mínima para esta fase: mostrar en la misma vista (o en un tooltip) si el empleado tuvo vacaciones aprobadas ese período, para que el Admin lo tenga en cuenta antes de actuar sobre el aviso.
- **Riesgo medio — un cambio de ubicación afecta a todos los empleados a la vez**: si el Admin comete un error al guardar (p.ej. invierte `lat`/`lng`), todos los fichajes de entrada empiezan a fallar hasta que alguien lo note y lo corrija — no hay validación semántica posible más allá de los rangos numéricos (el backend no puede saber si una coordenada válida es la correcta). Mitigación: mostrar en el front (`fase1-3-front.md`) la ubicación actual de forma clara antes de guardar, y considerar (fuera de alcance de esta fase) una confirmación con "doble check" o un enlace a un mapa para verificar visualmente antes de guardar.
- **Riesgo bajo**: si un empleado tiene fichajes en distinta zona horaria (no aplica hoy) habría que revisar el agrupado por `fecha`.

## Criterios de aceptación técnicos

- Un token `ADMIN` puede listar/filtrar fichajes de cualquier empleado en los tres niveles de resumen; un token `EMPLEADO` recibe 403 en los siete endpoints de este documento.
- Un empleado con `horasJornadaDiaria = NULL` usa el umbral global (8h); uno con `horasJornadaDiaria = 6.00` usa 6h como umbral diario, 30h como semanal y `6 × díasLaborablesDelMes` como mensual.
- `GET /api/fichajes/admin/resumen/diario` marca `cumpleMinimo=false` en un día por debajo del umbral de ese empleado concreto, y `true` en uno que lo alcanza.
- `GET /api/fichajes/admin/resumen/semanal` marca en rojo una semana cuya suma de horas no llega a `umbralDiario × 5`, aunque algún día suelto de esa semana sí llegara a su mínimo diario.
- Corregir un fichaje recalcula `horasCalculadas` de forma consistente y deja traza (`corregidoPorAdmin`, `corregidoPorAdminId`, `fechaCorreccion`); el resumen diario/semanal/mensual de ese rango refleja el cambio en la siguiente consulta.
- `PUT /api/configuracion/ubicacion` con valores válidos actualiza `lat`/`lng`/`radioMetros` y `updatedAt`/`actualizadoPorAdminId`; el siguiente `POST /api/fichajes/entrada` de cualquier empleado valida ya contra la nueva ubicación, sin reiniciar el backend.
- `PUT /api/configuracion/ubicacion` con `lat`, `lng` o `radioMetros` fuera de rango responde 400 y no modifica la fila existente.
