# Plan de Implementación — Fase 1.3 (Frontend): Fichajes (vista Admin)

Consume el contrato de [`../back/fase1-3-back.md`](../back/fase1-3-back.md). Reutiliza `EmpleadoService`/`ui-select`/`ui-table`/`ui-modal` de [`fase0-front.md`](./fase0-front.md) y `Fichaje`/`FichajeService` de [`fase1-1-front.md`](./fase1-1-front.md).

## Contexto y objetivo

Vista Admin para revisar los fichajes de cualquier empleado: selector de empleado + rango de fechas, tabla diaria con las horas por debajo del mínimo resaltadas en rojo, resúmenes semanal y mensual con su propio resaltado, corrección manual de un fichaje, **edición de la ubicación/radio del restaurante** contra la que se valida la geovalla (confirmado con el usuario: debe poder cambiarla el Admin), y **edición del horario teórico (jornada diaria) de cualquier empleado sin salir de esta vista** (añadido en revisión post-implementación, a petición del usuario). El mínimo de horas es **por empleado** (jornada parcial), no un valor fijo para todos.

**Revisión post-implementación (a petición del usuario, tras la primera entrega de esta fase)**: tres cambios de diseño que no estaban en la versión original y se documentan en detalle en las subtareas 4/4-bis/9 más abajo —
1. La pestaña **Diario** deja de listar cada fichaje individual (cada entrada/salida sueltas, "paradas y arranques"): pasa a mostrar una fila por día con el **total** de ese día (reutilizando `obtenerResumenDiario`, que ya agregaba por día para calcular `cumpleMinimo` y ahora se usa también para pintar la tabla), con un botón "Ver detalle" que abre en un modal los fichajes individuales de ese día concreto (con su botón "Corregir" de siempre).
2. Las pestañas **Semanal** y **Mensual** ganan una columna **"Horario teórico"** (el nuevo campo `horasTeoricas` de `back/fase1-3-back.md`), antes invisible en el front aunque el backend ya lo calculaba.
3. Nueva pestaña **"Horarios"** para editar el horario teórico de un empleado (jornada diaria) directamente desde esta vista, reutilizando el selector de empleado ya existente.
4. **Formato de horas como duración `HH:mm:ss`** (en vez del número decimal crudo que devuelve el backend) en las columnas de tiempo trabajado/horario teórico de Diario, y en "Horas totales" de Semanal/Mensual — ver subtarea 9.

## Estructura (se añade a `features/fichajes/`)

```
features/fichajes/fichajes-admin/fichajes-admin.ts / .html / .scss
shared/utils/tiempo.util.ts   (compartido con fase1-1-front.md, ver subtarea 9)
```

Ruta `/fichajes/admin` protegida con `permissionGuard('EMPLEADOS', 'EDICION')` (coherente con la decisión de autorización de `back/fase1-3-back.md`: si el backend exige `EMPLEADOS:EDICION`, el guard del front debe exigir lo mismo, no `FICHAJES:EDICION`). Enlace a esta vista visible solo para quien tenga ese permiso.

## Desglose de subtareas

### Extensión puntual de Fase 0 (formulario de empleado) — necesaria para la jornada parcial
1. **[Frontend, baja]** Añadir a `Empleado`/`EmpleadoRequest` (modelos de `fase0-front.md`) el campo opcional `horasJornadaDiaria: number | null`.
2. **[Frontend, baja]** Añadir a `EmpleadoForm` (componente ya construido en `fase0-front.md`) un `ui-input type="number"` opcional "Horas de jornada diaria" (placeholder "8", ayuda visual "Déjalo vacío para usar el valor por defecto del restaurante"), con `Validators.min(1)` / `Validators.max(12)` si se rellena.

