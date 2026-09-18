# Plan de Implementación — Fase 1.1 (Backend): Fichaje

Depende del esquema de [`../bbdd/fase1-1-bbdd.md`](../bbdd/fase1-1-bbdd.md) y de la base transversal de [`fase0-back.md`](./fase0-back.md) (auth, permisos, `Empleado`). Expone el contrato que consume [`../front/fase1-1-front.md`](../front/fase1-1-front.md). Reutilizado sin cambios de esquema por [`fase1-3-back.md`](./fase1-3-back.md) (vista Admin).

## Contexto y objetivo

Endpoints para que un empleado fiche su entrada y salida (estilo Woffu: un único botón que alterna "Iniciar" / "Finalizar"), con el cálculo de horas siempre hecho en el servidor, verificando al iniciar el turno que el empleado está físicamente en el restaurante (geovalla GPS, decisión confirmada con el usuario — ver "Análisis y decisiones técnicas: verificación de ubicación"), y con un resumen de los fichajes de hoy (horas imputadas vs. horas teóricas del turno) para la propia vista del empleado.

## Análisis y supuestos

- El `empleadoId` de todas las operaciones se obtiene del JWT autenticado (`Authentication` → claim `empleadoId`), **nunca** de un parámetro de la petición — así un empleado no puede fichar en nombre de otro aunque manipule la request.
- `permisos` de Fase 0 ya da `EDICION` en `FICHAJES` al tipo `EMPLEADO` (seed `V5`); no hace falta ninguna migración de permisos para esta vista.
- El cronómetro que verá el empleado en el front se reconstruye siempre a partir de `horaEntrada` devuelta por el backend (`GET /api/fichajes/actual`), nunca de un estado que solo viva en el navegador — así un refresco de página o cambiar de dispositivo no rompe el contador.
- La verificación de ubicación se hace **solo al fichar entrada**, no al fichar salida — coherente con el requisito original ("para poder iniciar el fichaje sepamos que el empleado se encuentre en el puesto de trabajo"); una vez dentro de turno, finalizar no vuelve a pedir ubicación.
- **"Horas teóricas del turno"** (confirmado con el usuario, añadido tras la primera implementación): la vista del empleado necesita comparar lo que lleva fichado hoy contra un objetivo de horas. Se introduce ya en esta fase la propiedad `app.fichajes.horas-jornada-default=8` — inicialmente pensada solo para la Fase 1.3 (jornada parcial por empleado en la vista Admin), pero su primer uso real es aquí. `fase1-3-back.md` reutiliza esta misma propiedad como valor por defecto cuando `empleado.horasJornadaDiaria` no esté configurado, en vez de volver a declararla.

## Análisis y decisiones técnicas: verificación de ubicación (geovalla GPS)

Decisión confirmada con el usuario tras evaluar dos opciones (GPS+geovalla vs. IP pública del router): se implementa **GPS + geovalla (Haversine)** en esta fase; la IP pública queda descartada como mecanismo principal por depender de que el restaurante tenga IP fija (ver el análisis completo, ya con esta misma conclusión, en el historial de este documento / `plan-implementacion-general.md`).

- **La ubicación del restaurante debe poder cambiarla el Admin sin redesplegar** (confirmado con el usuario): por eso **no** vive en `application.properties` como se planteó en un primer momento, sino en la tabla `configuracion_ubicacion` (`bbdd/fase1-1-bbdd.md`, una sola fila). El endpoint de lectura/edición para el Admin se define en `fase1-3-back.md`; este documento solo declara la entidad/repositorio y el punto de lectura que usa `FichajeService.iniciar(...)`.
- **Fórmula de distancia**: Haversine, sobre la esfericidad terrestre (radio 6.371.000 m) — precisión de sobra para distancias de cientos de metros, sin necesitar una librería externa.
- **Sin caché de la configuración en el servicio**: `ConfiguracionUbicacionService.obtener()` hace una consulta por PK (`id=1`) en cada `POST /api/fichajes/entrada` — coste despreciable (una fila, una consulta indexada por clave primaria) y evita el riesgo de servir una ubicación desactualizada tras un cambio del Admin mientras el backend sigue corriendo (el mismo razonamiento ya aplicado a `PermisoService` en `fase0-back.md`).
- **Qué pasa si el front no manda `lat`/`lng`**: se valida como error de formato (400), no como "fuera de rango" (403) — son dos causas distintas y el front no debería poder disparar la segunda sin datos.
- **Qué pasa si el empleado deniega el permiso de geolocalización**: el front (`front/fase1-1-front.md`) no llega a llamar al endpoint; el empleado no puede fichar por la app en ese caso. Red de seguridad ya prevista: el Admin corrige/crea el fichaje manualmente (`fase1-3-back.md`), para no dejar a un empleado sin forma de fichar por un permiso denegado.
- **No es un control de seguridad crítico, es antifraude de nivel bajo/medio**: GPS simulado en un dispositivo rooteado podría burlarlo; se acepta ese riesgo residual, mitigado por la corrección manual del Admin como respaldo y por quedar auditado (`lat_entrada`/`lng_entrada` persistidos, ver `bbdd/fase1-1-bbdd.md`) para poder revisar casos sospechosos a posteriori.
- **IP del router — no descartada para siempre**: puede añadirse **más adelante como factor secundario opcional** (no sustituto) si el restaurante llega a tener IP pública fija; no forma parte de esta fase.

