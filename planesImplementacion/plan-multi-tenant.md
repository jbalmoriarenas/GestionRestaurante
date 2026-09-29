# Plan de Implementación: Despliegue multi-cliente — servidor y base de datos propios por restaurante

Extiende [`guia-despliegue-aws.md`](./guia-despliegue-aws.md) (que sigue siendo la referencia técnica de "cómo se despliega un servidor", sin cambios de fondo) con el proceso de repetirlo para varios restaurantes distintos, y con el pequeño ajuste de código que lo hace practicable a partir del segundo cliente. Decisiones acordadas explícitamente con el usuario (ver "Análisis del ticket"): un servidor y una base de datos propios por restaurante, alta manual/asistida (no autoservicio), y subdominios de un dominio propio de la empresa (no dominio propio de cada cliente).

## Contexto y objetivo

Hoy la aplicación está desplegada para un único restaurante (Sidrería La Zapica), en un único servidor EC2 con su propia base de datos MySQL local, siguiendo `guia-despliegue-aws.md`. El negocio quiere poder ofrecer la misma aplicación a varios restaurantes distintos, cada uno con sus propios datos, sin que ninguno pueda ver ni afectar a los del otro. Este documento resuelve dos preguntas: qué modelo de aislamiento usar entre restaurantes, y qué proceso concreto sigue el alta de un cliente nuevo.

## No-objetivos

- **Multi-tenencia a nivel de aplicación** (una única base de datos compartida con columna `restaurante_id`, o un único backend sirviendo varios tenants por subdominio): descartada explícitamente para este alcance — ver "Alternativas consideradas".
- **Autoservicio**: no se construye ningún flujo público de alta (un restaurante no puede registrarse solo); el alta la ejecuta el equipo, de forma manual/asistida por un script.
- **Panel de administración de clientes** (un "super-admin" que liste todos los restaurantes dados de alta, facturación, etc.): fuera de alcance mientras el número de clientes sea pequeño; el inventario de clientes de esta fase es un fichero plano, no una funcionalidad de la app.
- **Migración a Docker/contenedores**: se evalúa como alternativa (ver más abajo) y se descarta por ahora — el ajuste real que hace falta (una imagen de máquina "dorada" con los paquetes ya instalados) resuelve el mismo problema sin reescribir la guía de despliegue existente ni introducir la complejidad de contenerizar MySQL con volúmenes persistentes.
- **Balanceo de carga o alta disponibilidad entre servidores**: cada cliente sigue siendo un único servidor EC2, igual que hoy — no cambia con esta fase.

## Stack tecnológico

Sin cambios: Angular (front, build estático) + Spring Boot (backend, `.jar` autocontenido) + MySQL + Nginx + systemd, sobre EC2 (Ubuntu), tal como ya describe `guia-despliegue-aws.md`. Se añade, como artefacto de infraestructura nuevo, una **AMI propia** ("imagen dorada") para acelerar el arranque de cada servidor nuevo.

## Análisis del ticket

Decisiones ya acordadas explícitamente con el usuario para este documento:

- **Modelo de aislamiento**: servidor + base de datos propios por restaurante (no una app ni una BBDD compartidas).
- **Proceso de alta**: manual/asistido — un script/checklist que ejecuta el equipo, no un formulario público.
- **Dominios**: subdominios de un dominio propio de la empresa (p. ej. `<cliente>.<tudominio>`), no que cada restaurante aporte el suyo — más rápido de dar de alta porque el DNS lo controláis vosotros de principio a fin.

Lo que esto implica técnicamente: al ser un servidor por cliente, **cada restaurante es, en la práctica, el mismo despliegue de un solo tenant que ya existe hoy, repetido N veces** — no hace falta que el código sepa que existen otros restaurantes. El trabajo real de esta fase es (a) un ajuste de código que hoy asume implícitamente "solo hay un dominio" y por tanto obligaría a recompilar el front para cada cliente, y (b) las herramientas/documentación para repetir el proceso de alta de forma rápida y consistente, en vez de seguir la guía de 13 pasos a mano cada vez.

