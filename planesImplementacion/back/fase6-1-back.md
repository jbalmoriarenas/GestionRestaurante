# Plan de Implementación — Fase 6.1 (Backend): Recuperación de contraseña ("olvidé mi contraseña")

Reutiliza el modelo de datos de [`../bbdd/fase6-1-bbdd.md`](../bbdd/fase6-1-bbdd.md) (tabla `tokens_recuperacion_password`). Reutiliza `EmpleadoService.resetearPassword` ya existente (`back/fase1-4-back.md` y siguientes) para fijar la contraseña nueva — no se duplica esa lógica. Introduce en el proyecto, por primera vez, un **motor de plantillas de email** (FreeMarker), a petición explícita del usuario. Expone el contrato que consume [`../front/fase6-1-front.md`](../front/fase6-1-front.md).

## Contexto y objetivo

Dos endpoints públicos nuevos bajo `/api/auth/**` (ya cubiertos por el wildcard público existente, `Wuris.Auth.PUBLIC_PATTERN`, sin tocar `SecurityConfig`): uno para solicitar la recuperación (recibe un email, genera un token de un solo uso y manda un correo con un enlace), y otro para consumir ese token y fijar la contraseña nueva. El correo se construye con una plantilla FreeMarker, replicando el patrón ya usado en otro proyecto del equipo (`realdoohprojectapi`, `spring-boot-starter-freemarker` + `Configuration`/`Template`/`FreeMarkerTemplateUtils.processTemplateIntoString`), en vez del texto plano concatenado a mano que usa hoy el único email existente del proyecto (`EmailServiceImpl.enviarNomina`).

## No-objetivos

- **No se revocan las sesiones (JWT) activas al cambiar la contraseña.** El proyecto usa JWT sin estado y sin lista de revocación en absoluto (ningún módulo la tiene hoy); un JWT ya emitido sigue siendo válido hasta su expiración natural (8h, `app.jwt.expiration-ms`) aunque el usuario cambie su contraseña por este flujo. Construir revocación de sesiones es un cambio de infraestructura mucho mayor, no pedido y fuera de alcance de este ticket.
- ~~Sin límite de frecuencia (rate limiting) de solicitudes de recuperación por email/IP~~ **Revertido en revisión de código posterior**: sí se implementó un cooldown de 60 segundos por email (`SolicitudRecuperacionDemasiadoFrecuenteException`, 429) — el endpoint es público y sin autenticación, así que sin límite cualquiera podía spamear de correos a un empleado concreto o agotar la cuota/reputación de la cuenta SMTP. Mitigación barata (mapa en memoria, sin infraestructura nueva) que no justificaba esperar a "una fase de seguridad aparte". Ver "Enfoque técnico" y "Decisiones técnicas".
- ~~No se migra el envío de nóminas a FreeMarker en esta fase~~ **Revertido**: sí se migró, en la misma entrega, a petición del usuario (`template_nomina.ftl`, mismo mecanismo de remitente configurable que la recuperación de contraseña) — ver "Enfoque técnico".
- **Sin pantalla de administración de tokens de recuperación** (listarlos, revocarlos a mano desde el panel de Admin) — no solicitado.

## Análisis y supuestos

