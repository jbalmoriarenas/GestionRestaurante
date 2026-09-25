# GestionRestauranteBack

Backend de GestionRestaurante, generado con [Spring Initializr](https://start.spring.io) sobre Spring Boot 4 / Spring Framework 7.

## Tecnologías y versiones

| Categoría              | Tecnología                | Versión         |
|------------------------|----------------------------|-----------------|
| Framework              | Spring Boot                | 4.1.1           |
| Framework base         | Spring Framework           | 7.0.9           |
| Lenguaje / plataforma  | Java (JDK)                 | 21 (Eclipse Temurin 21.0.12.1) |
| Build tool             | Maven (wrapper `mvnw`)     | 3.9.16          |
| Persistencia           | Spring Data JPA            | 4.1.1           |
| ORM                    | Hibernate ORM              | 7.4.5.Final     |
| Base de datos          | MySQL (driver JDBC `mysql-connector-j`) | 9.7.0 |
| Migraciones de BBDD    | Flyway (`flyway-mysql`)    | 12.4.0          |
| Servidor embebido      | Tomcat embebido            | 11.0.24         |
| Seguridad / Auth       | Spring Security + JWT (`jjwt`) | 0.13.0     |
| Envío de email         | Spring Boot Mail (Jakarta Mail) | —          |
| OCR de facturas        | Tess4J (Tesseract vía JNI) | 5.13.0          |
| Renderizado/lectura PDF| Apache PDFBox               | 3.0.3           |
| Exportación a Excel    | Apache POI (`poi-ooxml`)    | 5.3.0           |
| Serialización JSON     | Jackson (Jackson 3, `tools.jackson.core`) | 3.1.5 |
| Validación             | Jakarta Bean Validation / Hibernate Validator | 9.1.3.Final |
| Boilerplate            | Lombok                     | 1.18.46         |

El JDK 21 está **embebido en el propio proyecto** (`.jdk/`, no versionado en git) y no depende del Java instalado a nivel de sistema: `mvnw` / `mvnw.cmd` fuerzan automáticamente ese `JAVA_HOME` al ejecutarse desde esta carpeta, para no interferir con otros proyectos que usen versiones de Java más antiguas.

## Starters de Spring Boot incluidos

- `spring-boot-starter-webmvc` — API REST (Spring MVC + Tomcat embebido)
- `spring-boot-starter-data-jpa` — persistencia con Spring Data JPA / Hibernate
- `spring-boot-starter-validation` — validación de beans (Jakarta Validation)
- `spring-boot-starter-security` — autenticación/autorización (JWT propio, sin OAuth2 excepto Google Business Profile de Valoraciones)
- `spring-boot-starter-mail` — envío de nóminas por email (Fase 3.3)
- `spring-boot-starter-flyway` + `flyway-mysql` — migraciones de esquema versionadas (`src/main/resources/db/migration`)
- `com.mysql:mysql-connector-j` — driver JDBC de MySQL (scope `runtime`)
- `org.projectlombok:lombok` — reduce boilerplate (getters/setters/constructores)
- `io.jsonwebtoken:jjwt-*` — generación/validación de tokens JWT
- `net.sourceforge.tess4j:tess4j` + `org.apache.pdfbox:pdfbox` — OCR de facturas (ver más abajo)
- `org.apache.poi:poi-ooxml` — exportación de listados a Excel
- Starters `-test` equivalentes (`webmvc-test`, `data-jpa-test`, `validation-test`) + `spring-security-test` para pruebas

## Configuración del proyecto

- `groupId`: `com.gestionrestaurante`
- `artifactId`: `gestion-restaurante-back`
- `packaging`: `jar`

## Comandos habituales

Compilar:

```bash
./mvnw compile
```

Ejecutar la aplicación:

```bash
./mvnw spring-boot:run
```

Ejecutar tests:

```bash
./mvnw test
```

Generar el `.jar` ejecutable:

```bash
./mvnw package
```

En Windows (PowerShell / cmd) usar `mvnw.cmd` en lugar de `./mvnw`.

## Base de datos

El proyecto usa MySQL. Crea la base de datos ejecutando `scripts/create-database.sql` contra tu servidor MySQL (por ejemplo con `mysql -u root -p < scripts/create-database.sql`), y ajusta usuario/contraseña en `src/main/resources/application.properties` si no son `root`/`root`.

## OCR de facturas (Tesseract, fase 2.3)

`POST /api/facturas/extraer` usa Tess4J (JNI sobre el binario nativo de Tesseract). El binario para Windows/Linux/macOS va empaquetado dentro del propio `.jar` de Tess4J, pero **los datos de idioma (`.traineddata`) no** — sin ellos, la llamada nativa `TessBaseAPI` falla con un error de memoria poco descriptivo (`java.lang.Error: Invalid memory access`) en vez de un mensaje claro.

Antes de usar `/extraer` en local, descarga el paquete de idioma español (y opcionalmente inglés/orientación, aunque solo `spa` es necesario para el idioma configurado por defecto, `app.ocr.idioma=spa`) en una carpeta local y apunta `TESSDATA_PATH` a ella:

```bash
mkdir -p .tessdata
curl -sL -o .tessdata/spa.traineddata https://github.com/tesseract-ocr/tessdata_fast/raw/main/spa.traineddata
TESSDATA_PATH="$(pwd)/.tessdata" DB_USERNAME=gestor DB_PASSWORD=Gestor.2026 ./mvnw spring-boot:run
```

`.tessdata/` no se versiona (ver `.gitignore`); cada entorno (desarrollo y despliegue) necesita descargarlo una vez.

## Valoraciones de Google (fase 4)

Conexión OAuth 2.0 con Google Business Profile para sincronizar las reseñas del restaurante (`/api/valoraciones/**`). A diferencia del resto de secretos del proyecto, `app.google.client-id`, `app.google.client-secret` y `app.google.token-encryption-key` **no tienen valor por defecto**: el arranque falla explícitamente si no están definidos, en vez de arrancar con una clave de ejemplo conocida protegiendo un refresh token real.

1. Crea un proyecto en [Google Cloud Console](https://console.cloud.google.com/), habilita la "Google My Business API" (v4, la única superficie que expone reseñas — requiere solicitud de acceso aprobada manualmente por Google, puede tardar días) y crea unas credenciales OAuth 2.0 de tipo "Aplicación web" con `http://localhost:8080/api/valoraciones/conexion/callback` como URI de redirección autorizada.
2. Genera una clave AES-256 en Base64 para cifrar el refresh token en reposo:
   ```bash
   openssl rand -base64 32
   ```
3. Arranca con las tres variables definidas (además de `DB_USERNAME`/`DB_PASSWORD`):
   ```bash
   GOOGLE_CLIENT_ID=... GOOGLE_CLIENT_SECRET=... GOOGLE_TOKEN_ENCRYPTION_KEY=... \
   DB_USERNAME=gestor DB_PASSWORD=Gestor.2026 ./mvnw spring-boot:run
   ```

Sin conexión activa, la sincronización periódica (`app.valoraciones.sync-cron`, cada 6h por defecto) no hace nada — no es un error, solo no hay nada que sincronizar hasta que un Admin conecte la cuenta desde `/valoraciones` en el front.

## Despliegue en producción

Compilar el `.jar` ejecutable (sin ejecutar tests, para acelerar el build en el servidor):

```bash
./mvnw -DskipTests package
```

Genera `target/gestion-restaurante-back-0.0.1-SNAPSHOT.jar`. Ejecutarlo directamente:

```bash
java -jar target/gestion-restaurante-back-0.0.1-SNAPSHOT.jar
```

Sin el perfil `local` activo (ver `application-local.properties`), el `.jar` arranca con `application.properties` **tal cual está pensado para producción**: `nominas.enabled=false` por defecto, SMTP sin configurar y sin ninguna credencial de Google — hace falta definir, como mínimo, `DB_USERNAME`/`DB_PASSWORD` (obligatorias, sin valor por defecto) y, si se van a usar esas funcionalidades, `MAIL_*`/`GOOGLE_*`/`TESSDATA_PATH` (variables de entorno, o cualquier propiedad de `application.properties` sobreescrita igual por variable de entorno gracias al *relaxed binding* de Spring Boot: `app.jwt.secret` → `APP_JWT_SECRET`, `app.cors.allowed-origin` → `APP_CORS_ALLOWEDORIGIN`).

Guía completa de despliegue en un servidor real (AWS EC2 + dominio + HTTPS, variables de entorno de producción una por una, `systemd`, Nginx como proxy hacia este `.jar`): [`planesImplementacion/guia-despliegue-aws.md`](../planesImplementacion/guia-despliegue-aws.md).

## Recursos adicionales

- [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/maven-plugin)
- [Spring Web (Servlet)](https://docs.spring.io/spring-boot/4.1.1/reference/web/servlet.html)
- [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.1/reference/data/sql.html#data.sql.jpa-and-spring-data)
- [Validation](https://docs.spring.io/spring-boot/4.1.1/reference/io/validation.html)