## Estructura (se añade a los paquetes ya existentes de `fase0-back.md`, sin carpeta nueva por módulo)

```
entity/      Fichaje, ConfiguracionUbicacion
repository/  FichajeRepository, ConfiguracionUbicacionRepository
service/     FichajeService (incluye el cálculo de distancia Haversine), ConfiguracionUbicacionService
controller/  FichajeController
dto/         FichajeEntradaRequestDTO, FichajeActualDTO, FichajeResponseDTO, FichajeResumenHoyDTO
exception/   FichajeYaAbiertoException, FichajeNoAbiertoException, UbicacionFueraDeRangoException
security/    CurrentUser (helper estático, no una clase de negocio)
```

`ConfiguracionUbicacionService` es un servicio pequeño y separado de `FichajeService` (no mezclado en él): lo consume `FichajeService.iniciar(...)` para leer la ubicación actual, y lo consume también el endpoint de edición del Admin en `fase1-3-back.md` — separar la lectura/escritura de configuración de la lógica de fichaje evita que `FichajeService` crezca con responsabilidades que no son suyas.

Rutas nuevas en `Wuris.Fichajes` (`BASE`, `ENTRADA`, `SALIDA`, `ACTUAL`, `MIOS`, `MIOS_HOY`), siguiendo la convención ya establecida.

El `empleadoId` se extrae del `Authentication` mediante el helper `CurrentUser.empleadoId(authentication)` (no un cast directo): lee el valor guardado en `authentication.getDetails()` por `JwtAuthFilter` y lo valida con `Objects.requireNonNull(..., mensaje)` antes de devolverlo — si algún día un token llegara sin el claim `empleadoId`, falla aquí con un mensaje claro en vez de un `NullPointerException`/`ClassCastException` opaco varias capas más abajo (p. ej. dentro de `EmpleadoRepository.getReferenceById(null)`).

## Desglose de subtareas

1. **[Backend, baja]** Entidad `Fichaje` (`@ManyToOne` a `Empleado`; incluye `latEntrada`/`lngEntrada` nullable; sin mapear la columna `marca_abierto`, ver `bbdd/fase1-1-bbdd.md`).
2. **[Backend, baja]** `FichajeRepository`: `findByEmpleado_IdAndHoraSalidaIsNull(Long empleadoId)`, `findByEmpleado_IdOrderByFechaDescHoraEntradaDesc(Long empleadoId, Pageable)`, `findByEmpleado_IdAndFechaOrderByHoraEntradaAsc(Long empleadoId, LocalDate fecha)` (para el resumen de hoy, sin paginar — un solo día no necesita paginación).
3. **[Backend, baja]** Entidad `ConfiguracionUbicacion` (`id` fijo `1L`, `lat`, `lng`, `radioMetros`, `updatedAt`, `actualizadoPorAdminId`) y `ConfiguracionUbicacionRepository` (extiende `JpaRepository<ConfiguracionUbicacion, Long>`; no necesita métodos derivados propios, `findById(1L)` ya basta).
4. **[Backend, baja]** `ConfiguracionUbicacionService`:
   - `obtener(): ConfiguracionUbicacion` — `repository.findById(1L).orElseThrow(...)` (no debería poder faltar, la fila la siembra `V13`; si faltara, es un error de entorno, no un 404 de negocio).
   - `actualizar(BigDecimal lat, BigDecimal lng, Integer radioMetros, Long adminId): ConfiguracionUbicacion` — usado por el endpoint de Admin de `fase1-3-back.md`, no por este documento; se declara aquí porque vive en el mismo servicio que `obtener()`.