## Contexto del repositorio

- **`guia-despliegue-aws.md`** ya documenta la arquitectura de un solo servidor (EC2 + Nginx + systemd + MySQL local, sin Docker, sin balanceador) — sigue siendo la arquitectura correcta *por cliente* en el modelo elegido; este documento no la reemplaza, la reutiliza N veces.
- **`GestionRestauranteFront/src/app/core/api-base-url.ts`**: `export const API_BASE_URL = 'http://localhost:8080/api';` — hoy es una constante **absoluta**, fijada en tiempo de compilación (`guia-despliegue-aws.md`, paso 7, exige editarla y recompilar el front para cada dominio). Con un servidor por cliente y Nginx haciendo de proxy a `/api` en el mismo dominio (`guia-despliegue-aws.md`, paso 8), esta URL puede ser **relativa** (`/api`) sin perder nada — el mismo `dist/` compilado una sola vez sirve para todos los clientes.
- **`GestionRestauranteBack/src/main/resources/application.properties`**: `spring.datasource.url=jdbc:mysql://localhost:3306/gestion_restaurante...` — el nombre de la BBDD (`gestion_restaurante`) está fijo, pero como cada cliente vive en su propio servidor, **no colisiona entre clientes** (cada MySQL local solo tiene una base de datos con ese nombre) — no hace falta parametrizarlo para este modelo.
- **`AdminSeeder`** (`config/AdminSeeder.java`): ya crea el primer `ADMIN` automáticamente "si la tabla `empleados` está vacía" — funciona sin cambios para un cliente nuevo, cada base de datos nueva arranca vacía y se siembra sola.
- **`spring.flyway.enabled=true`** (`application.properties:15`): las migraciones ya se aplican automáticamente contra una BBDD vacía en el primer arranque — dar de alta la BBDD de un cliente nuevo es literalmente `CREATE DATABASE` + arrancar el backend una vez, nada de scripts de esquema a mano (`guia-despliegue-aws.md`, paso 5.3, ya lo dice para el caso de un solo cliente, sigue siendo cierto para cada cliente nuevo).
- **`configuracion_empresa`, `configuracion_ubicacion`, `configuracion_vacaciones`, `configuracion_emisor_fiscal`, `conexion_google_business`** (`ConfiguracionEmpresa`, etc.): todas son tablas de **fila única** (`id = 1`), pensadas siempre para "un solo restaurante por base de datos" — con el modelo elegido, esa asunción pasa de ser una limitación a ser exactamente el modelo real. **Cero cambios de código necesarios en ninguna de ellas.**
- **`SecurityConfig.java:27`** (`app.cors.allowed-origin`) y **`APP_JWT_SECRET`**: ya son configuración por instancia (variable de entorno), listas para tener un valor distinto por cliente sin tocar código.
- **`GOOGLE_REDIRECT_URI`** (`guia-despliegue-aws.md`, paso 11): hoy se asume una única credencial OAuth de Google por despliegue. Un client OAuth de Google admite **varias URIs de redirección autorizadas sobre el mismo client** — se puede reutilizar un único proyecto/credencial de Google Cloud para todos los clientes, añadiendo la URI de cada cliente nuevo (`https://<cliente>.<tudominio>/api/valoraciones/conexion/callback`) a esa lista, en vez de crear un proyecto de Google Cloud nuevo por cada restaurante (ver "Decisiones técnicas").

## Enfoque técnico