- **Duración de expiración del token: 30 minutos por defecto, configurable por variable de entorno.** No confirmado explícitamente por el usuario — se asume un valor estándar de la industria para este tipo de flujo (recuperación de contraseña), fácil de ajustar sin tocar código si se decide otro valor.
- **El envío de este correo no depende del flag `nominas.enabled`** (ese nombre es específico de Nóminas, `application.properties`): se asume que el envío de recuperación funciona siempre que `spring.mail.*` esté configurado, igual en local (MailDev, `application-local.properties`) que en producción (SMTP real, ya usado hoy para nóminas).
- ~~El mensaje de error cuando el email no existe es específico~~ **Revertido en revisión de código de seguridad, antes de producción**: `solicitar()` responde siempre 204, exista o no el email — el trade-off de enumeración de emails aceptado inicialmente (ver la versión anterior de esta misma línea) se reconsideró al aplicar el estándar de la industria (OWASP ASVS: no revelar si una cuenta existe desde un endpoint público sin autenticación). El cambio queda acotado a `RecuperacionPasswordService.solicitar` (un `Optional.ifPresent(...)` en vez de `orElseThrow`), sin tocar el resto del diseño — ver "Riesgos y dependencias".
- **Solo empleados activos (`activo = true`) pueden recuperar su contraseña**, mismo criterio ya aplicado en `AuthService.login` (`.filter(Empleado::isActivo)`) — un empleado dado de baja no debe poder generarse un enlace de acceso nuevo.
- ~~Mínimo de 6 caracteres para la contraseña nueva~~ **Reforzado en revisión de código posterior**: `RestablecerPasswordRequestDTO.password` exige la misma política ya validada en el frontend (mínimo 8 caracteres, con mayúscula, minúscula y dígito — `@Pattern`, no solo `@Size`). A diferencia de `EmpleadoCreateDTO.password` (que se queda en mínimo 6, porque lo crea un Admin autenticado), este endpoint es público y autoservicio: el backend debe imponer la política fuerte como última línea de defensa, no confiar solo en la validación del frontend.

## Enfoque técnico

1. `POST /api/auth/recuperacion/solicitar {email}`: aplica primero un cooldown de 60s por email (ver punto 4); busca el empleado activo por email — **si no existe, no lanza nada** (`Optional.ifPresent(...)`, ver "Análisis y supuestos"), responde 204 igual que si existiera; si existe, genera un token aleatorio (`SecureRandom`, suficiente entropía), calcula su SHA-256, borra los tokens anteriores no usados de ese empleado, guarda el nuevo con expiración `now + duración configurable`, y envía el email con el token **en claro** dentro del enlace (el hash es lo único que se persiste).
2. `POST /api/auth/recuperacion/restablecer {token, password}`: calcula el hash del token recibido, lo busca; si no existe, ya está usado, o ha caducado, 400; si es válido, delega en `EmpleadoService.resetearPassword(empleado.getId(), password)` (reutiliza el `passwordEncoder.encode(...)` + `save` ya existentes, sin duplicarlos) y marca el token como usado (`fechaUso = now`).
3. El email se renderiza con una plantilla FreeMarker nueva (`template_recuperacion_password.ftl`, en `GestionRestauranteBack/src/main/resources/email/`, mismo directorio que usa el proyecto de referencia) y se envía como HTML vía `MimeMessageHelper` (`helper.setText(html, true)`), mismo `JavaMailSender` que ya usa Nóminas. **Añadido en la misma entrega**: `EmailServiceImpl.enviarNomina` se migró también a una plantilla FreeMarker propia (`template_nomina.ftl`, con el mes/año y un mensaje breve), sustituyendo el texto plano concatenado a mano que tenía antes — el no-objetivo original de dejarlo intacto se revirtió a petición del usuario.
4. **Añadido en revisión de código posterior — cooldown anti-spam**: `RecuperacionPasswordService` guarda un `Map<String, Instant> ultimaSolicitudPorEmail` (`ConcurrentHashMap`, en memoria — mismo supuesto de una sola instancia ya documentado para `ConexionGoogleService.estadosPendientes`); si la última solicitud para ese email fue hace menos de 60s, lanza `SolicitudRecuperacionDemasiadoFrecuenteException` (429) antes de tocar la BBDD o el email. Se aplica **antes** de comprobar si el email existe, para no crear una vía de enumeración alternativa por temporización.
5. **Añadido en revisión de código posterior — remitente configurable reutilizado**: tanto `enviarRecuperacionPassword` como `enviarNomina` llaman a `aplicarRemitente(helper)` (`EmailServiceImpl`, privado): usa `ConfiguracionEmpresa.emailRemitente` (nuevo campo que amplía `back/fase5-1-back.md`, ver ese documento y `bbdd/fase6-1-bbdd.md` — migración `V41`) si está configurado, o cae a `spring.mail.username` si no. Si ninguno de los dos está informado, se envía sin cabecera `From` explícita y se deja un `log.warn` (para poder diagnosticar por qué un correo sale "sin remitente").
6. **Gotcha de FreeMarker con locale español**: interpolar directamente un número (`${periodoAnio}`, `${expiracionMinutos}`) aplica el formato numérico del locale configurado — en español, `2026` se renderiza como `2.026` (separador de miles). Ambas plantillas usan `?c` (`${periodoAnio?c}`, `${expiracionMinutos?c}`) para forzar el formato "de ordenador" (sin agrupación), evitando el bug. Se detectó escribiendo un test de plantillas (`EmailTemplatesTest`, ver "Estrategia de pruebas") — sin ese test habría llegado a producción.

