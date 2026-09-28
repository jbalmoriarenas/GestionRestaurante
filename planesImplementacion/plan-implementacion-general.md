# Plan de Implementación: GestionRestaurante (visión general)

## Contexto y objetivo

Aplicación de gestión integral para un restaurante, compuesta por varios módulos funcionales sobre una base común de empleados y autenticación:

1. **Fichaje de empleados** — registro de entrada/salida tipo Woffu simplificado, con vista propia (empleado) y vista de supervisión (Admin).
2. **Gestión de vacaciones** — calendario de festivos y días propios, solicitud de vacaciones por el empleado (con cupo anual y disponibilidad) y aprobación/rechazo por el Admin.
3. **Gestión de facturas** — facturas de proveedores y de suministros (luz, agua...), en pestañas separadas; digitalizar (foto o PDF, con conversión automática de foto a PDF), extraer datos automáticamente (OCR) y consultar facturas con filtros avanzados, paginación y exportación a Excel o a ZIP con los archivos originales del periodo.
4. **Gestión de nóminas** — mantener el listado de empleados, subir PDFs/ZIPs de nóminas y enviarlas por email.
5. **Gestión de valoraciones** — conexión OAuth con la cuenta de Google Business Profile del restaurante, sincronización periódica (no un stream en vivo, Google no lo ofrece) de sus reseñas de Google, y panel de consulta con resumen (media, distribución por estrellas) y filtros.
6. **Configuración** — datos de empresa (nombre y logo, visibles en la cabecera de toda la app), y punto único de configuración para la ubicación/radio de la geovalla de fichaje y los días de vacaciones anuales por defecto (antes embebidos en otras vistas).

Es un único restaurante (sin multi-sede por ahora) con dos roles de usuario: **Admin** y **Empleado**.

Este documento es un plan **general** (arquitectura + fases), no un desglose ticket a ticket. Cada fase deberá convertirse en tickets/planes de detalle más adelante.

Además de estas seis fases numeradas, hay dos **planes de cumplimiento normativo** documentados aparte (`integracion_verifactu-*.md` y `fichajes_vacaciones_2027-*.md`, en `bbdd/`/`back/`/`front/`): preparación técnica para la facturación verificable VeriFactu y para el nuevo registro horario digital, ambos con fecha de entrada en vigor propia (ver "Notas adicionales"). No son funcionalidad de producto priorizada por el usuario, por lo que se mantienen fuera de la numeración secuencial de fases hasta que se decida implementarlos.

## Stack tecnológico

Ya montado y verificado en el repo (`GestionRestaurante/`):

| Capa | Tecnología | Versión |
|---|---|---|
| Frontend | Angular (standalone, zoneless) | 22.1.7 |
| Frontend runtime | Node.js (vía Volta, pineado al proyecto) | 24.21.0 |
| Backend | Spring Boot / Spring Framework | 4.1.1 / 7.0.9 |
| Backend runtime | Java (JDK embebido en el proyecto) | 21 |
| Persistencia | Spring Data JPA + Hibernate ORM | 7.4.5 |
| Base de datos | MySQL | driver `mysql-connector-j` 9.7.0 |
| Build | Maven (`mvnw`) / npm | — |

A añadir según los módulos:

- **Spring Security + JWT** — autenticación y autorización por rol (ADMIN / EMPLEADO).
- **Spring Boot Starter Mail** — envío de nóminas por email vía SMTP.
- **Apache POI** (`poi-ooxml`) — exportación de listados a Excel (Fase 2.2, ver `back/fase2-2-back.md`).
- **Tess4J + Apache PDFBox** — extracción automática (OCR) de facturas y conversión de foto a PDF: motor Tesseract y rasterizado/generación de PDF, respectivamente. Ambas gratuitas (Apache License 2.0); decisión tomada en la Fase 2.3, ver "Decisiones técnicas" y `back/fase2-3-back.md`. Tess4J requiere el binario nativo de Tesseract instalado en cada entorno (no es una dependencia JVM autosuficiente). (`Apache Commons Text`, usada en una versión anterior solo para *fuzzy matching* de proveedor, se retiró en una revisión posterior — ver `back/fase2-3-back.md`.)
- **Spring Boot Starter Validation** (ya incluido) — validación de formularios en los distintos módulos.
- **OAuth 2.0 contra Google (Business Profile / "Google My Business API v4")** — conexión y sincronización de reseñas, Fase 4. Sin SDK oficial de Google: llamadas REST directas vía `RestClient` de Spring, para no añadir una dependencia pesada solo para un puñado de endpoints (decisión tomada en la Fase 4.1, ver `back/fase4-1-back.md`). Requiere un proyecto en Google Cloud con acceso aprobado a esa API (aprobación manual de Google, puede tardar días) y que la cuenta de Google Business Profile del restaurante autorice el acceso.
- **Spring `@Scheduled`** (ya incluido en Spring Boot, sin dependencia nueva) — primera tarea programada del proyecto, sincronización periódica de reseñas (Fase 4.1).
- **Sin dependencias nuevas para la Fase 5 (Configuración)**: reutiliza `FileStorageService` (logo) y `EMPLEADOS:EDICION` (permiso) ya existentes.
- **ZXing** (`com.google.zxing:core`+`javase`) — generación de códigos QR, **solo si se decide implementar** `integracion_verifactu-back.md` (fuera de la numeración secuencial de fases, ver "Contexto y objetivo"). El plan de cumplimiento de registro horario (`fichajes_vacaciones_2027-back.md`) no requiere ninguna dependencia nueva (reutiliza Apache POI ya listado).

## Análisis funcional y supuestos