5. **[Backend, alta]** `FichajeService`:
   - `calcularDistanciaMetros(double lat1, double lng1, double lat2, double lng2): double` — método estático, fórmula de Haversine; sin dependencias de Spring, unit-testable de forma aislada.
   - `iniciar(Long empleadoId, double lat, double lng): FichajeResponseDTO` — lee `configuracionUbicacionService.obtener()`; valida `calcularDistanciaMetros(lat, lng, config.getLat(), config.getLng()) <= config.getRadioMetros()`; si no, `UbicacionFueraDeRangoException` (403, "Debes estar en el restaurante para fichar"). Si la ubicación es válida, comprueba que no haya ya un fichaje abierto (`findByEmpleado_IdAndHoraSalidaIsNull`); si lo hay, `FichajeYaAbiertoException` (409). Si todo ok, crea `Fichaje` con `fecha = LocalDate.now()`, `horaEntrada = LocalDateTime.now()`, `latEntrada = lat`, `lngEntrada = lng`, y envuelve el `save()` en un `try/catch(DataIntegrityViolationException)` que relanza `FichajeYaAbiertoException` — cierra el hueco de condición de carrera de dos peticiones casi simultáneas (ver `bbdd/fase1-1-bbdd.md`, índice único `uk_fichajes_abierto`): sin este catch, ese caso límite se colaba como un 500 genérico en vez del 409 de negocio.
   - `finalizar(Long empleadoId): FichajeResponseDTO` — sin verificación de ubicación (ver "Análisis y supuestos"); busca el fichaje abierto, si no existe `FichajeNoAbiertoException` (409); asigna `horaSalida = now()` y calcula `horasCalculadas = Duration.between(horaEntrada, horaSalida)` en horas con 2 decimales (`BigDecimal`, `RoundingMode.HALF_UP`).
   - `obtenerActual(Long empleadoId): FichajeActualDTO` — `enCurso=true` + `horaEntrada` si hay fila abierta, `enCurso=false` en caso contrario.
   - `listarMios(Long empleadoId, Pageable): Page<FichajeResponseDTO>`.
   - `obtenerResumenHoy(Long empleadoId): FichajeResumenHoyDTO` — `findByEmpleado_IdAndFechaOrderByHoraEntradaAsc(empleadoId, LocalDate.now())`; suma `horasCalculadas` de los fichajes ya cerrados de hoy (los `null` de un fichaje todavía abierto no cuentan: el cronómetro en vivo de ese turno es solo feedback visual del front, la fuente de verdad de "horas imputadas" es lo ya cerrado); devuelve esa suma junto con la lista de fichajes de hoy y `horasJornadaDefault` (inyectada vía `@Value("${app.fichajes.horas-jornada-default}")`) como `horasTeoricas`.
6. **[Backend, baja]** `FichajeController` (`/api/fichajes`), `@PreAuthorize("hasAuthority('FICHAJES:EDICION')")` en los cinco:
   - `POST /api/fichajes/entrada` `{lat, lng}` (`@Valid`) → `FichajeResponseDTO` (201)
   - `POST /api/fichajes/salida` (sin cuerpo) → `FichajeResponseDTO` (200)
   - `GET /api/fichajes/actual` → `FichajeActualDTO`
   - `GET /api/fichajes/mios?page=&size=` → `Page<FichajeResponseDTO>`
   - `GET /api/fichajes/mios/hoy` → `FichajeResumenHoyDTO`
7. **[Backend, baja]** DTOs:
   - `FichajeEntradaRequestDTO {lat: Double, lng: Double}` — `@NotNull` **y** rango (`@DecimalMin("-90")`/`@DecimalMax("90")` en `lat`, `@DecimalMin("-180")`/`@DecimalMax("180")` en `lng`): un valor fuera de rango responde 400 antes de llegar siquiera a calcular la distancia contra la geovalla.
   - `FichajeActualDTO {enCurso: boolean, horaEntrada: LocalDateTime}`.
   - `FichajeResponseDTO {id, fecha, horaEntrada, horaSalida, horasCalculadas, observaciones}` (sin `lat`/`lng`: son auditoría interna, no se exponen al propio empleado ni se necesitan en el front).
   - `FichajeResumenHoyDTO {fichajes: List<FichajeResponseDTO>, horasImputadas: BigDecimal, horasTeoricas: BigDecimal}`.