## Alternativas consideradas

- **JWT autocontenido como token de recuperación (sin tabla en BBDD)**: descartada — no permite invalidarlo antes de su expiración natural ni marcarlo "ya usado" sin añadir de todos modos algún almacén de estado, con lo que se perdería la única ventaja (no tocar BBDD) sin ganar nada a cambio. Una tabla con hash + expiración + fecha de uso es más simple y sí cubre "un solo uso" de verdad.
- **Thymeleaf en vez de FreeMarker**: descartada por decisión explícita del usuario — quiere replicar el patrón ya usado en `realdoohprojectapi` (`spring-boot-starter-freemarker`, plantillas `.ftl` en `src/main/resources/email/`), no introducir un motor distinto al que ya conoce el equipo.
- **Mensaje de error específico vs. genérico cuando el email no existe**: decisión explícita del usuario (mensaje específico) — la alternativa más segura (mensaje de éxito siempre, sin revelar si el email existe) queda descartada a petición suya, con el riesgo documentado abajo.

## División en entregas

- **Entrega 1 (esta, back + BBDD)**: migración `V40`, entidad/repositorio/servicio, controller, plantilla FreeMarker, dependencia nueva en `pom.xml`, tests de backend. Se puede probar de punta a punta con `curl`/Postman + MailDev sin depender del front.
- **Entrega 2** ([`front/fase6-1-front.md`](../front/fase6-1-front.md)): modal en login, vista nueva de restablecimiento, rutas públicas, i18n. Depende del contrato de esta entrega (rutas y DTOs), pero puede maquetarse en paralelo mockeando `AuthService`.

Mismo criterio de división ya usado en todas las fases anteriores del proyecto (BBDD+Backend como una entrega, Frontend como la siguiente, revisable por separado).

## Desglose de subtareas

### Modelo
1. **[Backend, baja]** Entidad `TokenRecuperacionPassword`: `id` (`@GeneratedValue(IDENTITY)`), `empleado` (`@ManyToOne(LAZY)`), `tokenHash`, `fechaCreacion`, `fechaExpiracion`, `fechaUso` (nullable).
2. **[Backend, baja]** `TokenRecuperacionPasswordRepository extends JpaRepository<TokenRecuperacionPassword, Long>`: `findByTokenHash(String): Optional<TokenRecuperacionPassword>`; `List<TokenRecuperacionPassword> findByEmpleado_IdAndFechaUsoIsNull(Long)` (para borrar los anteriores no usados al pedir uno nuevo).
3. **[Backend, baja]** `EmpleadoRepository`: nuevo método `findByEmailAndActivoTrue(String email): Optional<Empleado>` (mismo patrón que `findByDniNieAndActivoTrue` ya existente).

### Servicio
4. **[Backend, alta]** `RecuperacionPasswordService`:
   - `solicitar(String email)`: `empleadoRepository.findByEmailAndActivoTrue(email)`; si vacío, `EmpleadoNotFoundException` (nuevo constructor por email, ver punto 8); si existe: genera el token (`SecureRandom` de 32 bytes, codificado en Base64 URL-safe sin padding), calcula `SHA-256` hex del token, borra (`tokenRepository.deleteAll(...)`) los tokens no usados del empleado, guarda uno nuevo con `fechaExpiracion = now().plusMinutes(duracionMinutos)`, y llama a `emailService.enviarRecuperacionPassword(empleado, tokenEnClaro)`.
   - `restablecer(String tokenEnClaro, String nuevaPassword)`: calcula el hash del token recibido, `tokenRepository.findByTokenHash(hash)`; si vacío, ya usado (`fechaUso != null`) o caducado (`fechaExpiracion.isBefore(now())`), lanza `TokenRecuperacionInvalidoException`; si válido, `empleadoService.resetearPassword(token.getEmpleado().getId(), nuevaPassword)` y `token.setFechaUso(now())`.