- **Un solo restaurante**, sin necesidad de modelar "sede" en ninguna entidad (asunción confirmada).
- **Roles**: Admin/RRHH (acceso total) y Empleado (acceso **únicamente** al módulo de Fichaje, y solo a sus propios fichajes). El Empleado **no** tiene acceso a Facturas de proveedores ni a Nóminas desde la app — las nóminas le llegan solo por email; ni la vista ni los endpoints de esos dos módulos son alcanzables por el rol Empleado.
- **Permisos**: dos roles fijos definidos en código (ADMIN / EMPLEADO), no un sistema de permisos configurable desde la app. Cambiar qué ve cada rol implica un cambio de código (guards en Angular + `@PreAuthorize` en Spring), no una pantalla de administración de permisos.
- **Facturas**: dos categorías — facturas de proveedores y facturas de suministros (luz, agua, gas...) — mostradas en dos pestañas de un mismo listado, con búsqueda avanzada (proveedor/estado de pago/mes), paginación de 10 en 10 y exportación a Excel (todos los campos de negocio, sin datos de contacto del proveedor) o a ZIP con los archivos originales del periodo filtrado. Se quiere extracción automática de datos (OCR) al subir una foto/PDF de una factura, no solo carga manual; una foto se convierte automáticamente a PDF antes de guardarse. **Revisión posterior**: el mecanismo de "campos nuevos creados automáticamente en BBDD" (tabla de campos dinámicos, EAV) se probó y se descartó — con facturas reales generaba más ruido que valor; el PDF/imagen original, siempre conservado y descargable, cubre esa misma necesidad (ver nota histórica en `bbdd/fase2-3-bbdd.md`). Se asume que el usuario siempre podrá **revisar y corregir** lo extraído antes de darlo por bueno (ningún proceso automático debe ser 100% ciego). Desglose completo en `bbdd/`, `back/` y `front/fase2-1-front.md` a `fase2-3-front.md`.
- **Nóminas**: el envío por email es **manual** (el admin sube el archivo, revisa la asignación empleado↔nómina, y pulsa "Enviar"), nunca automático al subir el archivo.
- **Valoraciones**: "en tiempo real" (enunciado del usuario) se interpreta como sincronización periódica automática (job programado cada 6h por defecto) más un botón de sincronización manual bajo demanda — Google no ofrece un stream de reseñas nuevas, así que un "tiempo real" literal no es viable (acordado con el usuario, ver `back/fase4-1-back.md`). Se asume una única ubicación de Google Business Profile por cuenta conectada (el restaurante); acceso exclusivo de `ADMIN` (conectar la cuenta, sincronizar y consultar reseñas), sin acceso de `EMPLEADO` en esta fase. Solo lectura: no hay funcionalidad de responder a reseñas desde la app.
- Se asume que los empleados tendrán login propio (usuario/contraseña) para fichar y consultar sus nóminas, no un totem/tablet compartido.
- Se asume entorno de despliegue único (sin múltiples entornos por ahora); no se detallan pipelines de CI/CD en este plan.

## Arquitectura propuesta

**Monolito modular** en el backend (un único Spring Boot, paquetes separados por dominio: `auth`, `empleados`, `fichajes`, `facturas`, `nominas`, `common`), consumido por el SPA Angular vía API REST. Un monolito modular es la opción correcta aquí: bajo volumen de datos esperado, un solo equipo, y evita la complejidad operativa de microservicios que no aporta valor a este tamaño de proyecto. Si en el futuro el módulo de OCR necesita escalar de forma independiente (picos de procesamiento), se puede extraer como servicio aparte sin rehacer el resto.

```
GestionRestaurante/
├── GestionRestauranteFront/   (Angular SPA)
├── GestionRestauranteBack/    (Spring Boot monolito modular)
│   └── src/main/java/.../
│       ├── auth/          (login, JWT, roles)
│       ├── empleados/     (CRUD empleados)
│       ├── fichajes/      (entrada/salida, cálculo de horas)
│       ├── facturas/      (proveedores, facturas, OCR)
│       ├── nominas/       (nóminas, envío de email)
│       ├── valoraciones/  (conexión OAuth con Google, sincronización y consulta de reseñas)
│       └── common/        (almacenamiento de ficheros, utilidades)
└── planesImplementacion/
```

### Transversales

- **Autenticación**: Spring Security + JWT (login devuelve token; el front lo guarda y lo manda vía interceptor HTTP). Passwords con BCrypt.
- **Autorización / permisos**: no es un rol fijo hardcodeado en el código. Se modela como catálogo relacional — **`tipos_usuarios`** (ADMIN, EMPLEADO, y los que se añadan en el futuro) × **`vistas`** (Empleados, Fichajes, Facturas, Nóminas) × **`permisos`** (nivel de acceso `EDICION` o `VISUALIZACION`; si no hay fila para una combinación, ese tipo de usuario no tiene ningún acceso a esa vista). El JWT solo lleva identidad (`empleadoId`, código del tipo de usuario); las autoridades de Spring Security (`"<VISTA>:<NIVEL>"`) se resuelven en cada request contra la tabla `permisos`, y el login devuelve además el mapa de permisos completo para que el front monte menú y guards. Se aplica en dos capas:
  - **Backend**: `@PreAuthorize("hasAuthority('FACTURAS:EDICION')")` (o `hasAnyAuthority(...)` para lectura) en cada controller — nunca `hasRole('ADMIN')` fijo, precisamente para no tener que tocar código de autorización cuando se añada un tipo de usuario nuevo.
  - **Frontend**: guard `permissionGuard(vista, nivelMinimo)` por ruta + menú de navegación generado a partir de los permisos del usuario logueado, no de un `if (rol === 'ADMIN')`.

  Matriz de acceso inicial (seed, ver `planesImplementacion/bbdd/fase0-bbdd.md`):

  | Vista | ADMIN | EMPLEADO |
  |---|---|---|
  | Empleados | EDICIÓN | sin acceso |
  | Fichajes (propios) | EDICIÓN | EDICIÓN |
  | Fichajes (de todos / corrección) | EDICIÓN (vía la authority de `EMPLEADOS`, ver nota debajo) | sin acceso (la restricción "solo los suyos" es lógica de negocio, no de la tabla de permisos) |
  | Vacaciones (propias: solicitar/cancelar) | EDICIÓN | EDICIÓN |
  | Vacaciones (de todos / aprobar-rechazar) | EDICIÓN (vía la authority de `EMPLEADOS`, ver nota debajo) | sin acceso |
  | Facturas de proveedores | EDICIÓN | sin acceso (ni vista ni endpoints) |
  | Nóminas (gestión y envío) | EDICIÓN | sin acceso (ni vista ni endpoints; recibe la nómina solo por email) |
  | Valoraciones (conexión Google y consulta) | EDICIÓN | sin acceso (ni vista ni endpoints) |

  **Nota (Fase 1)**: `FICHAJES:EDICION` y `VACACIONES:EDICION` los tiene también `EMPLEADO` (para operar sobre lo suyo), así que esa authority no sirve para proteger los endpoints "de cualquier empleado" que usa el Admin (listar/corregir fichajes de todos, aprobar/rechazar solicitudes de cualquiera). Esos endpoints reutilizan `hasAuthority('EMPLEADOS:EDICION')` — que solo tiene `ADMIN` — en vez de crear una vista/permiso nueva solo para esa distinción; ver el detalle en `planesImplementacion/back/fase1-3-back.md` y `fase1-4-back.md`.

  La gestión de esta matriz desde una pantalla de administración (crear tipos de usuario, marcar EDICIÓN/VISUALIZACIÓN/sin acceso por vista) queda para una fase posterior; en Fase 0 se deja bien modelada en BBDD/backend y poblada por seed.
- **Almacenamiento de ficheros** (facturas y nóminas): sistema de ficheros local (carpeta `uploads/`, organizada por tipo/año/mes) para el MVP; en BBDD solo se guarda la ruta/metadata, nunca el binario. Diseñar el acceso a ficheros detrás de una interfaz (`FileStorageService`) para poder migrar a S3/Blob Storage sin tocar el resto del código si el volumen crece.
- **CORS**: habilitado para `localhost:4200` en desarrollo y el dominio real en producción.

## Modelo de datos de alto nivel

**TipoUsuario**
`id, codigo (ADMIN|EMPLEADO|...), nombre, descripcion`

**Vista**
`id, codigo (EMPLEADOS|FICHAJES|FACTURAS|NOMINAS|...), nombre, descripcion`