1. **Ajuste mínimo de código** (independiente de todo lo demás, beneficia incluso al cliente único de hoy): `API_BASE_URL` pasa a ser relativo, y se añade un proxy de desarrollo de Angular para que `ng serve` en local siga funcionando exactamente igual que hoy (ver "Decisiones técnicas").
2. **AMI dorada**: una imagen de máquina de EC2 con los paquetes del paso 4 de `guia-despliegue-aws.md` (Java 21, MySQL, Nginx, Tesseract+español, Certbot, Node) ya instalados — cada servidor nuevo parte de "lanzar instancia desde esta AMI" en vez de repetir `apt install` a mano.
3. **Artefactos de release, no compilación por servidor**: el `.jar` del backend y el `dist/` del front se compilan **una vez** por versión (en tu máquina o, más adelante, en CI) y se copian a cada servidor — no cada servidor hace su propio `git clone` + `mvn package`/`npm run build`, para garantizar que todos los clientes corren exactamente el mismo código en cada versión.
4. **Script de alta de un cliente nuevo** (`scripts/alta-cliente.sh`, nuevo): automatiza, sobre un servidor ya lanzado desde la AMI dorada, los pasos 5-10 de `guia-despliegue-aws.md` (crear BBDD+usuario, generar `backend.env` con secretos nuevos por cliente, copiar el `.jar`/`dist/` ya compilados, crear los servicios systemd, configurar Nginx con el subdominio del cliente, pedir el certificado Certbot) a partir de un puñado de parámetros (subdominio, y poco más — el resto se genera o se pide de forma interactiva).
5. **Inventario de clientes**: un fichero (`planesImplementacion/clientes.md` o similar, fuera de la app) con una fila por restaurante — subdominio, IP elástica, fecha de alta — suficiente para el volumen de clientes esperado; no una tabla en BBDD ni una pantalla en la app.
6. **Script de actualización multi-cliente** (`scripts/actualizar-todos.sh`, nuevo): recorre el inventario y repite, por SSH, los pasos de "Actualizar la aplicación" (`guia-despliegue-aws.md`, paso 13) en cada servidor — para cuando se publique una versión nueva y haya que llevarla a todos los clientes ya dados de alta, sin hacerlo servidor a servidor a mano.

## Alternativas consideradas

- **Una única app compartida con routing de base de datos por subdominio (`AbstractRoutingDataSource`)**: un solo servidor que mantener en vez de N, pero exige construir y probar a fondo un mecanismo de detección de tenant (filtro que lea el `Host` de cada petición, resuelva a la BBDD correcta) antes de confiar en él para separar datos de clientes distintos — un fallo ahí filtra datos de un restaurante a otro. Se descarta para el volumen de clientes actual ("un puñado"): el coste de mantener N servidores es menor que el riesgo y el esfuerzo de construir bien un mecanismo de aislamiento a nivel de aplicación que hoy no existe en absoluto. Revisable si el número de clientes creciera lo suficiente como para que el coste operativo de N servidores superara al riesgo de esta alternativa.
- **Una única app y una única BBDD compartidas, con columna `restaurante_id` en cada tabla**: la opción más eficiente en recursos a gran escala, pero también la más invasiva — obligaría a añadir esa columna (y su filtro) a *todas* las tablas del proyecto, incluidas las cinco tablas de "fila única" que hoy no la necesitan para nada, y a revisar cada repositorio/endpoint para no filtrar datos entre clientes por error. Descartada por el mismo motivo que la anterior, con más razón (es la alternativa más cara de construir bien de las tres).
- **Migrar el despliegue a Docker/contenedores** (para estandarizar el aprovisionamiento): se descarta por ahora — la AMI dorada resuelve el mismo problema (no repetir la instalación de paquetes a mano) sin tener que contenerizar MySQL (volúmenes persistentes, backups) ni reescribir `guia-despliegue-aws.md` desde cero. Si en el futuro se decide migrar a contenedores por otros motivos (orquestación, más servidores gestionados centralmente), es una fase aparte.
- **Cada restaurante con su propio proyecto Google Cloud/credencial OAuth**: es lo que ya asume `guia-despliegue-aws.md` hoy (pensada para un solo cliente); se descarta como práctica recomendada para N clientes porque añade un paso manual completo (crear proyecto, credenciales, esperar aprobación de la API) por cada alta — se prefiere un único proyecto Google Cloud compartido con varias URIs de redirección autorizadas (ver "Decisiones técnicas"), dejando la opción de un proyecto propio solo para el cliente que explícitamente lo pida (p. ej. por querer su propia cuenta de facturación de Google Cloud).