### Vista Admin de fichajes
3. **[Frontend, baja]** Extender `FichajeService` (de `fase1-1-front.md`) con `listarComoAdmin(filtros, page, size)`, `obtenerResumenDiario(empleadoId, desde, hasta)`, `obtenerResumenSemanal(empleadoId, desde, hasta)`, `obtenerResumenMensual(empleadoId, anio)`, `corregir(id, dto)`.
4. **[Frontend, alta]** `FichajesAdminComponent` (revisado post-implementación, ver "Contexto y objetivo"):
   - `ui-select` de empleado (poblado desde `EmpleadoService.listar()`) + dos `ui-input type="date"` (desde/hasta) para diario/semanal, más un `ui-input type="number"` de año para mensual. El filtro con este selector y el botón "Consultar" solo se muestra en las pestañas Diario/Semanal/Mensual (no en Horarios ni Ubicación, que tienen su propio flujo, ver 4-bis/7).
   - **Pestañas** "Diario" / "Semanal" / "Mensual" / "Horarios" (4-bis) / "Ubicación" (7) dentro de la misma vista (no rutas separadas): cada una llama a su endpoint correspondiente al cambiar empleado/rango.
   - `ui-table` **diaria** (rediseñada; en la primera versión listaba cada fichaje del rango): una fila por día desde `obtenerResumenDiario`, columnas Fecha / **Horario teórico** / **Tiempo trabajado** (ambas en formato `HH:mm:ss`, ver subtarea 9), fila con `fila--bajoMinimo` cuando `cumpleMinimo=false`, y un botón **"Ver detalle"** por fila (sustituye a la columna "Corregir" que antes iba en esta misma tabla). Al pulsarlo, `abrirDetalleDia(fecha)` llama `listarComoAdmin({empleadoId, desde: fecha, hasta: fecha}, 0, 100)` y abre un `ui-modal` ("Fichajes del día") con una tabla de los fichajes individuales de ese día (Entrada / Salida en `HH:mm:ss` / Tiempo trabajado en `HH:mm:ss`) y, en cada fila, el botón **"Corregir"** original → `ui-modal` de corrección (ver más abajo). Al abrir la corrección se cierra el modal de detalle primero (nunca dos modales apilados a la vez).
   - `ui-table` semanal: columnas Semana (inicio–fin) / **Horario teórico** (nuevo, decimal sin formatear — ver subtarea 9 sobre por qué aquí no se convierte a `HH:mm:ss`) / Horas totales (en `HH:mm:ss`), fila en rojo cuando `cumpleMinimo=false` de `obtenerResumenSemanal`.
   - `ui-table` mensual: columnas Mes / **Horario teórico** (decimal) / Horas totales (`HH:mm:ss`), fila en rojo cuando `cumpleMinimo=false` de `obtenerResumenMensual`.
   - `ui-modal` de corrección (sin cambios de fondo respecto a la versión original, solo cambia desde dónde se abre): `ui-input type="datetime-local"` para hora entrada/salida + `ui-input` de observaciones → `corregir(id, dto)`; al guardar con éxito, refresca la consulta activa (`consultar()`), que para Diario vuelve a traer el resumen por día.
5. **[Frontend, baja]** Nota visual (texto de ayuda, no bloqueante) en las tablas semanal/mensual: "El mínimo esperado no descuenta días de vacaciones aprobadas" — coherente con la limitación documentada en `back/fase1-3-back.md`, para que el Admin no interprete un rojo como una alarma automática sin revisar el contexto.

### Horario teórico editable desde esta vista (4-bis, añadido en revisión post-implementación)
4-bis. **[Frontend, media]** Pestaña **"Horarios"**: un formulario propio, independiente del formulario de filtro de las otras pestañas —
   - `ui-select` de empleado (mismo `empleadoOptions()` que el resto de la vista) + `ui-input` de horas (campo de texto libre, no `type="number"`, ver subtarea 9 sobre el formato aceptado) + botón "Guardar horario".
   - Al elegir un empleado (`horarioTeoricoForm.controls.empleadoId.valueChanges`), `EmpleadoService.obtener(id)` precarga el campo de horas con `empleado.horasJornadaDiaria` si tiene valor propio, o con el valor global por defecto (8h, mismo número que `app.fichajes.horas-jornada-default` del backend) si es `null` — y en ese caso se muestra además un texto de ayuda aclarando que ese empleado usa el valor global.
   - Guardar llama al nuevo `EmpleadoService.actualizarHorarioTeorico(id, horas)` (`PATCH .../horario-teorico` de `back/fase1-3-back.md`), no `EmpleadoService.actualizar` (Fase 0): no hace falta cargar ni reenviar el resto de la ficha del empleado.
   - Mientras no se ha elegido empleado, el botón "Guardar" está deshabilitado y se muestra un texto guía ("Selecciona un empleado...").

### Configuración de ubicación del restaurante (confirmado con el usuario)
6. **[Frontend, media]** `ConfiguracionService` (nuevo, `core/services/`): `obtenerUbicacion()`, `actualizarUbicacion(dto)`.
7. **[Frontend, media]** Sección/pestaña adicional "Ubicación" dentro de `FichajesAdminComponent` (junto a Diario/Semanal/Mensual):
   - Al entrar, `obtenerUbicacion()` rellena el formulario con la configuración actual (lat, lng, radio en metros) y la fecha de la última actualización.
   - Formulario con tres `ui-input type="number"` (lat, lng, radio) + `ui-button` "Guardar", con validación en el front que refleje la del backend (`lat` entre -90 y 90, `lng` entre -180 y 180, `radio` entre 10 y 2000) antes de enviar.
   - Texto de ayuda visible siempre: "Los empleados solo podrán fichar entrada dentro de este radio alrededor de este punto" — para que el Admin entienda el impacto antes de guardar, dado el riesgo (documentado en `back/fase1-3-back.md`) de que un error aquí bloquee el fichaje de todo el restaurante.
   - Tras guardar con éxito, notificación de confirmación y refresco del formulario con los valores persistidos (incluye la nueva `updatedAt`).