**Permiso**
`id, tipo_usuario_id (FK), vista_id (FK), nivel_acceso (EDICION|VISUALIZACION)` — único por (`tipo_usuario_id`, `vista_id`); sin fila = sin acceso.

**Empleado**
`id, nombre, apellidos, email, dni_nie, telefono, puesto, fecha_alta, fecha_baja (nullable), activo, tipo_usuario_id (FK), password_hash, dias_vacaciones_anuales (default 23)`

**Fichaje**
`id, empleado_id (FK), fecha, hora_entrada, hora_salida (nullable), horas_calculadas, observaciones, corregido_por_admin (bool), corregido_por_admin_id (FK, nullable), fecha_correccion (nullable)`

**DiaFestivo**
`id, fecha, descripcion`

**SolicitudVacaciones**
`id, empleado_id (FK), fecha_inicio, fecha_fin, dias_solicitados, estado (PENDIENTE|APROBADA|RECHAZADA), fecha_solicitud, fecha_resolucion (nullable), resuelta_por_admin_id (FK, nullable), motivo_rechazo (nullable)`

**Proveedor** (esquema definitivo, ver [`bbdd/fase2-1-bbdd.md`](./bbdd/fase2-1-bbdd.md))
`id, nombre, cif (nullable), email_contacto, telefono, tipo (PROVEEDOR|SUMINISTRO)` — el tipo distingue las dos pestañas del listado de facturas (`front/fase2-1-front.md`); es una propiedad de la empresa, no de cada factura.

**Factura** (esquema definitivo, ver [`bbdd/fase2-1-bbdd.md`](./bbdd/fase2-1-bbdd.md) y [`bbdd/fase2-3-bbdd.md`](./bbdd/fase2-3-bbdd.md))
`id, proveedor_id (FK), numero_factura (nullable), fecha_impresion, fecha_entrega (nullable), importe_base (nullable), iva (nullable), importe_total, mes, anio (derivados de fecha_impresion, nunca enviados por el cliente), estado_pago (PENDIENTE|PAGADA), estado_extraccion (MANUAL|PENDIENTE|PROCESADA|ERROR|REVISADA, añadido en Fase 2.3), ruta_archivo (siempre un PDF — una foto subida se convierte antes de guardar), nombre_archivo_original, subida_por_admin_id, fecha_subida` — `estado_pago` (negocio: si ya se ha pagado) y `estado_extraccion` (pipeline de OCR) son conceptos independientes, no confundir. El PDF original (`ruta_archivo`) siempre se conserva y es la vía para consultar cualquier dato de la factura que no sea uno de estos campos fijos; no existe ninguna tabla de campos dinámicos ni columna de texto OCR crudo (ver nota histórica en `bbdd/fase2-3-bbdd.md`). `producto`, `fecha_factura` y `fecha_vencimiento` — presentes en el esquema original de Fase 2.1 — se eliminaron por completo en una revisión posterior (`bbdd/fase2-3-bbdd.md` `V34`/`V35`); `fecha_impresion` (añadida en Fase 2.3) es ahora la fecha de referencia única.

**Nomina** (esquema definitivo, ver [`bbdd/fase3-1-bbdd.md`](./bbdd/fase3-1-bbdd.md))
`id, empleado_id (FK), periodo_mes, periodo_anio, ruta_archivo, nombre_archivo_original, estado (ADJUNTADA|ENVIADA), fecha_subida, subida_por_admin_id (FK), fecha_envio (nullable), enviada_por_admin_id (FK, nullable)` — el tercer estado que ve el Admin en la UI, "pendiente" (sin nómina subida), **no es un valor de esta columna**: es la ausencia de fila para un empleado activo en un periodo dado, calculada al listar (ver `back/fase3-1-back.md`).

**ConexionGoogleBusiness** (esquema definitivo, ver [`bbdd/fase4-1-bbdd.md`](./bbdd/fase4-1-bbdd.md))
`id (fijo a 1), location_resource_name, nombre_ubicacion (nullable), refresh_token_cifrado, fecha_conexion, conectado_por_admin_id (FK), fecha_ultima_sincronizacion (nullable), ultimo_error (nullable)` — fila única (`CHECK id = 1`, mismo patrón que `configuracion_ubicacion`); a diferencia de esa tabla, **sin fila = "Desconectado"** (no se siembra por defecto). `refresh_token_cifrado` es el único dato cifrado en reposo de todo el proyecto (ver `back/fase4-1-back.md`).

**Valoracion** (esquema definitivo, ver [`bbdd/fase4-1-bbdd.md`](./bbdd/fase4-1-bbdd.md))
`id, google_review_id (único, clave de sincronización), autor_nombre, autor_foto_url (nullable), puntuacion (1-5), comentario (nullable), fecha_publicacion, fecha_actualizacion (nullable), fecha_sincronizacion` — una fila por reseña de Google ya sincronizada; el front nunca consulta la API de Google directamente, siempre lee esta tabla.

**ConfiguracionEmpresa** (esquema definitivo, ver [`bbdd/fase5-1-bbdd.md`](./bbdd/fase5-1-bbdd.md))
`id (fijo a 1), nombre_empresa, logo_ruta (nullable), logo_nombre_original (nullable), updated_at, actualizado_por_admin_id (nullable)` — fila única (mismo patrón que `configuracion_ubicacion`), con `INSERT` inicial (a diferencia de `ConexionGoogleBusiness`): la fila siempre existe, sembrada con el nombre real del negocio; el logo nace sin configurar. Lectura abierta a cualquier usuario autenticado (se muestra en la cabecera de toda la app); escritura solo `ADMIN`.

Relaciones: `TipoUsuario 1—N Empleado`, `TipoUsuario 1—N Permiso`, `Vista 1—N Permiso`, `Empleado 1—N Fichaje`, `Empleado 1—N SolicitudVacaciones`, `Empleado 1—N Nomina`, `Proveedor 1—N Factura`, `Empleado 1—N ConexionGoogleBusiness` (en la práctica, 0 o 1 fila — quién la conectó).

**Modelo de datos de los planes de cumplimiento normativo** (VeriFactu, registro horario 2027): no se duplica aquí para evitar que este documento se desincronice de sus propios planes de detalle — ver el modelo completo en [`bbdd/integracion_verifactu-bbdd.md`](./bbdd/integracion_verifactu-bbdd.md) (`ConfiguracionEmisorFiscal`, `RegistroFacturacionVerifactu`) y [`bbdd/fichajes_vacaciones_2027-bbdd.md`](./bbdd/fichajes_vacaciones_2027-bbdd.md) (`FichajeHistorialCorreccion`, nuevas columnas de `Fichaje`/`SolicitudVacaciones`).

## Desglose de fases