5. **[Backend, media]** `EmailService`: nuevo método `enviarRecuperacionPassword(Empleado destinatario, String token)`. `EmailServiceImpl`: inyecta `freemarker.template.Configuration freemarkerConfig`; `freemarkerConfig.setClassForTemplateLoading(this.getClass(), "/email/")`; `Template t = freemarkerConfig.getTemplate("template_recuperacion_password.ftl")`; modelo `Map.of("nombre", destinatario.getNombre(), "enlace", construirEnlace(token))`; `String html = FreeMarkerTemplateUtils.processTemplateIntoString(t, modelo)`; `helper.setText(html, true)`. El enlace se construye como `${app.front.base-url}/restablecer-contrasena?token=` + `URLEncoder.encode(token, UTF_8)` — reutiliza `app.front.base-url` ya existente (mismo property usado hoy para el redirect OAuth de Google Business, `back/fase4-1-back.md`).
6. **[Backend, baja]** Plantilla nueva `GestionRestauranteBack/src/main/resources/email/template_recuperacion_password.ftl`: HTML simple (sin dependencias externas de imágenes/fuentes remotas, a diferencia del ejemplo de `realdoohprojectapi` que carga una fuente de Google Fonts) con: saludo con `${nombre}`, un botón/enlace a `${enlace}`, y un aviso de que el enlace caduca en `${duracionMinutos}` minutos.

### Controller
7. **[Backend, baja]** Nuevas rutas en `Wuris.Auth`: `RECUPERACION_SOLICITAR = BASE + "/recuperacion/solicitar"`, `RECUPERACION_RESTABLECER = BASE + "/recuperacion/restablecer"` (ambas ya públicas por el wildcard `PUBLIC_PATTERN = BASE + "/**"` existente — sin cambios en `SecurityConfig`).
8. **[Backend, media]** `RecuperacionPasswordController` (o ampliar `AuthController` existente — a decidir en implementación, ambas opciones son razonables dado que solo hay dos métodos nuevos):

   | Verbo | Ruta | DTO request | Respuesta |
   |---|---|---|---|
   | POST | `/api/auth/recuperacion/solicitar` | `SolicitarRecuperacionRequestDTO {@NotBlank @Email String email}` | 204 (siempre, exista o no el email) |
   | POST | `/api/auth/recuperacion/restablecer` | `RestablecerPasswordRequestDTO {@NotBlank String token, @NotBlank @Pattern(...) String password}` | 204 |

   ~~`@Size(min=6)`~~ **Reforzado tras revisión de código**: `password` valida con `@Pattern` (mínimo 8, mayúscula+minúscula+dígito), no solo longitud — ver "Análisis y supuestos".

   Ningún endpoint lleva `@PreAuthorize` (son públicos por definición, igual que `/api/auth/login`).