8. **[Backend, baja]** `FichajeYaAbiertoException` / `FichajeNoAbiertoException` (409) / `UbicacionFueraDeRangoException` (403) → registradas en `GlobalExceptionHandler`, mismo `ApiError` que el resto de la app.
9. **[Backend, baja]** Nueva propiedad `app.fichajes.horas-jornada-default=8` en `application.properties`, inyectada en `FichajeService` vía `@Value`. Reutilizada (no vuelta a declarar) por `fase1-3-back.md` como valor por defecto del umbral de horas cuando `empleado.horasJornadaDiaria` no esté configurado.
10. **[Backend, media]** Test:
   - Unit test de `calcularDistanciaMetros` (distancia conocida entre dos coordenadas reales, sin mocks).
   - `@DataJpaTest` verificando que el índice único de `bbdd/fase1-1-bbdd.md` bloquea un segundo fichaje abierto a nivel de BBDD.
   - `@WebMvcTest` de `FichajeController`: `POST /entrada` con coordenadas dentro del radio de la `ConfiguracionUbicacion` sembrada en el test → 201; fuera del radio → 403; sin `lat`/`lng` en el body → 400; dos fichajes seguidos sin salida (ambos dentro del radio) → el segundo, 409.
   - Unit test de `FichajeService.iniciar` mockeando `ConfiguracionUbicacionService` con dos configuraciones distintas, para comprobar que un cambio de ubicación cambia el resultado de la validación sin tocar código (verifica que de verdad es editable en caliente).
   - Unit test de `obtenerResumenHoy`: solo suma `horasCalculadas` de fichajes cerrados, ignora el abierto; `horasTeoricas` refleja la propiedad configurada.
   - **Pendiente al cierre de esta fase**: ninguno de los tests de este apartado está escrito todavía (se detectó en la revisión de MR — `reviewer-mr` — y quedó pospuesto); la lógica sí está verificada manualmente end-to-end (ver Riesgos).

## Decisiones técnicas