### Fase 0 — Base transversal (prerequisito de todo lo demás)
- [Backend, media] Modelo `Empleado` + repositorio + CRUD (solo Admin).
- [Backend, media] Spring Security + JWT: login, filtro de autenticación, autoridades resueltas dinámicamente desde `tipos_usuarios`/`vistas`/`permisos`.
- [Backend, baja] `FileStorageService` genérico (guardar/recuperar ficheros por tipo).
- [Frontend, media] Login, interceptor JWT, guards de ruta por rol, layout base (menú según rol).
- [Frontend, media] Pantalla de gestión de empleados (alta/baja/edición) — solo Admin.
- [BBDD, baja] Migraciones iniciales (Flyway/Liquibase recomendado en vez de `ddl-auto=update`, ver Decisiones técnicas).

### Fase 1 — Fichaje y Vacaciones de empleados

Dividida en 4 vistas independientes, cada una con su propio plan de detalle (BBDD + Backend + Frontend, cuando aplica):

| Vista | Qué es | Planes de detalle |
|---|---|---|
| **1.1** | Fichaje — empleado (botón Iniciar/Finalizar tipo Woffu) | [`bbdd/fase1-1-bbdd.md`](./bbdd/fase1-1-bbdd.md) · [`back/fase1-1-back.md`](./back/fase1-1-back.md) · [`front/fase1-1-front.md`](./front/fase1-1-front.md) |
| **1.2** | Vacaciones — empleado (calendario, cupo anual, solicitud) | [`bbdd/fase1-2-bbdd.md`](./bbdd/fase1-2-bbdd.md) · [`back/fase1-2-back.md`](./back/fase1-2-back.md) · [`front/fase1-2-front.md`](./front/fase1-2-front.md) |
| **1.3** | Fichaje — Admin (todos los empleados, corrección, umbral de horas por empleado, resumen diario/semanal/mensual) | [`bbdd/fase1-3-bbdd.md`](./bbdd/fase1-3-bbdd.md) · [`back/fase1-3-back.md`](./back/fase1-3-back.md) · [`front/fase1-3-front.md`](./front/fase1-3-front.md) *(añade solo una columna a `empleados`; el resto reutiliza el esquema de 1.1)* |
| **1.4** | Vacaciones — Admin (bandeja global de pendientes, por empleado, aprobar/rechazar, asignación de oficio, configuración de días anuales global y por empleado) | [`bbdd/fase1-4-bbdd.md`](./bbdd/fase1-4-bbdd.md) · [`back/fase1-4-back.md`](./back/fase1-4-back.md) · [`front/fase1-4-front.md`](./front/fase1-4-front.md) *(la capacidad de configuración, añadida en una revisión posterior, sí requirió migraciones nuevas — tabla `configuracion_vacaciones` y `empleados.dias_vacaciones_anuales` pasa a nullable)* |

Resumen (ver cada documento para el desglose completo de subtareas):

- [Backend, media] Modelo `Fichaje`, endpoint fichar entrada/salida (valida que no haya doble entrada sin salida previa **a nivel de índice único de BBDD**, no solo en código), cálculo de horas trabajadas — siempre en el backend.
- [Backend, media] Verificación de ubicación al fichar entrada (**GPS + geovalla, decisión confirmada con el usuario tras evaluar también la IP del router**, descartada por depender de IP fija): el front envía `{lat, lng}`, el backend valida la distancia (Haversine) contra la ubicación del restaurante y un radio de tolerancia; fuera de rango, 403. Se audita la ubicación de cada entrada (`fichajes.lat_entrada`/`lng_entrada`). La ubicación/radio se guardan en BBDD (`configuracion_ubicacion`, una sola fila) y son **editables por el Admin en caliente** (confirmado con el usuario), no una propiedad fija que requiera redesplegar. Detalle completo en `back/fase1-1-back.md` y `back/fase1-3-back.md`.
- [Backend, baja] Modelo `DiaFestivo` + `SolicitudVacaciones`, cálculo centralizado de días laborables (excluyendo fines de semana y festivos) y de disponibilidad anual (cupo `dias_vacaciones_anuales`, 23 por defecto, **prorrateado el año de alta** según `fecha_alta`; completo en los años siguientes).
- [Backend, baja] Columna `empleados.horas_jornada_diaria` (nullable, 1.3): el mínimo de horas que resalta en rojo la vista Admin de Fichajes es **por empleado** (jornada parcial), no un único valor fijo para todo el restaurante; si no está configurado, se usa el valor global por defecto (8h).
- [Backend, baja] Endpoints de consulta: fichajes/vacaciones propios (empleado) / de todos con filtros por empleado y rango de fechas (admin, protegido con la authority `EMPLEADOS:EDICION` — ver nota de diseño más arriba), incluyendo resumen diario/semanal/mensual de fichajes y una bandeja global de solicitudes de vacaciones pendientes de todos los empleados.
- [Backend, baja] Corrección manual de fichajes, aprobación/rechazo de solicitudes de vacaciones, y **asignación de oficio** de días de vacaciones por el Admin (sin solicitud previa del empleado, ej. cierre obligatorio de empresa) — con traza de quién y cuándo en todos los casos.
- [Frontend, media] Vista "Mi Fichaje" para empleado (botón Iniciar/Finalizar, cronómetro en signal — la app es zoneless).
- [Frontend, media] Vista "Mis Vacaciones" para empleado (resumen anual, calendario, solicitud, cancelación).
- [Frontend, alta] Componente reutilizable `ui-calendar` (mes/año + marcadores por variante semántica), construido en 1.2 y reutilizado en 1.4.
- [Frontend, alta] Vista Admin de Fichajes: tabla diaria filtrable por empleado/fecha + resúmenes semanal y mensual, todos en rojo bajo el mínimo de horas de cada empleado.
- [Frontend, media] Vista Admin de Vacaciones: bandeja global de pendientes, detalle por empleado (calendario + listado), aprobar/rechazar, asignar de oficio.
- [Frontend, baja] Exportación a CSV/Excel de fichajes (opcional, valorar si entra en esta fase o se pospone).

**Futuro (fuera de alcance de Fase 1)**: la IP pública del router del restaurante queda descartada como mecanismo principal (depende de IP fija, ver el análisis en `back/fase1-1-back.md`), pero podría añadirse más adelante como **factor secundario opcional** junto al GPS, si el restaurante llega a tener IP fija — no como sustituto.

### Fase 2 — Gestión de facturas (proveedores y suministros)

Dividida en 3 bloques, cada uno con su propio plan de detalle (BBDD + Backend + Frontend):

| Bloque | Qué es | Planes de detalle |
|---|---|---|
| **2.1** | Modelo (`Proveedor` con `tipo`, `Factura`), listado en 2 pestañas (Facturas de proveedores / Facturas de suministros) con búsqueda avanzada (proveedor/estado de pago/mes) y paginación de 10 en 10, alta manual (datos + archivo adjunto obligatorio — foto o PDF, la foto se convierte a PDF automáticamente —, sin OCR) | [`bbdd/fase2-1-bbdd.md`](./bbdd/fase2-1-bbdd.md) · [`back/fase2-1-back.md`](./back/fase2-1-back.md) · [`front/fase2-1-front.md`](./front/fase2-1-front.md) |
| **2.2** | Exportación del listado completo filtrado a Excel (campos de negocio, sin id/CIF/email/número de factura) o a ZIP con los archivos originales del periodo | [`back/fase2-2-back.md`](./back/fase2-2-back.md) · [`front/fase2-2-front.md`](./front/fase2-2-front.md) *(sin migraciones nuevas, reutiliza el esquema de 2.1)* |
| **2.3** | Extracción automática (OCR) al subir una foto/PDF: propone valores solo para fecha de impresión/entrega e importe base/IVA/total (el proveedor siempre se selecciona a mano) — el PDF/imagen original, siempre conservado y descargable, cubre cualquier otro dato | [`bbdd/fase2-3-bbdd.md`](./bbdd/fase2-3-bbdd.md) · [`back/fase2-3-back.md`](./back/fase2-3-back.md) · [`front/fase2-3-front.md`](./front/fase2-3-front.md) |

