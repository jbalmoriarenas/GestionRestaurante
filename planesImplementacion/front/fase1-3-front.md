# Plan de Implementación — Fase 1.3 (Frontend): Fichajes (vista Admin)

Consume el contrato de [`../back/fase1-3-back.md`](../back/fase1-3-back.md). Reutiliza `EmpleadoService`/`ui-select`/`ui-table`/`ui-modal` de [`fase0-front.md`](./fase0-front.md) y `Fichaje`/`FichajeService` de [`fase1-1-front.md`](./fase1-1-front.md).

## Contexto y objetivo

Vista Admin para revisar los fichajes de cualquier empleado: selector de empleado + rango de fechas, tabla diaria con las horas por debajo del mínimo resaltadas en rojo, resúmenes semanal y mensual con su propio resaltado, corrección manual de un fichaje, y **edición de la ubicación/radio del restaurante** contra la que se valida la geovalla (confirmado con el usuario: debe poder cambiarla el Admin). El mínimo de horas es **por empleado** (jornada parcial), no un valor fijo para todos.

## Estructura (se añade a `features/fichajes/`)

```
features/fichajes/fichajes-admin/fichajes-admin.ts / .html / .scss
```

Ruta `/fichajes/admin` protegida con `permissionGuard('EMPLEADOS', 'EDICION')` (coherente con la decisión de autorización de `back/fase1-3-back.md`: si el backend exige `EMPLEADOS:EDICION`, el guard del front debe exigir lo mismo, no `FICHAJES:EDICION`). Enlace a esta vista visible solo para quien tenga ese permiso.

## Desglose de subtareas

### Extensión puntual de Fase 0 (formulario de empleado) — necesaria para la jornada parcial
1. **[Frontend, baja]** Añadir a `Empleado`/`EmpleadoRequest` (modelos de `fase0-front.md`) el campo opcional `horasJornadaDiaria: number | null`.
2. **[Frontend, baja]** Añadir a `EmpleadoForm` (componente ya construido en `fase0-front.md`) un `ui-input type="number"` opcional "Horas de jornada diaria" (placeholder "8", ayuda visual "Déjalo vacío para usar el valor por defecto del restaurante"), con `Validators.min(1)` / `Validators.max(12)` si se rellena.

### Vista Admin de fichajes
3. **[Frontend, baja]** Extender `FichajeService` (de `fase1-1-front.md`) con `listarComoAdmin(filtros, page, size)`, `obtenerResumenDiario(empleadoId, desde, hasta)`, `obtenerResumenSemanal(empleadoId, desde, hasta)`, `obtenerResumenMensual(empleadoId, anio)`, `corregir(id, dto)`.
4. **[Frontend, alta]** `FichajesAdminComponent`:
   - `ui-select` de empleado (poblado desde `EmpleadoService.listar()`) + dos `ui-input type="date"` (desde/hasta) para la vista diaria, más un `ui-select`/`ui-input type="number"` de año para la vista mensual.
   - **Pestañas o secciones** "Diario" / "Semanal" / "Mensual" dentro de la misma vista (no tres rutas separadas): cada una llama a su endpoint de resumen correspondiente al cambiar empleado/rango.
   - `ui-table` diaria: columnas Fecha / Entrada / Salida / Horas, fila con `class.danger` cuando `cumpleMinimo=false` de `obtenerResumenDiario`.
   - `ui-table` semanal: columnas Semana (inicio–fin) / Horas totales, fila en rojo cuando `cumpleMinimo=false` de `obtenerResumenSemanal`.
   - `ui-table` mensual: columnas Mes / Horas totales, fila en rojo cuando `cumpleMinimo=false` de `obtenerResumenMensual`.
   - Columna de acciones (solo en la vista diaria) con botón "Corregir" → `ui-modal` con `ui-input type="datetime-local"` para hora entrada/salida + `ui-input` de observaciones → `corregir(id, dto)`.
5. **[Frontend, baja]** Nota visual (texto de ayuda, no bloqueante) en las tablas semanal/mensual: "El mínimo esperado no descuenta días de vacaciones aprobadas" — coherente con la limitación documentada en `back/fase1-3-back.md`, para que el Admin no interprete un rojo como una alarma automática sin revisar el contexto.

