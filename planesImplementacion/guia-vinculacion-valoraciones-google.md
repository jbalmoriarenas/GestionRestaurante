# Vinculación de Valoraciones con Google Business Profile

Sep 25, 2026 · @Jorge

## Qué es esta guía y a quién va dirigida

Esta guía explica cómo conectar la cuenta de Google Business Profile del restaurante con la funcionalidad de Valoraciones ya implementada en la aplicación, para que las reseñas de Google se sincronicen automáticamente (cada 6h, o al pulsar "Sincronizar ahora") en la pantalla `/valoraciones`.

Va dirigida a quien tenga acceso de Admin en la app y, a la vez, acceso a la cuenta de Google que gestiona la ficha del restaurante en Google Maps/Búsqueda (o pueda coordinarse con quien la tenga), y a quien despliegue/configure el backend.

## Requisitos previos

- Acceso a la cuenta de Google (Gmail) que es propietaria o gestora de la ficha del restaurante en Google Business Profile — no vale cualquier cuenta corporativa, tiene que ser la que aparece en [business.google.com](https://business.google.com) con acceso a esa ubicación.
- Un usuario Admin de la aplicación (permiso `VALORACIONES:EDICION`, ya asignado por defecto al rol ADMIN).
- Acceso para configurar variables de entorno y reiniciar el backend.
- El backend desplegado y accesible en una URL fija (hace falta para registrar la URL de callback en Google).

## Paso 1 — Proyecto en Google Cloud y acceso a la API

1. Entra en [Google Cloud Console](https://console.cloud.google.com/) y crea un proyecto (o usa uno existente) para el restaurante.
2. Habilita estas APIs (APIs y servicios → Biblioteca):
   - **My Business Account Management API** y **My Business Business Information API** — para descubrir la cuenta/ubicación al conectar.
   - **Google My Business API (v4)** — la única superficie que expone reseñas; **requiere solicitar acceso manualmente a Google, y la aprobación puede tardar varios días**.
3. Planifica esto con antelación: sin esa aprobación, la conexión falla al intentar listar reseñas aunque el resto del flujo OAuth funcione correctamente.

## Paso 2 — Credenciales OAuth 2.0

1. En **APIs y servicios → Credenciales → Crear credenciales → ID de cliente de OAuth**, elige tipo **Aplicación web**.
2. Añade como **URI de redirección autorizada** exactamente: `https://<dominio-del-backend>/api/valoraciones/conexion/callback` (en local: `http://localhost:8080/api/valoraciones/conexion/callback`).
3. Configura la **pantalla de consentimiento OAuth** con el scope `https://www.googleapis.com/auth/business.manage`.
4. **Importante**: mientras la app esté en estado "Prueba" (Testing) en la pantalla de consentimiento, el refresh token que devuelve Google caduca a los 7 días — la conexión se rompería sola cada semana. Para un uso real hay que publicar la app (puede requerir verificación de Google al ser un scope sensible). Mientras tanto, añade la cuenta de Google del Requisito previo como "usuario de prueba".
5. Guarda el **Client ID** y el **Client Secret**.

## Paso 3 — Configurar el backend

1. Genera una clave AES-256 en Base64 para cifrar el refresh token en reposo: `openssl rand -base64 32`.
2. Define estas variables de entorno antes de arrancar el backend (no tienen valor por defecto: el arranque falla si faltan):
   - `GOOGLE_CLIENT_ID`
   - `GOOGLE_CLIENT_SECRET`
   - `GOOGLE_REDIRECT_URI` (debe coincidir carácter a carácter con la registrada en Google)
   - `GOOGLE_TOKEN_ENCRYPTION_KEY` (la clave generada arriba)
   - Opcionales: `VALORACIONES_SYNC_CRON` (por defecto cada 6h) y `FRONT_BASE_URL` (para construir la redirección tras conectar)
3. Reinicia el backend con esas variables aplicadas.

## Paso 4 — Conectar desde la aplicación

1. Entra en la app con un usuario Admin y ve al menú **Valoraciones**.
2. Pulsa **Conectar con Google**.
3. En la pantalla de Google, inicia sesión **con la cuenta de Google del Requisito previo** (la que gestiona la ficha del restaurante) — si te equivocas de cuenta, la conexión puede completarse pero sin encontrar ninguna ubicación, o encontrar la ubicación equivocada.
4. Concede el permiso solicitado ("Gestionar tu perfil de negocio en Google").
5. Google redirige de vuelta a `/valoraciones`. Si la cuenta gestiona más de una ubicación, la app conecta automáticamente la primera que devuelve Google — si el restaurante tiene varias ubicaciones bajo la misma cuenta, confírmalo antes de conectar para evitar sincronizar la ubicación equivocada.
6. Nada más conectar se lanza una primera sincronización inmediata; luego se repite sola según `VALORACIONES_SYNC_CRON`.

## Mantenimiento

- **Desconectar** revoca el acceso en Google y borra la conexión local; las reseñas ya sincronizadas no se borran.
- Si la pantalla de consentimiento de Google sigue en modo "Prueba" (ver Paso 2), la conexión dejará de funcionar sola pasados 7 días — se verá como un error en la tarjeta de conexión. Solución: publicar la app en Google Cloud Console, o reconectar manualmente cada vez que caduque.
- Si alguien revoca el acceso desde la propia cuenta de Google (Seguridad → Aplicaciones de terceros), hay que volver a pulsar "Conectar con Google" en la app.

## Problemas comunes

| Síntoma | Causa probable |
| --- | --- |
| Google devuelve error `redirect_uri_mismatch` | `GOOGLE_REDIRECT_URI` no coincide exactamente (protocolo, dominio o barra final) con la URI registrada en Google Cloud Console |
| El backend no arranca: `Could not resolve placeholder 'GOOGLE_CLIENT_ID'` | Faltan variables de entorno del Paso 3 |
| Tras conectar, la tarjeta muestra "Error: ..." | Revisa si la API de reseñas (My Business API v4) ya tiene el acceso aprobado por Google (Paso 1), o si el refresh token caducó (app en modo Prueba, ver Mantenimiento) |
| Vuelve a `/valoraciones?error=access_denied` | El usuario canceló el consentimiento en la pantalla de Google |
| Se conecta pero no aparece ninguna reseña | La cuenta de Google usada no gestiona ninguna ubicación de Google Business Profile, o la ubicación conectada no es la correcta (ver Paso 4) |