Resumen (ver cada documento para el desglose completo de subtareas):

- [Backend, media] Modelo `Proveedor` (con `tipo`, que determina la pestaña) + alta rápida embebida en el flujo de alta de factura; modelo `Factura` con `estado_pago` (negocio) separado de `estado_extraccion` (pipeline de OCR, añadido en 2.3).
- [Backend, media] Listado paginado a nivel de BBDD (no en memoria, a diferencia de nóminas — las facturas crecen sin límite con los años) con filtros combinables por proveedor/estado de pago/periodo, separado por `tipo`.
- [Backend, media] Conversión automática de una foto subida (JPEG/PNG/WEBP) a PDF de una sola página (Apache PDFBox) antes de guardarla — el archivo almacenado queda siempre en un formato consistente con independencia del origen.
- [Backend, alta] Exportación a `.xlsx` (Apache POI, *streaming*, título y cabeceras traducidos por el front) y a `.zip` (`java.util.zip`, archivos originales) del conjunto completo filtrado, ignorando la paginación de la UI.
- [Backend, alta] Extracción OCR gratuita (Tesseract vía Tess4J + PDFBox para PDF) detrás de una interfaz (`FacturaExtractionService`), con parseo heurístico propio (fecha de impresión/entrega por etiqueta, sin fallback; importe base/IVA/total por etiqueta explícita o tabla-resumen de tramos de IVA como último recurso por proporción) — nunca persiste nada sin confirmación explícita del Admin; ni `producto` (eliminado del modelo) ni el proveedor se autocompletan nunca (sin heurística fiable posible / decisión de no arriesgar una coincidencia automática equivocada, ver nota histórica en `back/fase2-3-back.md`). No hay ningún mecanismo de "campos dinámicos": el PDF/imagen original, siempre conservado, es la vía para consultar cualquier otro dato de la factura (ver nota histórica en `bbdd/fase2-3-bbdd.md`).
- [Frontend, alta] Vista Admin con componente `ui-tabs` (nuevo) para las 2 pestañas, filtros avanzados (con el periodo inicializado al mes actual), tabla paginada (Empresa/Fecha de impresión/Fecha de entrega/Base imponible/IVA/Cuantía/Estado), modal de alta con todos los campos obligatorios salvo número de factura/estado de pago y alta rápida de proveedor embebida (solo nombre obligatorio + teléfono).
- [Frontend, media] Dos botones de exportación (Excel y ZIP) junto a los filtros, deshabilitados sin un periodo seleccionado, que respetan los filtros activos pero no la paginación.
- [Frontend, alta] Paso opcional de "Extraer datos automáticamente" dentro del modal de alta (fechas de impresión/entrega, importes — nunca el proveedor) y aviso visual de "pendiente de revisar".
- **Riesgo alto** en 2.3 — ver sección de riesgos. El flujo manual de 2.1/2.2 funciona de forma autónoma y no depende de que el OCR esté terminado.

### Fase 3 — Gestión de nóminas

**Implementada antes que la Fase 2** (decisión del usuario: es más rápida de construir y ambos módulos son independientes entre sí, ver "Notas adicionales"). Dividida en 3 bloques, cada uno con su propio plan de detalle (BBDD + Backend + Frontend, cuando aplica):

| Bloque | Qué es | Planes de detalle |
|---|---|---|
| **3.1** | Listado con filtros (empleado, periodo mes/año, estado) y paginación, subida individual de un PDF y descarga | [`bbdd/fase3-1-bbdd.md`](./bbdd/fase3-1-bbdd.md) · [`back/fase3-1-back.md`](./back/fase3-1-back.md) · [`front/fase3-1-front.md`](./front/fase3-1-front.md) |
| **3.2** | Subida masiva vía ZIP con matching automático por convención de nombre de fichero, y asignación manual de los archivos que no se pudieron matchear | [`back/fase3-2-back.md`](./back/fase3-2-back.md) · [`front/fase3-2-front.md`](./front/fase3-2-front.md) *(sin migraciones nuevas, reutiliza el esquema de 3.1)* |
| **3.3** | Envío por email con modal de confirmación, registro de `fecha_envio`/`enviada_por_admin_id` | [`back/fase3-3-back.md`](./back/fase3-3-back.md) · [`front/fase3-3-front.md`](./front/fase3-3-front.md) *(sin migraciones nuevas, reutiliza el esquema de 3.1)* |

Resumen (ver cada documento para el desglose completo de subtareas):

- [Backend, baja] Modelo `Nomina` — una fila solo existe una vez se sube un archivo; "pendiente" (sin subir) es un estado calculado al listar, no un valor persistido (ver `bbdd/fase3-1-bbdd.md`).
- [Backend, media] `FileStorageService` (primera implementación real del proyecto, ya prevista desde Fase 0) + endpoint de subida de un PDF individual, asignado manualmente a un empleado y periodo concretos, y de descarga para verificar el contenido antes de enviarlo.
- [Backend, alta] Subida de un ZIP con varias nóminas: extracción y asociación de cada PDF a un empleado por convención de nombre de fichero (`DNI_AAAA-MM.pdf`); lo que no se pueda matchear automáticamente queda disponible para asignación manual, sin perder el archivo.
- [Backend, media] Spring Boot Starter Mail (primera vez que el proyecto envía email): servicio de envío con el PDF adjunto, registro de `fecha_envio` y `enviada_por_admin_id`.
- [Backend, baja] Endpoint "enviar" individual — nunca automático al subir; reenviar una nómina ya enviada está permitido. Envío masivo no incluido en esta ronda de planificación (queda como extensión natural si se pide más adelante, ver `back/fase3-3-back.md`).
- [Frontend, media] Vista Admin: filtros por empleado y periodo, tabla paginada con nombre/email/estado de cada empleado (pendiente / adjuntada / enviada), subida individual y por ZIP, descarga, y envío con modal de verificación. Módulo completo (ruta, menú y endpoints) exclusivo del rol Admin; el Empleado no tiene ninguna vista de nóminas en la app — la ruta, el icono y la entrada de menú ya existían desde Fase 0, solo faltaba el componente.
- [Frontend, media] Nuevo componente reutilizable `ui-file-input` (primero del proyecto para subir ficheros), envolviendo un `<input type="file">` nativo con el mismo patrón `ControlValueAccessor` que `ui-select`/`ui-input`.