### Configuración de ubicación del restaurante (confirmado con el usuario)
6. **[Frontend, media]** `ConfiguracionService` (nuevo, `core/services/`): `obtenerUbicacion()`, `actualizarUbicacion(dto)`.
7. **[Frontend, media]** Sección/pestaña adicional "Ubicación" dentro de `FichajesAdminComponent` (junto a Diario/Semanal/Mensual):
   - Al entrar, `obtenerUbicacion()` rellena el formulario con la configuración actual (lat, lng, radio en metros) y la fecha de la última actualización.
   - Formulario con tres `ui-input type="number"` (lat, lng, radio) + `ui-button` "Guardar", con validación en el front que refleje la del backend (`lat` entre -90 y 90, `lng` entre -180 y 180, `radio` entre 10 y 2000) antes de enviar.
   - Texto de ayuda visible siempre: "Los empleados solo podrán fichar entrada dentro de este radio alrededor de este punto" — para que el Admin entienda el impacto antes de guardar, dado el riesgo (documentado en `back/fase1-3-back.md`) de que un error aquí bloquee el fichaje de todo el restaurante.
   - Tras guardar con éxito, notificación de confirmación y refresco del formulario con los valores persistidos (incluye la nueva `updatedAt`).
8. **[Frontend, baja]** Si `lat`/`lng` siguen en el valor semilla (`0, 0`, ver `bbdd/fase1-1-bbdd.md`), mostrar un aviso destacado (no solo un dato más) de "Ubicación sin configurar: ningún empleado podrá fichar hasta que la guardes" — para que el primer arranque del sistema no se confunda con un bug.

## Decisiones técnicas

- **El "rojo" reutiliza `--color-danger`/`--color-danger-light` ya existentes** en los tres niveles de resumen — ningún color nuevo.
- **Guard alineado con el backend** (`EMPLEADOS`, no `FICHAJES`): evita que un `EMPLEADO` viera el enlace de menú a esta vista y solo al hacer la petición HTTP descubriera el 403.
- **Tres tablas en una sola vista con pestañas**, no tres componentes/rutas: comparten el mismo selector de empleado y evitan duplicar esa parte del layout.
- **El input de jornada es opcional y con ayuda visual explícita** de qué pasa si se deja vacío — para que el Admin no asuma erróneamente que un campo vacío significa "0 horas".
- **La pestaña de ubicación reutiliza `ui-input type="number"`**, sin mapa embebido ni librería de mapas de terceros: mantiene el alcance simple (tres números + guardar); un selector visual sobre un mapa queda como posible mejora futura, no necesaria para que el Admin pueda cambiar la ubicación.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase1-3-back.md`: sin sus 7 endpoints, esta vista no es funcional.
- **Dependencia** con `fase0-front.md`: el cambio en `EmpleadoForm` toca un componente ya cerrado en Fase 0; revisar que no rompe sus tests/validaciones existentes al añadir el campo nuevo (es aditivo y opcional, riesgo bajo).
- **Riesgo medio**: al no mostrar un mapa, el Admin edita coordenadas "a ciegas" (copiadas de Google Maps u otra fuente externa); un error de transcripción no se detecta visualmente en esta fase — mitigado parcialmente por la validación de rango del backend, pero no evita una coordenada válida pero equivocada. Aceptado como limitación de alcance, ver nota equivalente en `back/fase1-3-back.md`.
- **Riesgo bajo**: si el rango de fechas seleccionado es muy amplio, considerar paginar también los resúmenes — no es un problema al tamaño actual (un restaurante, decenas de empleados).

## Criterios de aceptación técnicos

- Seleccionar un empleado y un rango de fechas muestra únicamente sus fichajes/resúmenes en ese rango.
- Un empleado con jornada parcial (p.ej. 6h configuradas) ve su propio umbral aplicado en las tres pestañas, distinto del de un empleado sin ese campo configurado (8h por defecto).
- Un día/semana/mes por debajo del mínimo esperado de ESE empleado se ve resaltado en rojo, sin necesidad de abrir el detalle.
- Corregir un fichaje actualiza la tabla diaria y, al cambiar de pestaña, también el resumen semanal/mensual de ese rango de forma consistente.
- La pestaña "Ubicación" muestra los valores actuales al entrar; guardar un cambio válido lo persiste y lo confirma con una notificación, sin recargar la página.
- Guardar valores fuera de rango (p.ej. `lat=200`) muestra el error de validación sin llegar a enviar la petición, o el error 400 del backend si se sortea la validación del front.
- Mientras la ubicación siga en el valor semilla `(0, 0)`, se ve un aviso explícito de que el fichaje está bloqueado para todos hasta configurarla.
- Un usuario con rol `EMPLEADO` no ve el enlace a `/fichajes/admin` en el menú y, si navega ahí directamente, es bloqueado por el guard.