9. ~~Nuevo constructor en `EmpleadoNotFoundException`: `EmpleadoNotFoundException(String email)`~~ **Eliminado tras revisión de código**: al pasar `solicitar()` a responder siempre 204 (ver "Análisis y supuestos"), este constructor dejó de usarse y se retiró (sin dejar código muerto) — el email inexistente ya no lanza ninguna excepción, simplemente no hace nada (`Optional.ifPresent(...)`).
10. **[Backend, baja]** Nueva excepción `TokenRecuperacionInvalidoException` (400) → `"El enlace de recuperación no es válido o ha caducado"`, añadida al grupo de `GlobalExceptionHandler` que maneja `EstadoOAuthInvalidoException` (mismo tipo de caso: un token/estado de un flujo que ya no es válido; el método del handler se renombró a `handleEstadoOTokenInvalido` para reflejar que ya no es solo OAuth).
11. **[Backend, media] Añadido en revisión de código posterior** — dos excepciones nuevas:
    - `SolicitudRecuperacionDemasiadoFrecuenteException` (429, `TOO_MANY_REQUESTS`, handler propio en `GlobalExceptionHandler`) — cooldown de 60s por email (ver "Enfoque técnico", punto 4).
    - `EnvioRecuperacionPasswordFallidoException` (502) — envuelve un fallo de `JavaMailSender` (SMTP caído, credenciales inválidas) con un mensaje claro, añadida al mismo grupo que ya maneja `EnvioNominaFallidoException`; sin esto, un fallo de envío caía en el `500` genérico de `handleGeneric`.

### Configuración y dependencias
12. **[Backend, baja]** Nueva dependencia en `pom.xml`: `spring-boot-starter-freemarker`.
13. **[Backend, baja]** Nueva config en `application.properties`: `app.recuperacion-password.expiracion-minutos=${RECUPERACION_PASSWORD_EXPIRACION_MINUTOS:30}`.

### Transversal
14. **[Backend, media]** Tests unitarios de `RecuperacionPasswordService` (con `EmpleadoRepository`/`TokenRecuperacionPasswordRepository`/`EmailService`/`EmpleadoService` mockeados, nunca se envía un email real en tests): ~~`solicitar` con email inexistente lanza `EmpleadoNotFoundException`~~ **actualizado**: `solicitar` con email inexistente no lanza nada ni llama a `EmailService` (204 silencioso); `solicitar` con éxito borra los tokens anteriores no usados del empleado antes de guardar el nuevo; el `tokenHash` guardado nunca coincide con el token en claro que recibe `EmailService` (verifica que de verdad se hashea antes de persistir); `restablecer` con un token caducado o ya usado lanza `TokenRecuperacionInvalidoException` sin llamar a `EmpleadoService.resetearPassword`; `restablecer` con éxito marca `fechaUso` y delega correctamente en `EmpleadoService.resetearPassword`. **Añadidos en revisión de código posterior**: una segunda solicitud para el mismo email dentro del cooldown lanza `SolicitudRecuperacionDemasiadoFrecuenteException` sin generar un segundo token; un fallo de `EmailService.enviarRecuperacionPassword` (mockeado con `MailSendException`) se traduce en `EnvioRecuperacionPasswordFallidoException`.
15. **[Backend, baja]** Tests `@WebMvcTest`: ambos endpoints validan sus DTOs (`email` vacío/con formato inválido → 400; ~~`password` de menos de 6 caracteres~~ **actualizado**: `password` demasiado corta o sin mayúscula/dígito → 400) y responden sin necesitar ningún token JWT (excluyendo `JwtAuthFilter` como ya hace `ConfiguracionControllerTest`, u omitiendo `.principal(...)` directamente, ya que estas rutas son públicas de verdad).
16. **[Backend, baja] Añadido en revisión de código posterior**: `EmailTemplatesTest` — carga ambas plantillas (`template_nomina.ftl`, `template_recuperacion_password.ftl`) con un `freemarker.template.Configuration` real (sin mocks, sin contexto de Spring) y un modelo equivalente al que construye `EmailServiceImpl`, y comprueba que el HTML resultante contiene los valores esperados. Detectó el bug de formato numérico con locale español (ver "Enfoque técnico", punto 6) — sin este test, un typo en una clave del modelo o un problema de formato solo se habría visto en el primer envío real.

## Decisiones técnicas

