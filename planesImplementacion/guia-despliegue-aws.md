# Guía de despliegue en AWS

Guía paso a paso para llevar GestionRestaurante (front Angular + back Spring Boot + MySQL) a un servidor en AWS, con dominio propio y HTTPS. Pensada para alguien que nunca ha desplegado nada en AWS.

## 1. Arquitectura elegida y por qué

Para un proyecto de este tamaño (un restaurante, tráfico bajo), lo más simple y barato es **un único servidor EC2** con todo dentro:

```
Internet
   │
   ▼
[Route 53 / DNS del dominio] → apunta a la IP elástica del servidor
   │
   ▼
[EC2 - una sola instancia Ubuntu]
   ├── Nginx (puerto 80/443) — sirve el front (ficheros estáticos) y hace de proxy a /api
   ├── Backend Spring Boot (puerto 8080, como servicio systemd)
   └── MySQL (puerto 3306, solo accesible desde localhost)
```

No hace falta RDS (base de datos gestionada aparte) ni balanceador de carga para empezar — son mejoras de fiabilidad que se pueden añadir más adelante sin cambiar la arquitectura de la aplicación. Tampoco hace falta Docker: el backend es un único `.jar` autocontenido y el front son ficheros estáticos.

**Coste aproximado**: una instancia `t3.small` (2 GB RAM, suficiente para MySQL + Java a la vez) ronda los 13-15 $/mes en `eu-west-1` (Irlanda) si se deja encendida 24/7, más ~1$/mes de la IP elástica si el servidor se apaga alguna vez (mientras está asociada a una instancia en marcha es gratis), más lo que cueste el dominio (ver paso 2). MySQL no tiene coste aparte porque corre dentro de la misma instancia.

## 2. Comprar el dominio

Dos opciones, cualquiera de las dos funciona igual de bien:

**Opción A — Route 53 (todo dentro de AWS, más cómodo si ya vas a usar AWS)**
1. En la consola de AWS, ve a **Route 53 → Registered domains → Register domain**.
2. Busca el dominio que quieras (p. ej. `sidrerialazapica.com`) y compruébalo disponible.
3. Rellena los datos de contacto (WHOIS) y paga (los `.com` rondan 12-15 $/año).
4. Tras el registro (puede tardar unos minutos), Route 53 crea automáticamente una **Hosted Zone** para el dominio — ahí es donde luego añadirás el registro DNS que apunta al servidor (paso 8).

**Opción B — Un registrador externo (Namecheap, GoDaddy, IONOS...)**
1. Compra el dominio igual que siempre en el registrador que prefieras (suele ser algo más barato que Route 53).
2. Luego, en el panel de ese registrador, apuntas los **nameservers** a los de una Hosted Zone de Route 53 (créala en AWS: **Route 53 → Hosted zones → Create hosted zone**, tipo público, con el nombre de tu dominio) — así gestionas el DNS desde AWS igualmente sin haber comprado el dominio ahí. Alternativa más simple: gestiona el DNS directamente en el panel del registrador (todos ofrecen editor de registros A/CNAME), sin tocar Route 53 para nada.

Para esta guía asumo que acabas gestionando el DNS en algún sitio con un editor de registros tipo A/CNAME — el paso concreto (7) es el mismo se use Route 53 o el panel del registrador.

## 3. Crear la cuenta de AWS y lanzar el servidor (EC2)