### Fase 4 — Gestión de valoraciones (Google)

Dividida en 2 bloques, cada uno con su propio plan de detalle (BBDD + Backend + Frontend, cuando aplica):

| Bloque | Qué es | Planes de detalle |
|---|---|---|
| **4.1** | Conexión OAuth con la cuenta de Google Business Profile del restaurante, sincronización periódica de reseñas (job programado + sincronización manual bajo demanda), panel de estado de conexión | [`bbdd/fase4-1-bbdd.md`](./bbdd/fase4-1-bbdd.md) · [`back/fase4-1-back.md`](./back/fase4-1-back.md) · [`front/fase4-1-front.md`](./front/fase4-1-front.md) |
| **4.2** | Listado paginado y resumen (media, distribución por estrellas) de las reseñas ya sincronizadas | [`back/fase4-2-back.md`](./back/fase4-2-back.md) · [`front/fase4-2-front.md`](./front/fase4-2-front.md) *(sin migraciones nuevas, reutiliza el esquema de 4.1)* |

Resumen (ver cada documento para el desglose completo de subtareas):

- [Backend, alta] Flujo OAuth 2.0 completo contra Google (primera integración OAuth del proyecto): URL de consentimiento, intercambio de código por tokens, descubrimiento de la ubicación de Google Business Profile, cifrado del *refresh token* antes de guardarlo (primer dato cifrado en reposo del proyecto) — todo detrás de una interfaz `GoogleBusinessProfileClient`, mismo patrón que `FacturaExtractionService`/`FileStorageService`.
- [Backend, media] `ValoracionSincronizacionService` + `@Scheduled` (primera tarea programada del proyecto, cada 6h por defecto): *upsert* de reseñas por `googleReviewId`, nunca duplica; cualquier fallo queda registrado (`ultimoError`) sin tumbar el resto de la app. Reutilizable también desde un endpoint de sincronización manual.
- [Backend, baja] Endpoints de solo lectura (listado paginado filtrable por puntuación, resumen agregado media/total/distribución) — nunca llaman a la API de Google, leen siempre de BBDD.
- [Frontend, alta] Vista Admin nueva `/valoraciones` (ruta, menú e icono nuevos): tarjeta "Conectar con Google" cuando no hay conexión (redirección de página completa a Google, no AJAX); panel de estado, sincronización manual y desconexión cuando sí la hay.
- [Frontend, media] Cabecera de resumen (`ui-card` x3, mismo patrón que `mis-vacaciones`) y tabla paginada de reseñas con filtro por puntuación, sin ninguna acción de edición (solo lectura).
- **Riesgo alto** — aprobación de acceso de Google a la API de reseñas (manual, puede tardar días); no bloquea el desarrollo pero sí el uso con la cuenta real antes de que Google la apruebe. Ver "Riesgos y dependencias".
- **Independiente de las Fases 1, 2 y 3** — solo depende de la Fase 0 (empleados + catálogo de permisos), puede desarrollarse en paralelo a cualquiera de ellas.

### Fase 5 — Configuración (datos de empresa, y consolidación de ubicación/vacaciones)

Un único bloque, con su propio plan de detalle (BBDD + Backend + Frontend): [`bbdd/fase5-1-bbdd.md`](./bbdd/fase5-1-bbdd.md) · [`back/fase5-1-back.md`](./back/fase5-1-back.md) · [`front/fase5-1-front.md`](./front/fase5-1-front.md).

- [BBDD, baja] Tabla `configuracion_empresa` (fila única, mismo patrón que `configuracion_ubicacion`): nombre del negocio y ruta del logo. Reutiliza `EMPLEADOS:EDICION`, sin vista/permiso nuevo.
- [Backend, media] `ConfiguracionController` (ya existente) se amplía con una tercera sección: lectura (`GET`) abierta a cualquier usuario autenticado (el nombre/logo se muestran en la cabecera de toda la app), escritura (`PUT`, multipart) restringida a `EMPLEADOS:EDICION`. Reutiliza `FileStorageService` para el logo, mismo patrón que el archivo de una factura.
- [Frontend, alta] Primera vista dedicada `/configuracion` del proyecto: datos de empresa (nombre + logo con previsualización) y, consolidados aquí, los formularios de ubicación de fichaje y días de vacaciones por defecto que hasta ahora vivían embebidos dentro de `fichajes-admin`/`vacaciones-admin`. La cabecera de la aplicación muestra el logo/nombre a todos los usuarios, no solo dentro de esta vista de Admin.
- **Independiente de las Fases 1, 2, 3 y 4** — solo depende de la Fase 0.

### Fase 6 — Pulido y cierre
- [Frontend, baja] Dashboard/resumen (horas del mes, facturas pendientes de revisar, nóminas pendientes de enviar, valoración media reciente).
- [Backend/Frontend, media] Tests de integración de los flujos críticos (fichaje, envío de nómina, subida de factura, sincronización de valoraciones).
- [Transversal, media] Revisión de seguridad (control de acceso a ficheros ajenos, validación de tipos/tamaños de archivo subido, rotación de la clave de cifrado de `refresh_token_cifrado`).
- [Transversal, baja] Documentación de despliegue.

## Decisiones técnicas

- **Monolito modular vs microservicios**: se descarta microservicios por sobreingeniería para el tamaño actual del proyecto (un restaurante, equipo pequeño). Los paquetes por dominio dejan la puerta abierta a separar el OCR más adelante si hiciera falta.
- **Migraciones de BBDD**: se recomienda introducir **Flyway** (o Liquibase) en vez de depender de `spring.jpa.hibernate.ddl-auto=update` (configuración actual) en cuanto el modelo de datos empiece a estabilizarse. `ddl-auto=update` es cómodo en el arranque pero no es seguro para evolucionar el esquema en equipo ni en producción.
- **Extracción OCR/IA de facturas — decidido en la Fase 2.3**: se opta por la opción gratuita/self-hosted como punto de partida, a petición explícita del usuario de no depender de un servicio de pago:
  1. **OCR self-hosted (Tesseract vía Tess4J) + PDFBox + parseo heurístico propio (elegido, Fase 2.3)**: gratuito, sin dependencia de un servicio externo ni coste por documento. La precisión en facturas de formato variado es sensiblemente peor que un LLM multimodal — mitigado con revisión humana obligatoria antes de confirmar (nunca se persiste sin confirmación) y con `FacturaExtractionService` como interfaz, para poder sustituir la implementación sin tocar el resto del código si la precisión resulta insuficiente en la práctica. Requiere el binario nativo de Tesseract instalado en cada entorno (no es una dependencia JVM autosuficiente). Detalle completo en `back/fase2-3-back.md`.
  2. **LLM multimodal (alternativa futura si la precisión de Tesseract no es suficiente)**: enviar la imagen/PDF a un modelo con visión (p.ej. la API de Claude) pidiendo un JSON estructurado. Mejor precisión con facturas variadas y sin post-procesado propio, a cambio de coste por documento y dependencia de un servicio externo (revisar tratamiento de datos si las facturas tienen información sensible). Al estar `FacturaExtractionService` diseñado como interfaz desde el principio, este cambio no requeriría tocar el controller ni el flujo de confirmación.
  3. **OCR cloud especializado** (Azure Document Intelligence, AWS Textract, Google Document AI): descartado por ahora — más integración y coste por volumen que las otras dos opciones, sin ventaja clara para el tamaño de este proyecto.

  En cualquier caso, el usuario siempre revisa/confirma los valores propuestos antes de que la factura pase a `REVISADA`. La extracción solo propone fecha de impresión/entrega e importe base/IVA/total — nunca el proveedor, que siempre se selecciona o se da de alta a mano (una versión anterior sí proponía proveedor por *fuzzy matching* de texto; se retiró, ver `back/fase2-3-back.md`); no se guarda ni el texto OCR crudo ni ningún dato adicional detectado fuera de esos campos — el PDF/imagen original (siempre conservado y descargable) es la única fuente de verdad para cualquier otro dato de la factura, tras comprobar en la práctica que un mecanismo de campos dinámicos (`factura_campos_dinamicos`, patrón EAV, ver nota histórica en `bbdd/fase2-3-bbdd.md`) generaba más ruido que valor con facturas reales.