- **Token de un solo uso respaldado por tabla, no JWT autocontenido** (ya justificado en "Alternativas consideradas") — permite invalidación real antes de la expiración natural.
- **Hash SHA-256 del token, no cifrado reversible**: solo se necesita comparar por igualdad al restablecer, nunca recuperar el valor en claro — mismo razonamiento ya aplicado a la huella de VeriFactu (si se llega a implementar), distinto del cifrado AES de `refresh_token_cifrado` (que sí necesita descifrarse para reutilizarse contra la API de Google).
- **Se reutiliza `EmpleadoService.resetearPassword` en vez de duplicar `passwordEncoder.encode(...)`+`save`**: es exactamente la misma operación (fijar una contraseña nueva ya validada), solo cambia quién la autoriza (un Admin con JWT+permiso vs. un token de recuperación válido) — la autorización se resuelve antes de llegar a ese método, no dentro de él.
- **FreeMarker en vez de construir el HTML como un `String` a mano**: a diferencia del único precedente del proyecto (`EmailServiceImpl.enviarNomina`, texto plano concatenado), aquí se introduce un motor de plantillas real por decisión explícita del usuario, replicando el patrón ya usado en `realdoohprojectapi` — deja la puerta abierta a reutilizar el mismo mecanismo para futuros correos con formato más elaborado (p. ej. migrar nóminas más adelante), aunque esta fase no lo haga.
- **El controller no lleva ningún `@PreAuthorize`**: ambos endpoints son, por definición, accesibles sin haber iniciado sesión — es la tercera vez que el proyecto tiene un endpoint público fuera de la autenticación por defecto (junto a `/api/auth/login` y el callback OAuth de Google Business), y como los otros dos, no requiere ningún cambio en `SecurityConfig` porque ya cae dentro del wildcard `Wuris.Auth.PUBLIC_PATTERN`.
- **Añadido en revisión de código posterior — cooldown en `ConcurrentHashMap`, no una librería de rate limiting**: se descartó introducir una dependencia nueva (p. ej. Bucket4j) para un caso tan acotado (un cooldown simple por email); se reutilizó el mismo patrón ya establecido en el proyecto para estado efímero en memoria (`ConexionGoogleService.estadosPendientes`, `back/fase4-1-back.md`), con el mismo supuesto de despliegue de una sola instancia ya documentado en `plan-implementacion-general.md`.

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase6-1-bbdd.md`: sin la tabla, `RecuperacionPasswordService` no tiene dónde persistir nada.
- **Dependencia dura** con `EmpleadoService.resetearPassword` (`back/fase1-4-back.md`) y con `EmailService`/`spring-boot-starter-mail` ya configurado.
- ~~Riesgo medio — enumeración de emails~~ **Resuelto tras revisión de código, antes de producción**: `solicitar()` responde siempre 204, exista o no el email — ya no es posible enumerar emails registrados probando contra este endpoint. El cambio quedó acotado a `RecuperacionPasswordService.solicitar` exactamente como se previó aquí, sin tocar el resto del diseño.
- **Riesgo medio — spam/abuso del endpoint público `solicitar`, mitigado con cooldown**: sin límite de frecuencia, cualquiera podía golpear el endpoint sin autenticación para spamear de correos a un empleado concreto o agotar la cuota/reputación de la cuenta SMTP. Mitigado con un cooldown de 60s por email en memoria (`ConcurrentHashMap`, ver "Enfoque técnico") — no protege contra un atacante distribuido (multitud de IPs), pero sí contra el caso más obvio de spam a una única víctima o script sin cuidado.
- **Riesgo medio — compatibilidad de `spring-boot-starter-freemarker` con Spring Boot 4.1.1**: esta versión de Spring Boot es muy reciente y ya se ha visto en este mismo proyecto que otras autoconfiguraciones cambiaron de paquete respecto a versiones anteriores (p. ej. `org.springframework.boot.hibernate.autoconfigure.HibernateJpaConfiguration`, `org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration`) — verificar al implementar que la autoconfiguración de FreeMarker sigue funcionando igual (el bean `freemarker.template.Configuration` debería seguir autoconfigurándose al añadir el starter, pero conviene confirmarlo pronto, no al final).
- **Riesgo bajo**: JWTs ya emitidos siguen siendo válidos hasta su expiración natural (8h) tras un cambio de contraseña por este flujo — limitación conocida de la infraestructura JWT stateless ya existente en todo el proyecto, no introducida por esta fase (ver "No-objetivos").
- **Riesgo bajo**: tokens caducados o ya usados se acumulan en la tabla sin purga automática — aceptable al volumen actual del proyecto.

## Aspectos transversales

- **Seguridad**: contraseñas siguen hasheadas con BCrypt (sin cambios); el token de recuperación se guarda como hash SHA-256, nunca en claro, con expiración corta (30 min por defecto) y de un solo uso; el endpoint `solicitar` no revela si un email existe (204 siempre) y tiene un cooldown de 60s por email — ver "Riesgos y dependencias".
- **Privacidad**: el nombre y el email del empleado son los únicos datos personales que viajan en el correo; la contraseña nunca se incluye en ningún email.
- **Internacionalización**: la plantilla FreeMarker se escribe solo en español, igual que el único email existente del proyecto (nóminas) — no se traduce a otros idiomas en esta fase, a diferencia del front, que sí lleva sus textos por i18n (ES/EN) como el resto de la app.
- **Rendimiento**: sin impacto relevante — una fila más por solicitud en una tabla pequeña, sin consultas costosas.
- **Observabilidad**: log a nivel INFO cuando se solicita y cuando se consume un token (identificando al empleado, nunca el token en claro ni su hash completo), mismo criterio ya aplicado en `EmailServiceImpl` (`log.info("nominas.enabled=false: se omite...")`).

## Estrategia de pruebas

Ver puntos 13-14 del desglose. Casos borde cubiertos: email inexistente/inactivo, token inexistente, token ya usado, token caducado, dos solicitudes seguidas del mismo empleado (la segunda invalida la primera). No se envía ningún email real en los tests (mismo criterio ya aplicado a Tesseract/Google/AEAT en fases anteriores: los servicios externos siempre se mockean).

## Despliegue y rollback

- Migración Flyway `V40` puramente aditiva (tabla nueva, sin tocar datos existentes) — revertir es trivial (`DROP TABLE tokens_recuperacion_password`, sin ningún backfill que deshacer).
- La nueva dependencia `spring-boot-starter-freemarker` exige recompilar el backend; sin pasos de despliegue especiales más allá del ciclo normal (relanzar backend tras aplicar la migración).
- Variable de entorno nueva `RECUPERACION_PASSWORD_EXPIRACION_MINUTOS`, opcional (con valor por defecto de 30), no bloquea el arranque si falta.

## Criterios de aceptación técnicos

- ~~`POST /api/auth/recuperacion/solicitar` con un email que no existe (o de un empleado inactivo) devuelve 404 con el mensaje específico, sin generar ningún token~~ **Actualizado**: devuelve 204 igual que con un email existente, sin generar ningún token ni enviar ningún email.
- `POST /api/auth/recuperacion/solicitar` con un email válido genera un token, lo guarda hasheado (nunca en claro) y envía un correo con la plantilla FreeMarker con un enlace a `${app.front.base-url}/restablecer-contrasena?token=...`.
- Pedir una segunda recuperación para el mismo empleado invalida (borra) el token de la primera solicitud.
- **Nuevo**: pedir una segunda recuperación para el mismo email antes de 60s devuelve 429, sin tocar la BBDD ni enviar un segundo email.
- `POST /api/auth/recuperacion/restablecer` con un token válido cambia la contraseña (verificable con `AuthService.login` usando la nueva contraseña) y marca el token como usado.
- Reutilizar el mismo token una segunda vez, o usar uno caducado, devuelve 400 sin modificar la contraseña.
- **Nuevo**: `POST /api/auth/recuperacion/restablecer` con una contraseña de menos de 8 caracteres, o sin mayúscula/minúscula/dígito, devuelve 400.
- Ninguno de los dos endpoints exige un JWT.

## Suposiciones

- Duración de expiración de 30 minutos por defecto — no confirmada explícitamente por el usuario, asumida como valor estándar de la industria para este tipo de flujo, configurable.
- El envío de este correo no depende de ningún flag de activación específico (a diferencia de `nominas.enabled`) — se asume que basta con que `spring.mail.*` esté configurado.