1. Crea una cuenta en [aws.amazon.com](https://aws.amazon.com) si no tienes una (pide tarjeta de crédito).
2. Entra en la consola y ve al servicio **EC2**. Elige una región cercana a tus usuarios (p. ej. `eu-west-3` París, o `eu-west-1` Irlanda).
3. **Launch instance**:
   - **Name**: `gestion-restaurante-prod` (o lo que prefieras).
   - **AMI (sistema operativo)**: `Ubuntu Server 24.04 LTS` (elige la versión LTS más reciente disponible).
   - **Instance type**: `t3.small` (2 vCPU, 2 GB RAM) — un `t3.micro` (1 GB) puede quedarse corto arrancando Java + MySQL a la vez.
   - **Key pair**: crea una nueva (`Create new key pair`, tipo RSA, formato `.pem`) y **descarga el fichero** — es la única forma de conectarte por SSH después, no se puede volver a descargar.
   - **Network settings → Security group**: crea uno nuevo con estas reglas de entrada (inbound):
     | Tipo | Puerto | Origen |
     |---|---|---|
     | SSH | 22 | Tu IP (elige "My IP", no "Anywhere" — evita que cualquiera intente entrar por SSH) |
     | HTTP | 80 | Anywhere (0.0.0.0/0) |
     | HTTPS | 443 | Anywhere (0.0.0.0/0) |

     No abras el 8080 (backend) ni el 3306 (MySQL) al exterior — solo se acceden internamente a través de Nginx/localhost.
   - **Storage**: 20-30 GB de disco `gp3` es de sobra (sistema + MySQL + facturas/nóminas subidas).
4. **Launch instance**.
5. Una vez arrancada, resérvale una **IP elástica** (para que no cambie si reinicias el servidor): **EC2 → Elastic IPs → Allocate Elastic IP address**, y luego **Associate** esa IP a tu instancia.
6. Conéctate por SSH (desde Windows con Git Bash o WSL):
   ```bash
   chmod 400 ruta/a/tu-clave.pem
   ssh -i ruta/a/tu-clave.pem ubuntu@<tu-ip-elastica>
   ```

## 4. Preparar el servidor: paquetes necesarios

Ya conectado por SSH al servidor Ubuntu recién creado:

```bash
sudo apt update && sudo apt upgrade -y

# Java 21 (el backend está compilado para Java 21)
sudo apt install -y openjdk-21-jre-headless

# MySQL
sudo apt install -y mysql-server

# Nginx (proxy + servidor de ficheros estáticos del front)
sudo apt install -y nginx

# Tesseract OCR + paquete de idioma español (reconocimiento automático de facturas)
sudo apt install -y tesseract-ocr tesseract-ocr-spa

# Certbot (certificado HTTPS gratuito de Let's Encrypt)
sudo apt install -y certbot python3-certbot-nginx

# Git (para clonar el repo) y Maven (para compilar el backend en el propio servidor,
# alternativa a compilarlo en tu máquina y subir el .jar - ver paso 6)
sudo apt install -y git maven

# Node.js 22+ (para compilar el front) - Ubuntu 24.04 trae una versión antigua por defecto,
# se instala vía NodeSource
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
```

Verifica versiones:
```bash
java -version    # debe ser 21
node -v          # debe ser >= 22
mysql --version
nginx -v
tesseract --version
```

## 5. Base de datos

1. Asegura la instalación de MySQL (te pedirá poner contraseña de root y responder unas preguntas de seguridad — responde "sí" a todo salvo que tengas un motivo concreto para no hacerlo):
   ```bash
   sudo mysql_secure_installation
   ```
2. Crea la base de datos y un usuario dedicado para la aplicación (no uses `root` desde el backend):
   ```bash
   sudo mysql -u root -p
   ```
   Dentro del cliente de MySQL:
   ```sql
   CREATE DATABASE gestion_restaurante
       CHARACTER SET utf8mb4
       COLLATE utf8mb4_unicode_ci;

   CREATE USER 'gestor'@'localhost' IDENTIFIED BY 'PonAquiUnaContraseñaFuerte';
   GRANT ALL PRIVILEGES ON gestion_restaurante.* TO 'gestor'@'localhost';
   FLUSH PRIVILEGES;
   EXIT;
   ```
   Guarda esa contraseña — la necesitas en el paso 6 como `DB_PASSWORD`.
3. **No hace falta ejecutar ningún script de tablas a mano**: el backend trae Flyway (`spring.flyway.enabled=true`) y crea/actualiza todas las tablas automáticamente la primera vez que arranca contra una base de datos vacía.

## 6. Compilar y desplegar el backend

Puedes compilar el `.jar` **en el propio servidor** (más simple, no necesitas subir nada) o **en tu máquina y subir solo el `.jar`** (más rápido de repetir en despliegues siguientes). Aquí va la opción de compilar en el servidor:

1. Clona el repositorio en el servidor:
   ```bash
   cd ~
   git clone <url-de-tu-repositorio-git> GestionRestaurante
   cd GestionRestaurante/GestionRestauranteBack
   ```
2. Descarga el paquete de idioma de Tesseract (igual que en local, ver `README.md` del backend):
   ```bash
   mkdir -p .tessdata
   curl -sL -o .tessdata/spa.traineddata https://github.com/tesseract-ocr/tessdata_fast/raw/main/spa.traineddata
   ```
3. Compila el `.jar` (usa el Maven del sistema que instalaste en el paso 4, no hace falta el wrapper aquí):
   ```bash
   mvn -DskipTests package
   ```
   Esto genera `target/gestion-restaurante-back-0.0.1-SNAPSHOT.jar`.
4. Crea una carpeta persistente para lo que el backend necesita guardar entre despliegues (facturas/nóminas subidas):
   ```bash
   sudo mkdir -p /opt/gestion-restaurante/uploads
   sudo chown -R ubuntu:ubuntu /opt/gestion-restaurante
   cp -r .tessdata /opt/gestion-restaurante/
   cp target/gestion-restaurante-back-0.0.1-SNAPSHOT.jar /opt/gestion-restaurante/app.jar
   ```
5. Crea el fichero de variables de entorno de producción (**nunca lo subas al repositorio**):
   ```bash
   sudo nano /opt/gestion-restaurante/backend.env
   ```
   Contenido (sustituye cada valor por el real):
   ```ini
   DB_USERNAME=gestor
   DB_PASSWORD=PonAquiUnaContraseñaFuerte
   ADMIN_SEED_PASSWORD=OtraContraseñaFuerteParaElAdminInicial
   TESSDATA_PATH=/opt/gestion-restaurante/.tessdata
   STORAGE_BASE_PATH=/opt/gestion-restaurante/uploads
   MAIL_HOST=smtp.tuservicio.com
   MAIL_PORT=587
   MAIL_USERNAME=tu-usuario-smtp
   MAIL_PASSWORD=tu-password-smtp
   GOOGLE_CLIENT_ID=tu-client-id-de-google
   GOOGLE_CLIENT_SECRET=tu-client-secret-de-google
   GOOGLE_TOKEN_ENCRYPTION_KEY=clave-generada-con-openssl-rand--base64-32
   GOOGLE_REDIRECT_URI=https://tudominio.com/api/valoraciones/conexion/callback
   FRONT_BASE_URL=https://tudominio.com

   # app.jwt.secret y app.cors.allowed-origin en application.properties no usan ${VARIABLE},
   # pero Spring Boot permite sobreescribir CUALQUIER propiedad con una variable de entorno
   # (los puntos se convierten en "_" y los guiones se eliminan, todo en mayusculas):
   APP_JWT_SECRET=genera-esto-con-openssl-rand--base64-48
   APP_CORS_ALLOWEDORIGIN=https://tudominio.com
   # nominas.enabled=false por defecto en produccion - actívalo solo cuando el SMTP de arriba
   # ya este probado y funcionando
   NOMINAS_ENABLED=false
   ```

6. Crea el servicio systemd para que el backend arranque solo y se reinicie si se cae:
   ```bash
   sudo nano /etc/systemd/system/gestion-restaurante-back.service
   ```
   Contenido:
   ```ini
   [Unit]
   Description=GestionRestaurante Backend
   After=network.target mysql.service

   [Service]
   Type=simple
   User=ubuntu
   EnvironmentFile=/opt/gestion-restaurante/backend.env
   ExecStart=/usr/bin/java -jar /opt/gestion-restaurante/app.jar
   Restart=on-failure
   RestartSec=5

   [Install]
   WantedBy=multi-user.target
   ```
7. Arráncalo y déjalo habilitado para el próximo reinicio del servidor:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable --now gestion-restaurante-back
   sudo systemctl status gestion-restaurante-back
   ```
   Comprueba los logs si algo falla:
   ```bash
   journalctl -u gestion-restaurante-back -f
   ```
   El backend queda escuchando en `http://localhost:8080` (Tomcat embebido) — no accesible desde fuera todavía, eso lo resuelve Nginx en el paso 8.

## 7. Compilar y desplegar el frontend

El front necesita saber contra qué URL de API hablar **en tiempo de compilación** (no hay variables de entorno en tiempo de ejecución en este proyecto): la constante `API_BASE_URL` en `GestionRestauranteFront/src/app/core/api-base-url.ts` está fijada a `http://localhost:8080/api`.

1. **Antes de compilar**, edita ese fichero (en tu máquina, antes de subir el cambio, o directamente en el servidor si compilas ahí) y cámbialo a tu dominio real:
   ```ts
   export const API_BASE_URL = 'https://tudominio.com/api';
   ```
2. Compila (puedes hacerlo en tu máquina local, que suele ser más rápido, y luego subir solo la carpeta `dist/`, o en el servidor igual que el backend):
   ```bash
   cd GestionRestauranteFront
   npm ci
   npm run build
   ```
   Esto genera los ficheros estáticos en `dist/GestionRestauranteFront/browser/`.
3. Copia ese contenido a la carpeta que servirá Nginx:
   ```bash
   sudo mkdir -p /var/www/gestion-restaurante
   sudo cp -r dist/GestionRestauranteFront/browser/* /var/www/gestion-restaurante/
   ```
   (Si compilaste en tu máquina, sube la carpeta `dist/GestionRestauranteFront/browser/` al servidor con `scp -i tu-clave.pem -r dist/GestionRestauranteFront/browser ubuntu@<ip>:~/front-dist` y luego `sudo cp -r ~/front-dist/* /var/www/gestion-restaurante/` en el servidor.)

## 8. Nginx: servir el front y hacer de proxy al backend

Crea la configuración del sitio:
```bash
sudo nano /etc/nginx/sites-available/gestion-restaurante
```
Contenido:
```nginx
server {
    listen 80;
    server_name tudominio.com www.tudominio.com;

    root /var/www/gestion-restaurante;
    index index.html;

    # Angular es una SPA: cualquier ruta que no sea un fichero real cae en index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # El backend vive en /api - proxy directo al Spring Boot local
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    client_max_body_size 10M;  # coincide con spring.servlet.multipart.max-file-size
}
```
Actívala y recarga Nginx:
```bash
sudo ln -s /etc/nginx/sites-available/gestion-restaurante /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```
En este punto, si el DNS ya apunta al servidor (paso 9), `http://tudominio.com` ya debería cargar la aplicación (sin HTTPS todavía).

## 9. Apuntar el dominio al servidor (DNS)

En el sitio donde gestiones el DNS (Route 53 o el panel del registrador, ver paso 2), crea un registro:

| Tipo | Nombre | Valor |
|---|---|---|
| A | `tudominio.com` (o `@`) | La IP elástica del servidor |
| A | `www` | La misma IP elástica (o un CNAME a `tudominio.com`) |

Los cambios de DNS pueden tardar desde minutos hasta un par de horas en propagarse. Puedes comprobar si ya resuelve con:
```bash
nslookup tudominio.com
```

## 10. HTTPS con Certbot

Con el DNS ya apuntando al servidor y Nginx sirviendo por el puerto 80:
```bash
sudo certbot --nginx -d tudominio.com -d www.tudominio.com
```
Certbot pide un email de contacto, modifica automáticamente la configuración de Nginx para servir por HTTPS (443) y redirigir el HTTP a HTTPS, y programa la renovación automática del certificado (gratuito, se renueva solo cada ~60 días vía un temporizador systemd que instala el propio paquete).

Verifica que la renovación automática funciona:
```bash
sudo certbot renew --dry-run
```

## 11. Comprobación final

- `https://tudominio.com` → carga el front.
- Inicia sesión con el admin inicial: `admin@gestionrestaurante.local` / la contraseña que pusiste en `ADMIN_SEED_PASSWORD` (o `Admin.2026` si no la sobreescribiste — **cámbiala desde la app en cuanto entres**).
- Prueba un endpoint de la API directamente: `https://tudominio.com/api/...` debe responder (401 si no envías token, es lo esperado).
- Si vas a usar Valoraciones de Google: repite el flujo OAuth (Google Cloud Console) con la URI de redirección de producción (`https://tudominio.com/api/valoraciones/conexion/callback`), no la de `localhost` — son credenciales distintas a las de desarrollo.

## 12. Copias de seguridad (mínimo recomendado)

- **Base de datos**: un cron diario con `mysqldump`, guardando el `.sql` fuera del propio servidor (p. ej. subido a S3):
  ```bash
  mysqldump -u gestor -p gestion_restaurante > backup-$(date +%F).sql
  ```
- **Ficheros subidos** (`/opt/gestion-restaurante/uploads`): facturas y nóminas en PDF — inclúyelos en la misma rutina de backup (o usa un volumen EBS con snapshots automáticos desde la consola de AWS).

## 13. Actualizar la aplicación en despliegues futuros

Backend:
```bash
cd ~/GestionRestaurante/GestionRestauranteBack
git pull
mvn -DskipTests package
sudo systemctl stop gestion-restaurante-back
cp target/gestion-restaurante-back-0.0.1-SNAPSHOT.jar /opt/gestion-restaurante/app.jar
sudo systemctl start gestion-restaurante-back
```
Frontend:
```bash
cd ~/GestionRestaurante/GestionRestauranteFront
git pull
npm ci
npm run build
sudo cp -r dist/GestionRestauranteFront/browser/* /var/www/gestion-restaurante/
```
Nginx no necesita reiniciarse para el front (son ficheros estáticos); para el backend, systemd lo hace por ti con los comandos de arriba.