- **Envío de email**: usar `spring-boot-starter-mail` con un SMTP (Gmail/Office365 corporativo, o un proveedor transaccional tipo SendGrid/Amazon SES si se quiere mejor entregabilidad y trazabilidad de envíos).
- **Cálculo de horas de fichaje**: se calcula en el backend en el momento de cerrar el fichaje (hora_salida), nunca en el frontend, para evitar inconsistencias por zona horaria/reloj del cliente.

## Riesgos y dependencias

- **Riesgo alto — precisión del OCR gratuito (Tesseract)**: sensiblemente peor que un LLM multimodal en facturas de formato libre; en particular, el proveedor y la fecha de impresión (obligatoria) siempre requieren selección/entrada manual, nunca se rellenan solos. Mitigación: paso de revisión humana obligatorio antes de dar una factura por definitiva (nunca se persiste sin confirmación); `FacturaExtractionService` diseñado como interfaz para poder sustituirlo por un motor de pago más adelante sin tocar el resto del código, si resulta necesario. Detalle en `back/fase2-3-back.md`.
- **Riesgo medio — dependencia nativa de Tesseract**: a diferencia del resto de dependencias del proyecto (JARs de Maven autosuficientes), Tess4J requiere el binario de Tesseract y los datos de idioma instalados en cada entorno (desarrollo y despliegue) — hay que documentarlo como prerequisito, no solo declararlo en `pom.xml`.
- **Riesgo medio — formatos de imagen no soportados**: Tesseract/`ImageIO` no leen HEIC (formato por defecto de fotos de iPhone) sin conversión previa — fuera de alcance salvo que se confirme como caso de uso real.
- **Riesgo medio — matching automático de nóminas en un ZIP**: si el ZIP no sigue una convención de nombres consistente, el sistema no podrá asociar automáticamente cada PDF a su empleado. Mitigación: definir una convención de nombre de fichero desde el principio (p.ej. `DNI_AAAA-MM.pdf`) y dejar siempre una pantalla de asignación manual como red de seguridad.
- **Riesgo medio — entregabilidad de emails con nóminas adjuntas**: datos sensibles (nóminas) por email requieren cuidado; valorar cifrado del PDF con contraseña (p.ej. el DNI del empleado) o enviar un enlace de descarga temporal en vez del adjunto directo, según las políticas de protección de datos que se quieran aplicar.
- **Dependencia**: los módulos de Fichaje, Vacaciones, Facturas, Nóminas, Valoraciones y Configuración dependen todos de que la Fase 0 (empleados + auth) esté terminada primero.
- **Riesgo medio — mantenimiento del catálogo de festivos**: se siembra por SQL año a año (ver `bbdd/fase1-2-bbdd.md`); si no se actualiza, el cálculo de días laborables de vacaciones sobreestimará ligeramente los días consumidos a partir del año no sembrado.
- **Riesgo bajo — volumen de almacenamiento**: facturas y nóminas en disco local crecen con el tiempo; planear rotación/backup del directorio `uploads/` o migración a almacenamiento externo si el volumen lo justifica.
- **Riesgo alto — aprobación de acceso de Google a la API de reseñas (Fase 4)**: la API de reseñas de Google Business Profile ("Google My Business API v4") requiere solicitud y aprobación manual de Google, que puede tardar días — no bloquea el desarrollo del código (se puede probar contra cuentas de prueba autorizadas explícitamente) pero sí el uso en producción con la cuenta real de "Sidrería La Zapica"; iniciar el trámite lo antes posible. Detalle en `back/fase4-1-back.md`.
- **Riesgo medio — dependencia de un servicio externo fuera de control del proyecto (Fase 4)**: cambios de cuota, deprecación de la API (ya ha ocurrido una vez con "Google My Business API") o revocación del acceso desde la propia cuenta de Google rompen la sincronización de valoraciones; mitigado por el registro visible de errores (`ultimoError`) y porque un fallo aquí nunca afecta al resto de la app.
- **Riesgo alto — los planes de cumplimiento normativo dependen de textos legales aún en tramitación**: ni el Real Decreto de VeriFactu ni el de registro horario digital tenían, al escribir este documento, su redacción final publicada — los algoritmos/esquemas implementados en `integracion_verifactu-back.md` y `fichajes_vacaciones_2027-back.md` son la mejor interpretación pública disponible en ese momento, aislados detrás de interfaces propias para poder ajustarlos sin rediseño si el texto definitivo difiere. No se recomienda planificar su despliegue a producción hasta acercarse a la fecha real de entrada en vigor de cada uno.

## Criterios de aceptación técnicos (a nivel de proyecto)