- **Doble cinturón de seguridad en "un solo fichaje abierto"**: unicidad garantizada en BBDD (índice único) y validada también en el servicio antes de insertar (con el `try/catch(DataIntegrityViolationException)` de `iniciar(...)` descrito arriba), para un 409 de negocio legible en vez de una `DataIntegrityViolationException` desnuda incluso en el caso límite de dos peticiones casi simultáneas.
- **"Horas imputadas" solo cuenta fichajes cerrados**: un turno todavía abierto no suma a `horasImputadas` de `obtenerResumenHoy`; el cronómetro en vivo que ve el empleado en el front es solo feedback visual, la fuente de verdad de horas trabajadas sigue siendo `horasCalculadas`, que solo existe tras `finalizar()`.
- **Cálculo de horas en backend**: confirmado por el plan general — nunca en el cliente, evita discrepancias por reloj/zona horaria del navegador.
- **Verificación de ubicación antes que verificación de fichaje abierto** en `iniciar(...)`: si el empleado no está en el restaurante, no interesa ni siquiera comprobar si ya tenía un turno abierto — se corta lo antes posible con el error más relevante para el usuario.
- **`calcularDistanciaMetros` como método estático sin dependencias**: se puede testear con un simple `assertEquals`, sin `@SpringBootTest` ni mocks, y es trivialmente reutilizable si en el futuro se necesitara la misma fórmula en otro sitio.
- **Coordenadas del restaurante en BBDD (`configuracion_ubicacion`), no en `application.properties`**: decisión revisada a petición del usuario — la ubicación debe poder cambiarla el Admin sin redesplegar el backend, algo que una propiedad estática no permite. Sigue siendo una tabla de una sola fila (coherente con la asunción de un solo restaurante); si en el futuro hay más de un local, se convierte en una fila por restaurante sin tener que cambiar la firma de `calcularDistanciaMetros` ni el contrato HTTP de `POST /entrada` (solo el criterio para elegir qué fila leer).
- **Auditoría de `lat_entrada`/`lng_entrada` en BBDD, no expuesta en el DTO**: sirve para que el Admin (o un desarrollador investigando un caso dudoso) pueda revisar directamente en BBDD dónde fichó alguien, sin necesidad de un endpoint dedicado en esta fase.

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase1-1-bbdd.md`: sin la tabla `fichajes` (con `lat_entrada`/`lng_entrada`) y la tabla `configuracion_ubicacion` con su fila sembrada, no hay endpoint de entrada/salida con verificación de ubicación posible.
- **Dependencia dura** con `fase0-back.md`: usa el JWT y las authorities ya construidas allí (`FICHAJES:EDICION`).
- **Dependencia hacia delante**: el endpoint de edición de `configuracion_ubicacion` (para que el Admin la cambie) se define en `fase1-3-back.md`, no aquí — si ese endpoint cambia de forma, revisar que `ConfiguracionUbicacionService.actualizar(...)` sigue encajando.
- **Riesgo medio — falsos negativos de GPS en interiores**: un radio de 150 m (valor inicial, editable por el Admin) absorbe la mayoría de la imprecisión indoor, pero un empleado dentro del local con muy mala señal podría, en el peor caso, recibir un 403 real estando físicamente dentro. Mitigación: el mensaje de error debe ser claro y accionable, y la corrección manual del Admin sigue disponible; si el problema es recurrente, el Admin puede simplemente aumentar el radio desde `fase1-3-front.md` sin intervención técnica.
- **Riesgo bajo**: si el reloj del servidor está mal configurado, `horas_calculadas` sería incorrecto para todos los empleados a la vez — asegurar NTP en el entorno de despliegue (nota operativa, no de código).
- **Riesgo bajo — arranque en estado "fail-closed"**: hasta que el Admin configure la ubicación real por primera vez (seed en `0,0`), ningún empleado podrá fichar entrada. Es intencional (ver `bbdd/fase1-1-bbdd.md`), pero debe formar parte del checklist de puesta en marcha del entorno, para que no se confunda con un bug.

## Criterios de aceptación técnicos

- Un empleado sin fichaje abierto, fichando desde coordenadas dentro del radio configurado, recibe 201 con `horaEntrada` = ahora; el `Fichaje` creado persiste `latEntrada`/`lngEntrada` con los valores enviados.
- Un empleado fichando desde coordenadas fuera del radio configurado recibe 403 con un mensaje claro, y no se crea ningún `Fichaje`.
- `POST /api/fichajes/entrada` sin `lat`/`lng` en el cuerpo responde 400 (error de validación), no 403.
- El mismo empleado, sin haber fichado salida, que vuelve a llamar a `POST /api/fichajes/entrada` (aun estando dentro del radio) recibe 409, no un 500 ni un segundo fichaje creado.
- `POST /api/fichajes/salida` sin fichaje abierto previo responde 409; no requiere `lat`/`lng`.
- `horasCalculadas` tras fichar salida es exactamente `(horaSalida - horaEntrada)` en horas, redondeado a 2 decimales, calculado en el servidor.
- `GET /api/fichajes/actual` tras un fichaje de entrada y **sin** salida devuelve `enCurso=true` y la `horaEntrada` correcta, incluso desde una sesión/dispositivo distinto al que fichó entrada.
- Un token de un empleado distinto no puede cerrar ni leer el fichaje abierto de otro (siempre se resuelve por el `empleadoId` del propio JWT).
- Cambiar `lat`/`lng`/`radioMetros` en `configuracion_ubicacion` (vía el endpoint de `fase1-3-back.md`) cambia el resultado de la siguiente llamada a `POST /api/fichajes/entrada` sin reiniciar el backend.
- Antes de la primera configuración por el Admin (fila sembrada en `0,0`), cualquier intento de `POST /api/fichajes/entrada` responde 403 (fail-closed), nunca 201.
- `GET /api/fichajes/mios/hoy` devuelve solo los fichajes de la fecha de hoy del empleado autenticado, con `horasImputadas` igual a la suma de sus `horasCalculadas` cerradas (un turno todavía abierto no suma) y `horasTeoricas` igual al valor de `app.fichajes.horas-jornada-default`.
- Enviar `lat`/`lng` fuera de rango (p. ej. `lat=200`) en `POST /api/fichajes/entrada` responde 400 por validación del DTO, sin llegar a evaluarse contra la geovalla.
