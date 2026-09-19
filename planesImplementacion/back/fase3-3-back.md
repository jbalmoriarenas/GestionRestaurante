# Plan de Implementación — Fase 3.3 (Backend): Nóminas — envío por email

Reutiliza `Nomina`/`NominaService`/`FileStorageService` de [`fase3-1-back.md`](./fase3-1-back.md), sin migraciones nuevas sobre [`../bbdd/fase3-1-bbdd.md`](../bbdd/fase3-1-bbdd.md) (esa tabla ya tiene `fecha_envio`/`enviada_por_admin_id`). Añade `spring-boot-starter-mail` — primera vez que el proyecto envía email, sin ningún precedente de `JavaMailSender`/`EmailService` en el código existente. Expone el contrato que consume [`../front/fase3-3-front.md`](../front/fase3-3-front.md).

## Contexto y objetivo

Endpoint para que el Admin envíe por email, al correo del propio empleado, la nómina ya adjuntada de un periodo — con confirmación explícita del Admin (nunca automático al subir el archivo, ver `plan-implementacion-general.md`), dejando registro de cuándo y quién la envió.

## Análisis y supuestos

- **Envío síncrono en esta fase, sin `@Async`**: mismo criterio de "sin infraestructura extra" ya aplicado en el resto del proyecto — un envío individual disparado por una acción explícita del Admin no necesita una cola ni un hilo aparte. Si el SMTP configurado es lento, el request tarda más, pero no hay una segunda pieza de infraestructura (cola, worker) que desplegar y mantener para el volumen esperado (envíos manuales, uno a uno, de una plantilla de restaurante).
- **Solo tiene sentido enviar una nómina que ya tiene archivo** (`ADJUNTADA` o `ENVIADA` — nunca `PENDIENTE`, que ni siquiera tiene fila en `nominas`, ver `fase3-1-bbdd.md`). **Reenviar una nómina ya `ENVIADA` está permitido** (p. ej. el empleado dice que no le llegó, o se le envía de nuevo tras corregir el email registrado): se reenvía el mismo archivo y se actualiza `fechaEnvio`/`enviadaPorAdminId` a los del nuevo envío — no existe un estado "REENVIADA" distinto, sería complejidad sin beneficio real para lo que se pide.
- **Credenciales y configuración SMTP siempre vía variables de entorno** (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`), nunca hardcodeadas en `application.properties` — mismo patrón ya usado para `DB_USERNAME`/`DB_PASSWORD` en el arranque del backend.
- **Un fallo de envío nunca deja el estado a medias**: si `JavaMailSender` lanza una excepción, la nómina permanece exactamente en el estado que tenía antes de intentar el envío (no se marca `ENVIADA` ni se actualizan las columnas de auditoría) — el Admin ve un error claro y puede reintentar sin que el sistema haya registrado un envío que en realidad no llegó.
- **Sin envío masivo en esta fase**: `plan-implementacion-general.md` lo menciona como posible ("botón de envío individual y masivo"), pero no fue parte de lo pedido en esta ronda de planificación — se deja como una extensión natural de `enviar()` a una lista de IDs si se pide más adelante, sin que eso obligue a rediseñar nada de lo construido aquí.
- **Cuerpo del email en texto plano, sin motor de plantillas HTML** (Thymeleaf u otro): no se ha pedido un diseño de email concreto; un texto simple con los datos del periodo basta para el alcance de esta fase, y evita añadir una dependencia de plantillas para un único correo de una línea.

## Desglose de subtareas

1. **[Backend, baja]** Dependencia `spring-boot-starter-mail` en `pom.xml`. Config en `application.properties`: `spring.mail.host=${MAIL_HOST}`, `spring.mail.port=${MAIL_PORT:587}`, `spring.mail.username=${MAIL_USERNAME}`, `spring.mail.password=${MAIL_PASSWORD}`, `spring.mail.properties.mail.smtp.auth=true`, `spring.mail.properties.mail.smtp.starttls.enable=true`.
2. **[Backend, media]** `EmailService` (nuevo, `service/`): `enviarNomina(Empleado destinatario, Resource archivo, String nombreArchivoParaAdjunto, int periodoMes, int periodoAnio): void` — construye un `MimeMessage` vía `JavaMailSender`/`MimeMessageHelper` (`multipart = true` para poder adjuntar), asunto tipo "Tu nómina de {mes}/{año}", cuerpo de texto plano breve, adjunta `archivo` con `nombreArchivoParaAdjunto`. Deja que cualquier excepción de `JavaMailSender` se propague (la traduce `NominaService`, ver subtarea 3).
3. **[Backend, media]** `NominaService.enviar(Long nominaId, Long adminId): NominaResponseDTO` — carga la nómina (`NominaNotFoundException`, 404, si no existe) junto con su `Empleado`; carga el archivo vía `FileStorageService.cargarComoResource(nomina.getRutaArchivo())`; llama `EmailService.enviarNomina(...)`; si lanza una excepción de envío, la envuelve y relanza como `EnvioNominaFallidoException` (para no confundirla con un 400 de validación propio) **sin tocar la fila**; si el envío se completa sin excepción, actualiza `estado = ENVIADA`, `fechaEnvio = now()`, `enviadaPorAdminId = adminId` y guarda.
4. **[Backend, baja]** `POST /api/nominas/{id}/enviar` → `NominaResponseDTO`, `@PreAuthorize("hasAuthority('NOMINAS:EDICION')")` (mismo controller de `fase3-1-back.md`).
5. **[Backend, baja]** Nueva excepción `EnvioNominaFallidoException`, mapeada en `GlobalExceptionHandler` a 502 (Bad Gateway — el fallo es de un servicio externo, el SMTP, no de la petición del cliente).
6. **[Backend, media]** Tests: `JavaMailSender` mockeado (`@MockitoBean`) — enviar una nómina `ADJUNTADA` con éxito la deja `ENVIADA` con `fechaEnvio`/`enviadaPorAdminId` correctos y verifica que se llamó a `send(...)` con el adjunto esperado; si `JavaMailSender.send(...)` lanza una excepción, la nómina conserva su estado anterior y la respuesta es 502; reenviar una nómina ya `ENVIADA` actualiza `fechaEnvio` a un valor más reciente; enviar un `id` inexistente responde 404; token `EMPLEADO` responde 403.

## Decisiones técnicas

- **Texto plano en el cuerpo del email**, no una plantilla HTML: alcance mínimo suficiente, sin añadir un motor de plantillas para un correo de un párrafo.
- **Reenviar una nómina `ENVIADA` está permitido y simplemente actualiza la auditoría**: no se modela un estado distinto para "reenviada", evita una complejidad de estados que no aporta nada de valor de negocio real.
- **Un fallo de envío nunca deja el estado a medias**: la actualización de `estado`/`fechaEnvio`/`enviadaPorAdminId` ocurre **después** de que `EmailService` complete sin excepción, nunca antes ni en paralelo — si el envío falla a mitad, la fila queda exactamente como estaba.
- **Envío síncrono, sin cola ni `@Async`**: coherente con el volumen esperado (envíos manuales, uno a uno); documentado como punto a revisar si en el futuro se añade envío masivo (ver "Análisis y supuestos").

## Riesgos y dependencias

- **Dependencia dura** con `back/fase3-1-back.md`: reutiliza `Nomina`, `NominaService`, `FileStorageService` y el mismo `NominaController` tal cual.
- **Riesgo medio**: sin `@Async`, un SMTP lento o con timeout largo bloquea el hilo de la petición HTTP durante ese tiempo — aceptable al volumen esperado (un restaurante, envíos manuales uno a uno); a revisar si más adelante se pide envío masivo (ver "Análisis y supuestos", no incluido en esta fase).
- **Riesgo bajo**: para probar el envío de extremo a extremo en desarrollo hace falta un servidor SMTP real o de pruebas (p. ej. Mailhog/Mailtrap) — fuera de alcance de este plan, a documentar en las instrucciones de despliegue/entorno local cuando se implemente.

## Criterios de aceptación técnicos

- Enviar una nómina `ADJUNTADA` a un empleado con email válido la deja `ENVIADA`, con `fechaEnvio` y `enviadaPorAdminId` rellenos, y el empleado recibe un correo con el PDF adjunto.
- Reenviar una nómina ya `ENVIADA` vuelve a enviar el correo y actualiza `fechaEnvio`/`enviadaPorAdminId` con los del nuevo envío.
- Si el servidor SMTP falla (o lanza cualquier excepción), la nómina permanece en su estado anterior (no se marca `ENVIADA` con un envío que en realidad no se completó) y la respuesta es 502.
- Enviar una nómina inexistente responde 404.
- Un token `EMPLEADO` recibe 403.