## División en entregas

1. **Entrega 1 — Ajuste de `API_BASE_URL` + proxy de desarrollo** (frontend, bajo riesgo, independiente del resto): hace el `dist/` reutilizable entre clientes. Puede revisarse y desplegarse ya contra el cliente único actual sin esperar al resto de esta fase.
2. **Entrega 2 — AMI dorada** (infraestructura, sin tocar código de la app): puede construirse en paralelo a la Entrega 1.
3. **Entrega 3 — Script de alta de un cliente nuevo + checklist actualizado** (depende de 1 y 2: necesita el `dist/` ya reutilizable y la AMI ya creada): el entregable principal de este documento — con esto ya se puede dar de alta un segundo cliente real.
4. **Entrega 4 — Script de actualización multi-cliente + inventario de clientes** (depende de 3: necesita que exista al menos un cliente real dado de alta para tener algo que actualizar): se puede construir y probar en cuanto haya dos clientes activos.

No se recomienda agrupar todo en un único desarrollo: la Entrega 1 en particular es un cambio de código pequeño y de bajo riesgo que no debería esperar a que el resto (guiones de infraestructura, más lentos de validar de verdad contra AWS) esté terminado.

## Desglose de subtareas

### Entrega 1 — Frontend
1. **[Frontend, baja]** `core/api-base-url.ts`: `API_BASE_URL` pasa de `'http://localhost:8080/api'` a `'/api'` (relativo).
2. **[Frontend, baja]** `GestionRestauranteFront/proxy.conf.json` (nuevo) + `angular.json` (`serve.options.proxyConfig`): proxy de desarrollo que reenvía `/api` a `http://localhost:8080` — para que `ng serve` (puerto 4200) siga hablando con el backend local (puerto 8080) exactamente igual que hoy, ahora que `API_BASE_URL` ya no lleva el host absoluto. Sin este paso, el ajuste de la subtarea 1 rompe el entorno de desarrollo de todo el equipo.
3. **[Documentación, baja]** `guia-despliegue-aws.md`, paso 7: eliminar la instrucción de editar `api-base-url.ts` antes de compilar — el front ya no necesita saber el dominio en tiempo de build.

### Entrega 2 — Infraestructura (AMI)
4. **[Infraestructura, media]** Lanzar una instancia EC2 temporal, repetir el paso 4 de `guia-despliegue-aws.md` (Java, MySQL *server* sin bases de datos creadas todavía, Nginx, Tesseract+español, Certbot, Node) y ninguno de los pasos posteriores (sin BBDD de aplicación, sin `.jar`, sin configuración de Nginx todavía) — crear una **AMI** a partir de esa instancia (**EC2 → Actions → Image → Create image**) y documentar en `guia-despliegue-aws.md` (o en un nuevo apartado) qué contiene exactamente y cómo regenerarla cuando cambie una versión de paquete (p. ej. una nueva LTS de Node).

### Entrega 3 — Alta de cliente nuevo
5. **[Infraestructura, alta]** `scripts/alta-cliente.sh` (nuevo, corre en el servidor del cliente ya lanzado desde la AMI dorada): pide/recibe el subdominio del cliente; genera `APP_JWT_SECRET`/`ADMIN_SEED_PASSWORD`/contraseña de BBDD nuevos (`openssl rand`); crea la base de datos y el usuario MySQL (mismo SQL que `guia-despliegue-aws.md` paso 5.2, parametrizado); descarga/copia el `.jar` y el `dist/` ya compilados de la última versión de release (ver subtarea 7); escribe `backend.env`; crea y arranca el servicio systemd; escribe la configuración de Nginx con el `server_name` del subdominio del cliente; ejecuta Certbot para ese subdominio.
6. **[Documentación, media]** Checklist de alta de cliente (nuevo documento o ampliación de `guia-despliegue-aws.md`): lanzar instancia desde la AMI dorada → asociar IP elástica → añadir el registro DNS `A` del subdominio → ejecutar `alta-cliente.sh` → añadir la URI de redirección de ese cliente al proyecto OAuth de Google compartido (si el cliente va a usar Valoraciones) → verificación final (login con el admin sembrado, cambiar contraseña, comprobar `https://<cliente>.<tudominio>/api/...`) → anotar el cliente en el inventario (subtarea 9).
7. **[Infraestructura, baja]** Definir dónde vive el "último `.jar`/`dist/` de release" que consume `alta-cliente.sh` (p. ej. un bucket S3 privado con la versión etiquetada, o simplemente construirlo en tu máquina y hacer `scp` como ya describe `guia-despliegue-aws.md` paso 7) — decisión operativa simple, no bloquea el resto.
8. **[Backend, baja]** Confirmar/documentar que un único proyecto Google Cloud OAuth compartido admite añadir la URI de redirección de cada cliente nuevo sin crear credenciales nuevas — actualizar `guia-vinculacion-valoraciones-google.md` con este criterio para clientes nuevos (sigue permitiendo un proyecto propio si un cliente concreto lo pide explícitamente).