8. **[Frontend, baja]** Si `lat`/`lng` siguen en el valor semilla (`0, 0`, ver `bbdd/fase1-1-bbdd.md`), mostrar un aviso destacado (no solo un dato más) de "Ubicación sin configurar: ningún empleado podrá fichar hasta que la guardes" — para que el primer arranque del sistema no se confunda con un bug.

### Formato de horas como duración y parseo flexible del input (9, añadido en revisión post-implementación)
9. **[Frontend, media]** Nuevo `shared/utils/tiempo.util.ts`, sin dependencias de Angular (funciones puras, testeadas con `tiempo.util.spec.ts`), **compartido con `fase1-1-front.md`** (`mi-fichaje` también lo usa, ver ese documento):
   - `segundosATiempo(totalSegundos): string` — `HH:mm:ss` con ceros a la izquierda; sustituye a la función privada que `mi-fichaje.ts` ya tenía para su cronómetro (se movió aquí para no duplicarla).
   - `horasATiempo(horas: number): string` — convierte el decimal que devuelve el backend (`horasCalculadas`/`horasTeoricas`, p. ej. `7.5`) a duración (`07:30:00`), redondeando a segundos enteros. Se usa en: Diario (Tiempo trabajado y Horario teórico), Semanal/Mensual (Horas totales), el modal "Fichajes del día", y la tabla "Fichajes de hoy" de `mi-fichaje` (Fase 1.1).
   - **La columna "Horario teórico" de Semanal/Mensual se deja en decimal, sin pasar por `horasATiempo`** — a diferencia de Diario, donde si se formatea: es una asimetría deliberada de esta revisión (alcance pedido explícitamente así por el usuario), no un olvido; si más adelante se pide consistencia total, es un cambio de una línea en la plantilla.
   - `parsearHorasFlexible(valor: string): number | null` — acepta **exactamente** dos formatos de entrada de usuario para el input de horas de la pestaña "Horarios" (subtarea 4-bis): decimal con coma o punto (`"8,5"` o `"8.5"`) u horas:minutos (`"8:30"`); cualquier otro texto devuelve `null`. Reutilizado por un `Validator` de Angular (`horasJornadaValidator`, en el propio `fichajes-admin.ts`, no en el util para no acoplar el util a `@angular/forms`) que combina el chequeo de formato con el rango `[1, 12]` (mismo rango que `back/fase1-3-back.md`/`fase0-back.md`), devolviendo `{formatoInvalido: true}` o `{fueraDeRango: true}` según el caso — el `ui-input` de horas muestra el mensaje traducido correspondiente (`horasFormatoInvalido`/`horasFueraDeRango`/`horasRequerida`) y **no envía la petición** si el campo es inválido.

## Decisiones técnicas