- Un Empleado puede loguearse, fichar entrada/salida, y ver únicamente sus propios fichajes; fichar entrada exige que su ubicación GPS esté dentro del radio configurado del restaurante (403 si no).
- Un Empleado puede solicitar y cancelar (mientras esté pendiente) sus propios días de vacaciones, ver su cupo anual y disponible, y ver en su calendario los festivos, sus días aprobados y sus días pendientes; no puede ver ni resolver solicitudes de otro empleado.
- Un Admin puede ver y corregir los fichajes de cualquier empleado (con aviso visual de días, semanas y meses por debajo del mínimo de horas de **ese** empleado, jornada parcial incluida), y puede ver una bandeja global de solicitudes de vacaciones pendientes de todos los empleados, aprobar/rechazar las de cualquier empleado, y asignarle días de vacaciones de oficio sin que este los solicite antes.
- Un Empleado **no** puede acceder ni a la vista ni a los endpoints de Facturas de proveedores ni de Nóminas (intentar acceder directamente por URL a esas rutas del front redirige/bloquea, y el backend responde 403 ante cualquier llamada a esos endpoints con un token de rol Empleado).
- Un Admin puede loguearse, gestionar empleados, ver y corregir fichajes de todos, gestionar facturas de proveedores y de suministros (listado paginado de 10 en 10, filtrable por proveedor/estado de pago/mes, exportable a Excel o a ZIP con los archivos originales del periodo), y subir/enviar nóminas.
- Toda factura subida queda asociada a un proveedor (de tipo Proveedor o Suministro) y es consultable por proveedor/estado de pago/mes; si se dio de alta con extracción automática, los datos propuestos (fecha de impresión/entrega, importes — nunca el proveedor) son editables antes de confirmarse, y su origen queda registrado (`estado_extraccion`).
- El envío de una nómina por email requiere una acción explícita del Admin (nunca ocurre solo al subir el archivo) y queda registrado (fecha y quién lo envió). El único canal por el que un Empleado recibe su nómina es el email.
- Ningún endpoint de datos de otro empleado (fichajes) es accesible por un Empleado que no sea el propietario.
- Un Admin puede conectar la cuenta de Google Business Profile del restaurante vía OAuth, ver el estado de esa conexión (incluido el último error, si lo hay), forzar una sincronización manual y desconectarla; las reseñas sincronizadas son consultables con resumen (media, distribución por estrellas) y filtro por puntuación. Un Empleado no tiene acceso ni a la vista ni a los endpoints de Valoraciones.
- Cualquier usuario autenticado (Admin o Empleado) ve el nombre y logo de la empresa en la cabecera de la app; solo un Admin puede editarlos, junto con la ubicación/radio de la geovalla de fichaje y los días de vacaciones anuales por defecto, desde la vista única `/configuracion`.

## Notas adicionales

- **Orden real de implementación (revisado tras cerrar la Fase 1)**: este plan asumía originalmente 0→1→2→3→4 (con la Fase 2 dividida en un bloque base y un bloque de OCR), pero el usuario decidió implementar la **Fase 3 (Nóminas) antes que la Fase 2 (Facturas)** por ser más rápida de construir — ambas son independientes entre sí (ninguna depende de datos ni de clases de la otra, solo de la Fase 0), así que el orden entre ellas no tiene impacto técnico, solo de priorización. El orden real pasa a ser 0→1→3→2, y ahora con Valoraciones añadida, 0→1→3→2→4→5 (ver el punto siguiente sobre por qué la Fase 4 no tiene por qué ir forzosamente después de la 2, solo se numera así por orden de incorporación al plan).
- **Renumeración de la Fase 2 (revisado al detallar sus planes de subfase)**: el desglose original separaba "Fase 2" (MVP sin OCR) de "Fase 2.1" (OCR) como dos fases distintas. Al planificar el detalle se consolidan en una única **Fase 2** con tres subfases — **2.1** (modelo + listado en pestañas + alta manual), **2.2** (exportación a Excel) y **2.3** (lo que antes era "Fase 2.1": extracción OCR + campos dinámicos) — siguiendo el mismo esquema de numeración por bloques ya usado en la Fase 1 (1.1–1.4) y la Fase 3 (3.1–3.3). No cambia el alcance funcional, solo el agrupamiento y el orden en que se detalla (la exportación a Excel, no prevista en el desglose original, se intercala como 2.2 antes del OCR porque no depende de él).
- **Fase 4 — Valoraciones (añadida en una revisión posterior)**: no estaba en el desglose original; se incorpora como Fase 4, empujando lo que era "Fase 4 — Pulido y cierre" a **Fase 5**. Es **independiente de las Fases 1, 2 y 3** (solo depende de la Fase 0), así que su posición en la numeración es por orden de incorporación al plan, no por una dependencia real — podría haberse implementado en paralelo a cualquiera de ellas, y de hecho es buena candidata a desarrollarse mientras se espera la aprobación de Google del punto siguiente.
- **Fase 5 — Configuración (añadida en una revisión posterior)**: mismo caso que la Fase 4 — no estaba en el desglose original; se incorpora como Fase 5, empujando "Pulido y cierre" a **Fase 6**. Es **independiente de las Fases 1, 2, 3 y 4** (solo depende de la Fase 0). Nace acotada a los datos de empresa (nombre y logo), pero de paso consolida en una única vista los formularios de `configuracion_ubicacion`/`configuracion_vacaciones` que hasta entonces vivían embebidos dentro de `fichajes-admin`/`vacaciones-admin` (ver `front/fase5-1-front.md`).
- **Planes de cumplimiento normativo fuera de esta numeración secuencial** (`integracion_verifactu-*.md`, `fichajes_vacaciones_2027-*.md`): no son "Fase 6/7" de este plan porque no son funcionalidad de producto priorizada, sino preparación para obligaciones legales con fecha de entrada en vigor propia (VeriFactu, registro horario digital) — se documentan aparte. Su numeración de migraciones (V39-V41 y V42-V44 respectivamente) y la de la Fase 5 de este plan (V45, `bbdd/fase5-1-bbdd.md`) son **provisionales**: cada documento indica qué asume que se aplica antes; el orden real depende de cuál se implemente primero, y el que vaya primero reclama los números más bajos — renumerar el resto en consecuencia al implementar.
- Este plan asume que las Fases se implementan en el orden 0→1→2→3→4→5→6, pero Fichajes/Vacaciones (Fase 1), Facturas (Fase 2.1/2.2, sin OCR), Nóminas (Fase 3), Valoraciones (Fase 4) y Configuración (Fase 5) son independientes entre sí y podrían desarrollarse en paralelo si hay más de una persona disponible, siempre que la Fase 0 ya esté cerrada.
- Dentro de la Fase 1, las vistas 1.1 (Fichaje empleado) y 1.2 (Vacaciones empleado) son independientes entre sí y se pueden desarrollar en paralelo; 1.3 depende de que 1.1 esté terminada (reutiliza su esquema y sus clases) y 1.4 depende de que 1.2 esté terminada, por la misma razón.
- Dentro de la Fase 2, **2.1 y 2.2 son independientes de 2.3** (el listado, los filtros y la exportación no dependen de que el OCR exista) — se pueden desarrollar y desplegar sin bloquear en el OCR, igual que ya se recomendaba en el desglose original. **2.2 depende de 2.1** (reutiliza `FacturaRepository`/entidades) y **2.3 depende de 2.1** (extiende su endpoint de alta) pero no de 2.2.
- Antes de arrancar la Fase 2.3 (OCR), conviene una prueba de concepto rápida con 5-10 facturas reales variadas (proveedores y suministros) para validar la precisión de Tesseract sobre casos reales antes de dar por buena la decisión tomada en "Decisiones técnicas", en vez de descubrirlo ya con el pipeline completo construido.
- Dentro de la Fase 4, **4.2 depende de 4.1** (reutiliza `ValoracionRepository`/la tabla ya poblada por la sincronización) pero no al revés. Antes de arrancar la Fase 4.1, conviene iniciar cuanto antes el trámite de aprobación de acceso a la API de Google (ver "Riesgos y dependencias") — es lo único de esta fase que el equipo no controla directamente y lo que más probablemente marque el ritmo real de cuándo puede probarse contra la cuenta de producción del restaurante.