### Entrega 4 — Actualización multi-cliente
9. **[Infraestructura, baja]** `clientes.md` (o `.csv`) en `planesImplementacion/` (o una carpeta `ops/` nueva, fuera del código de la app): una fila por cliente con subdominio, IP elástica y fecha de alta.
10. **[Infraestructura, media]** `scripts/actualizar-todos.sh` (nuevo): lee el inventario y, para cada IP, hace `ssh` y repite los comandos del paso 13 de `guia-despliegue-aws.md` (parar servicio, copiar `.jar`/`dist/` nuevos, arrancar servicio) — con una pausa/confirmación entre cliente y cliente, no todo en paralelo sin control, para poder abortar si el primer cliente actualizado falla.

## Decisiones técnicas

- **`API_BASE_URL` relativo, no una variable inyectada en tiempo de arranque**: Angular es un SPA compilado a estático; introducir configuración "real" en tiempo de ejecución (p. ej. un `config.json` cargado antes de arrancar la app) sería más flexible pero también más cambio del que hace falta — con Nginx ya haciendo de proxy same-origin en cada servidor (`guia-despliegue-aws.md`, paso 8, sin cambios), una ruta relativa resuelve el problema real (un único `dist/` para todos los clientes) con el mínimo cambio posible.
- **Servidor por cliente, no un único servidor con múltiples sitios de Nginx**: aunque técnicamente Nginx podría servir varios `server_name` (uno por cliente) desde una única instancia EC2 con múltiples bases de datos MySQL locales, eso ya sería un paso hacia "una app compartida" con los mismos riesgos de aislamiento que la alternativa descartada — el modelo elegido es deliberadamente "un servidor completo por cliente", no una optimización de recursos a costa de mezclar clientes en la misma máquina.
- **AMI dorada en vez de Docker**: ver "Alternativas consideradas" — mismo problema (no reinstalar paquetes a mano en cada servidor nuevo), resuelto sin tocar la arquitectura de despliegue ya documentada.
- **Artefactos de release compilados una vez, no build-per-server**: `guia-despliegue-aws.md` (pensada para un solo servidor) compila en el propio servidor por simplicidad; con N servidores, compilar en cada uno multiplica el riesgo de que dos clientes acaben en versiones de código ligeramente distintas (una compilación falla a mitad, alguien hace `git pull` en momentos distintos, etc.) — un artefacto único por versión, desplegado igual en todos los servidores, es más fiable y ya es una práctica estándar de despliegue.
- **Google Cloud OAuth compartido, con varias URIs de redirección**: reduce un paso manual por cliente (crear su propio proyecto/credenciales) a "añadir una línea a una lista ya existente" — un OAuth client de Google admite explícitamente varias `redirect_uris` autorizadas; se documenta como opción por defecto, dejando abierta la posibilidad de un proyecto propio si algún cliente lo pide.
- **Sin parametrizar el nombre de la base de datos (`gestion_restaurante`) por cliente**: al vivir cada cliente en su propio servidor/instancia MySQL, no hay colisión de nombres entre clientes — cambiarlo no aportaría nada en este modelo y sí complicaría innecesariamente `alta-cliente.sh` y `application.properties`.