- **El "rojo" reutiliza `--color-danger`/`--color-danger-light` ya existentes** en los tres niveles de resumen — ningún color nuevo.
- **Guard alineado con el backend** (`EMPLEADOS`, no `FICHAJES`): evita que un `EMPLEADO` viera el enlace de menú a esta vista y solo al hacer la petición HTTP descubriera el 403.
- **Tres tablas en una sola vista con pestañas**, no tres componentes/rutas: comparten el mismo selector de empleado y evitan duplicar esa parte del layout.
- **El input de jornada es opcional y con ayuda visual explícita** de qué pasa si se deja vacío — para que el Admin no asuma erróneamente que un campo vacío significa "0 horas".
- **La pestaña de ubicación reutiliza `ui-input type="number"`**, sin mapa embebido ni librería de mapas de terceros: mantiene el alcance simple (tres números + guardar); un selector visual sobre un mapa queda como posible mejora futura, no necesaria para que el Admin pueda cambiar la ubicación.
- **Diario pasa de listar cada fichaje a agregarlos por día, con detalle bajo demanda** (revisión post-implementación, a petición del usuario: "solo quiero ver la suma de los tiempos total, no cada parada y arranque"): reutiliza el endpoint de resumen que ya existía (antes solo alimentaba el resaltado en rojo, ahora también la tabla), y mueve la corrección puntual a un modal de segundo nivel en vez de perder esa función.
- **Pestaña "Horarios" dedicada, en vez de enviar al Admin al alta/edición de empleado (Fase 0) para cambiar la jornada**: el flujo pedido por el usuario es "selecciono un empleado en el filtro [de esta vista] y le asigno el horario teórico", sin salir de `/fichajes/admin` ni tocar el resto de la ficha.
- **El input de horas de "Horarios" es un campo de texto libre con parseo propio (`parsearHorasFlexible`), no un `type="number"` con `min`/`max`**: un `<input type="number">` no admite de forma fiable ni la coma decimal ni el formato `8:30`; validar el texto a mano permite aceptar ambas notaciones (como pidió el usuario) y rechazar con un mensaje claro cualquier otra cosa, en vez de que el navegador silenciosamente redondee o vacíe el campo.
- **`horasATiempo`/`segundosATiempo` centralizados en un util compartido con `fase1-1-front.md`**, no reimplementados en cada componente: evita que el redondeo de segundos o el padding de ceros diverjan entre "Mi fichaje" y la vista Admin.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase1-3-back.md`: sin sus 8 endpoints (los 7 originales + `PATCH .../horario-teorico`), esta vista no es funcional.
- **Dependencia** con `fase0-front.md`: `EmpleadoForm` sigue teniendo su propio campo "Horas de jornada diaria" (con `type="number"` y validación estricta `min`/`max`, sin el parseo flexible de la subtarea 9) — **no se ha unificado con el input de la pestaña "Horarios"** de este documento; hoy conviven dos formularios distintos que escriben el mismo campo del backend con reglas de entrada distintas (uno solo acepta un número, el otro admite coma u `horas:minutos`). Aceptado como limitación de esta revisión — unificar el input o el componente queda como mejora futura si genera confusión real.
- **Riesgo medio**: al no mostrar un mapa, el Admin edita coordenadas "a ciegas" (copiadas de Google Maps u otra fuente externa); un error de transcripción no se detecta visualmente en esta fase — mitigado parcialmente por la validación de rango del backend, pero no evita una coordenada válida pero equivocada. Aceptado como limitación de alcance, ver nota equivalente en `back/fase1-3-back.md`.
- **Riesgo bajo**: si el rango de fechas seleccionado es muy amplio, considerar paginar también los resúmenes — no es un problema al tamaño actual (un restaurante, decenas de empleados).
- **Riesgo bajo — inconsistencia de formato deliberada**: Diario formatea "Horario teórico" en `HH:mm:ss`, pero Semanal/Mensual dejan esa misma columna en decimal (ver subtarea 9) — es el alcance pedido explícitamente por el usuario en esta revisión, no un descuido, pero un Admin que compare ambas pestañas puede notar la diferencia.

## Criterios de aceptación técnicos

- Seleccionar un empleado y un rango de fechas muestra únicamente sus fichajes/resúmenes en ese rango.
- Un empleado con jornada parcial (p.ej. 6h configuradas) ve su propio umbral aplicado en las tres pestañas, distinto del de un empleado sin ese campo configurado (8h por defecto).
- Un día/semana/mes por debajo del mínimo esperado de ESE empleado se ve resaltado en rojo, sin necesidad de abrir el detalle.
- La pestaña **Diario** muestra una fila por día (Fecha / Horario teórico / Tiempo trabajado, ambas en `HH:mm:ss`), nunca una fila por fichaje individual; "Ver detalle" abre un modal con los fichajes concretos de ese día (Entrada/Salida en `HH:mm:ss`) y, desde ahí, "Corregir" sigue permitiendo editar uno.
- Corregir un fichaje (desde el modal de detalle) cierra ese modal, actualiza el resumen diario y, al cambiar de pestaña, también el resumen semanal/mensual de ese rango de forma consistente.
- Las pestañas Semanal y Mensual muestran la columna "Horario teórico" (decimal) junto a "Horas totales" (`HH:mm:ss`).
- En la pestaña **"Horarios"**, elegir un empleado precarga su horario propio o el valor global (8h) si no tiene uno; escribir `"8,5"` o `"8:30"` y guardar persiste `8.5` horas para ese empleado; escribir cualquier otro formato (p.ej. `"8h30"`) no envía la petición y muestra un mensaje de formato inválido; un valor fuera de `[1, 12]` (p.ej. `"15"`) tampoco se guarda y muestra el mensaje de rango.
- La pestaña "Ubicación" muestra los valores actuales al entrar; guardar un cambio válido lo persiste y lo confirma con una notificación, sin recargar la página.
- Guardar valores fuera de rango (p.ej. `lat=200`) muestra el error de validación sin llegar a enviar la petición, o el error 400 del backend si se sortea la validación del front.
- Mientras la ubicación siga en el valor semilla `(0, 0)`, se ve un aviso explícito de que el fichaje está bloqueado para todos hasta configurarla.
- Un usuario con rol `EMPLEADO` no ve el enlace a `/fichajes/admin` en el menú y, si navega ahí directamente, es bloqueado por el guard.