## Riesgos y dependencias

- **Riesgo medio — deriva de configuración entre clientes**: sin un mecanismo más automatizado que un script bash + un fichero de inventario, es responsabilidad humana no olvidar añadir un cliente nuevo al inventario o ejecutar `actualizar-todos.sh` tras cada release — aceptable para "un puñado de clientes" (decisión ya tomada), pero es el primer punto a automatizar más si el número de clientes crece.
- **Riesgo medio — secretos por cliente**: cada `backend.env` lleva su propio `APP_JWT_SECRET`/contraseña de BBDD/`ADMIN_SEED_PASSWORD`, generados por `alta-cliente.sh` — hay que decidir dónde se guardan esos secretos de forma seaura fuera del propio servidor (gestor de contraseñas del equipo, o Secrets Manager de AWS si se quiere ir más allá) para poder recuperarlos si hace falta reconstruir un servidor; no resuelto en detalle en este documento, se asume un gestor de contraseñas ya en uso por el equipo.
- **Riesgo bajo — coste**: cada cliente nuevo añade el coste de un `t3.small` (~13-15 $/mes, según `guia-despliegue-aws.md`) más su IP elástica — lineal con el número de clientes, a tener en cuenta en el modelo de precios que se cobre a cada restaurante.
- **Dependencia**: la Entrega 3 (alta de un cliente real) depende de que la 1 y la 2 estén terminadas; la Entrega 4 depende de que la 3 ya se haya usado al menos una vez.
- **Riesgo bajo, heredado**: todo lo ya señalado en `guia-despliegue-aws.md` (sin RDS, sin balanceador, backups con `mysqldump` por cron) se multiplica por cliente — cada servidor necesita su propia rutina de backup, no una compartida.

## Aspectos transversales

- **Seguridad**: aislamiento total entre clientes por diseño (servidores y bases de datos físicamente separados) — el riesgo real no es que un cliente vea datos de otro (no comparten nada), sino la gestión de secretos por cliente (ver "Riesgos"). `APP_JWT_SECRET` distinto por cliente ya limita el impacto de una fuga de token a un único restaurante.
- **Privacidad**: sin cambios respecto al modelo actual — cada restaurante sigue siendo dueño exclusivo de sus propios datos (BBDD propia), lo cual además simplifica cualquier conversación futura de protección de datos con un cliente (sus datos nunca salen de su propia base de datos).
- **Internacionalización**: no aplica — no hay contenido nuevo de cara al usuario final de la app.
- **Rendimiento**: sin cambios por cliente (cada uno sigue siendo el mismo dimensionamiento `t3.small` que ya validó `guia-despliegue-aws.md` para un restaurante); no hay recursos compartidos entre clientes que puedan verse afectados por el tráfico de otro.
- **Observabilidad**: se recomienda, aunque no se detalla como subtarea obligatoria de esta fase, que el inventario de clientes (subtarea 9) incluya también un enlace o nota de dónde revisar los logs (`journalctl -u gestion-restaurante-back`) de cada servidor — con varios servidores, no tener un sitio único donde mirar "qué cliente tiene un problema ahora mismo" es el primer dolor operativo esperable si esto crece.

## Estrategia de pruebas

- **Entrega 1**: verificación manual — `ng serve` sigue funcionando en local (proxy activo) y `ng build` + servir el `dist/` detrás de un Nginx local con proxy a `/api` sigue funcionando igual que con la URL absoluta anterior. No hay lógica de negocio que testear con un test automatizado aquí.
- **Entrega 3**: probar `alta-cliente.sh` de principio a fin contra una instancia EC2 real de prueba (no solo revisión de código) antes de usarlo con el primer cliente de pago — un script de aprovisionamiento con un error a mitad de camino puede dejar un servidor a medio configurar, más difícil de depurar que un test que falla.
- **Entrega 4**: probar `actualizar-todos.sh` contra dos servidores de prueba (no de un cliente real) antes de usarlo contra clientes en producción, comprobando en particular qué pasa si el `ssh` a un servidor falla a mitad de la lista (debe detenerse y avisar, no continuar silenciosamente con el resto).

## Despliegue y rollback

- **Rollback de un cliente concreto**: como cada cliente es un servidor independiente, revertir una actualización fallida en un cliente es exactamente el mismo procedimiento que ya describe `guia-despliegue-aws.md` para el único servidor de hoy (recompilar/copiar la versión anterior del `.jar`/`dist/`, reiniciar el servicio) — no afecta a ningún otro cliente.
- **Migraciones de BBDD por cliente**: al ejecutarse Flyway de forma independiente en cada servidor, una migración nueva se aplica cliente a cliente según cuándo se actualice cada uno (no todos a la vez, salvo que se use `actualizar-todos.sh` para desplegar la misma versión a todos en la misma sesión) — aceptable mientras las migraciones sigan siendo aditivas y no rompan compatibilidad con la versión de código anterior durante la ventana en la que distintos clientes puedan estar en versiones distintas (mismo cuidado que ya exige cualquier despliegue con Flyway, ahora simplemente multiplicado por cliente).
- **Nada de esto requiere parar todos los clientes a la vez**: al no compartir servidor ni BBDD, una actualización o un fallo en un cliente no afecta a los demás — es una de las ventajas explícitas del modelo elegido.

## Criterios de aceptación técnicos

- El `dist/` del frontend compilado una sola vez funciona, sin recompilar, servido desde Nginx en cualquier dominio (probado al menos con dos dominios/subdominios distintos).
- `ng serve` en local sigue funcionando contra el backend local sin ningún cambio en el flujo de trabajo del equipo.
- Lanzar una instancia nueva desde la AMI dorada y ejecutar `alta-cliente.sh` deja un cliente nuevo completamente funcional (login con el admin sembrado, HTTPS activo) sin pasos manuales adicionales de instalación de paquetes.
- El inventario de clientes refleja siempre los clientes realmente dados de alta (comprobado tras el alta del segundo cliente real).
- `actualizar-todos.sh`, ejecutado tras publicar una versión nueva, dispara sin intervención adicional la actualización de todos los clientes del inventario, deteniéndose y avisando si algún servidor falla.

## Suposiciones

- **Dominio propio de la empresa para los subdominios**: se asume que se registrará (o ya existe) un dominio propio bajo el que colgar los subdominios de cada cliente (p. ej. reutilizando la marca "Mavero" ya establecida en la Fase 6.2, `mavero.app`/`mavero.com` o similar) — no confirmado por el usuario, decisión de negocio pendiente fuera del alcance técnico de este documento.
- **Un gestor de secretos ya en uso por el equipo** (o que se adoptará uno) para guardar los `backend.env`/contraseñas generados por cliente — no se construye ni se elige aquí una herramienta concreta.
- **El proveedor SMTP y el proyecto Google Cloud OAuth pueden compartirse entre todos los clientes** (con `email_remitente`/nombre visible personalizado por restaurante desde `configuracion_empresa`, ya existente) — no confirmado explícitamente por el usuario, pero es la opción de menor fricción operativa y no compromete el aislamiento de datos entre clientes (son credenciales de infraestructura compartida, no datos de negocio de ningún restaurante).
- **El volumen de clientes se mantiene "un puñado"** durante el horizonte de este documento (según lo acordado) — si creciera sustancialmente, este mismo documento señala explícitamente (en "Alternativas consideradas") cuándo revisar el modelo de una app compartida.

## Notas adicionales

Este documento es, como `integracion_verifactu-*.md` y `fichajes_vacaciones_2027-*.md`, un plan de infraestructura/cumplimiento fuera de la numeración secuencial de fases de producto de `plan-implementacion-general.md` — no añade ni renumera ninguna fase ahí, solo se referencia desde sus "Notas adicionales".
