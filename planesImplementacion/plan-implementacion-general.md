# Plan de Implementación: GestionRestaurante (visión general)

## Contexto y objetivo

Aplicación de gestión integral para un restaurante, compuesta por tres módulos funcionales sobre una base común de empleados y autenticación:

1. **Fichaje de empleados** — registro de entrada/salida tipo Woffu simplificado, con vista propia (empleado) y vista de supervisión (Admin).
2. **Gestión de vacaciones** — calendario de festivos y días propios, solicitud de vacaciones por el empleado (con cupo anual y disponibilidad) y aprobación/rechazo por el Admin.
3. **Gestión de facturas** — facturas de proveedores y de suministros (luz, agua...), en pestañas separadas; digitalizar, extraer datos automáticamente (OCR) y consultar facturas con filtros avanzados, paginación y exportación a Excel.
4. **Gestión de nóminas** — mantener el listado de empleados, subir PDFs/ZIPs de nóminas y enviarlas por email.

Es un único restaurante (sin multi-sede por ahora) con dos roles de usuario: **Admin** y **Empleado**.

Este documento es un plan **general** (arquitectura + fases), no un desglose ticket a ticket. Cada fase deberá convertirse en tickets/planes de detalle más adelante.

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
- **Tess4J + Apache PDFBox + Apache Commons Text** — extracción automática (OCR) de facturas: motor Tesseract, rasterizado de PDF a imagen y *fuzzy matching* de proveedor, respectivamente. Las tres gratuitas (Apache License 2.0); decisión tomada en la Fase 2.3, ver "Decisiones técnicas" y `back/fase2-3-back.md`. Tess4J requiere el binario nativo de Tesseract instalado en cada entorno (no es una dependencia JVM autosuficiente).
- **Spring Boot Starter Validation** (ya incluido) — validación de formularios en los distintos módulos.

## Análisis funcional y supuestos

- **Un solo restaurante**, sin necesidad de modelar "sede" en ninguna entidad (asunción confirmada).
- **Roles**: Admin/RRHH (acceso total) y Empleado (acceso **únicamente** al módulo de Fichaje, y solo a sus propios fichajes). El Empleado **no** tiene acceso a Facturas de proveedores ni a Nóminas desde la app — las nóminas le llegan solo por email; ni la vista ni los endpoints de esos dos módulos son alcanzables por el rol Empleado.
- **Permisos**: dos roles fijos definidos en código (ADMIN / EMPLEADO), no un sistema de permisos configurable desde la app. Cambiar qué ve cada rol implica un cambio de código (guards en Angular + `@PreAuthorize` en Spring), no una pantalla de administración de permisos.
- **Facturas**: dos categorías — facturas de proveedores y facturas de suministros (luz, agua, gas...) — mostradas en dos pestañas de un mismo listado, con búsqueda avanzada (proveedor/estado de pago/mes), paginación de 25 en 25 y exportación a Excel con todos los campos (no solo los mostrados en el listado). Se quiere extracción automática de datos (OCR) al subir una foto/PDF de una factura, no solo carga manual — con capacidad de crear campos nuevos en BBDD automáticamente cuando el OCR detecta datos que no existían antes (implementado como tabla de campos dinámicos, no como alteración de esquema en caliente, ver `back/fase2-3-back.md`). Se asume que el usuario siempre podrá **revisar y corregir** lo extraído antes de darlo por bueno (ningún proceso automático debe ser 100% ciego). Desglose completo en `bbdd/`, `back/` y `front/fase2-1-front.md` a `fase2-3-front.md`.
- **Nóminas**: el envío por email es **manual** (el admin sube el archivo, revisa la asignación empleado↔nómina, y pulsa "Enviar"), nunca automático al subir el archivo.
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
`id, proveedor_id (FK), numero_factura (nullable), producto, fecha_factura, fecha_vencimiento (nullable), importe_base (nullable), iva (nullable), importe_total, mes, anio (derivados de fecha_factura, nunca enviados por el cliente), estado_pago (PENDIENTE|PAGADA), estado_extraccion (MANUAL|PENDIENTE|PROCESADA|ERROR|REVISADA, añadido en Fase 2.3), datos_extraidos_raw (JSON, lo que devolvió el OCR sin editar, añadido en Fase 2.3), ruta_archivo, nombre_archivo_original, subida_por_admin_id, fecha_subida` — `estado_pago` (negocio: si ya se ha pagado) y `estado_extraccion` (pipeline de OCR) son conceptos independientes, no confundir.

**FacturaCampoDinamico** (Fase 2.3, ver [`bbdd/fase2-3-bbdd.md`](./bbdd/fase2-3-bbdd.md))
`id, factura_id (FK), nombre_campo, valor` — patrón Entidad-Atributo-Valor para los campos que el OCR detecta en una factura y no existen como columna fija de `Factura`; única `(factura_id, nombre_campo)`. Es la forma en que "campos nuevos se crean automáticamente" sin necesitar `ALTER TABLE` en caliente (inseguro y no compatible con una entidad JPA estática) ni redesplegar el backend.

**Nomina** (esquema definitivo, ver [`bbdd/fase3-1-bbdd.md`](./bbdd/fase3-1-bbdd.md))
`id, empleado_id (FK), periodo_mes, periodo_anio, ruta_archivo, nombre_archivo_original, estado (ADJUNTADA|ENVIADA), fecha_subida, subida_por_admin_id (FK), fecha_envio (nullable), enviada_por_admin_id (FK, nullable)` — el tercer estado que ve el Admin en la UI, "pendiente" (sin nómina subida), **no es un valor de esta columna**: es la ausencia de fila para un empleado activo en un periodo dado, calculada al listar (ver `back/fase3-1-back.md`).

Relaciones: `TipoUsuario 1—N Empleado`, `TipoUsuario 1—N Permiso`, `Vista 1—N Permiso`, `Empleado 1—N Fichaje`, `Empleado 1—N SolicitudVacaciones`, `Empleado 1—N Nomina`, `Proveedor 1—N Factura`, `Factura 1—N FacturaCampoDinamico`.

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
| **2.1** | Modelo (`Proveedor` con `tipo`, `Factura`), listado en 2 pestañas (Facturas de proveedores / Facturas de suministros) con búsqueda avanzada (proveedor/estado de pago/mes) y paginación de 25 en 25, alta manual (datos + archivo adjunto obligatorio, sin OCR) | [`bbdd/fase2-1-bbdd.md`](./bbdd/fase2-1-bbdd.md) · [`back/fase2-1-back.md`](./back/fase2-1-back.md) · [`front/fase2-1-front.md`](./front/fase2-1-front.md) |
| **2.2** | Exportación a Excel del listado completo filtrado (todos los campos de la factura, no solo los mostrados en pantalla) | [`back/fase2-2-back.md`](./back/fase2-2-back.md) · [`front/fase2-2-front.md`](./front/fase2-2-front.md) *(sin migraciones nuevas, reutiliza el esquema de 2.1)* |
| **2.3** | Extracción automática (OCR) al subir una foto/PDF: propone valores para los campos del formulario y crea automáticamente campos dinámicos (tabla EAV) para los datos detectados que no existen como columna fija | [`bbdd/fase2-3-bbdd.md`](./bbdd/fase2-3-bbdd.md) · [`back/fase2-3-back.md`](./back/fase2-3-back.md) · [`front/fase2-3-front.md`](./front/fase2-3-front.md) |

Resumen (ver cada documento para el desglose completo de subtareas):

- [Backend, media] Modelo `Proveedor` (con `tipo`, que determina la pestaña) + alta rápida embebida en el flujo de alta de factura; modelo `Factura` con `estado_pago` (negocio) separado de `estado_extraccion` (pipeline de OCR, añadido en 2.3).
- [Backend, media] Listado paginado a nivel de BBDD (no en memoria, a diferencia de nóminas — las facturas crecen sin límite con los años) con filtros combinables por proveedor/estado de pago/periodo, separado por `tipo`.
- [Backend, alta] Exportación a `.xlsx` (Apache POI, *streaming*) del conjunto completo filtrado, ignorando la paginación de la UI, con todos los campos de la factura.
- [Backend, alta] Extracción OCR gratuita (Tesseract vía Tess4J + PDFBox para PDF) detrás de una interfaz (`FacturaExtractionService`), con parseo heurístico propio (fecha, importe, *fuzzy match* de proveedor) — nunca persiste nada sin confirmación explícita del Admin; `producto` nunca se autocompleta (sin heurística fiable posible).
- [Backend, media] Campos detectados que no encajan en ningún campo fijo se guardan en una tabla EAV (`factura_campos_dinamicos`) — así se cumple "los campos nuevos se crean automáticamente" sin alterar el esquema en caliente.
- [Frontend, alta] Vista Admin con componente `ui-tabs` (nuevo) para las 2 pestañas, filtros avanzados, tabla paginada (Empresa/Producto/Cuantía/Fecha/Estado), modal de alta con adjunto de archivo obligatorio y alta rápida de proveedor embebida.
- [Frontend, media] Botón de exportación a Excel que respeta los filtros activos pero no la paginación.
- [Frontend, alta] Paso opcional de "Extraer datos automáticamente" dentro del modal de alta, con lista editable de campos adicionales detectados por OCR y aviso visual de "pendiente de revisar".
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

### Fase 4 — Pulido y cierre
- [Frontend, baja] Dashboard/resumen (horas del mes, facturas pendientes de revisar, nóminas pendientes de enviar).
- [Backend/Frontend, media] Tests de integración de los flujos críticos (fichaje, envío de nómina, subida de factura).
- [Transversal, media] Revisión de seguridad (control de acceso a ficheros ajenos, validación de tipos/tamaños de archivo subido).
- [Transversal, baja] Documentación de despliegue.

## Decisiones técnicas

- **Monolito modular vs microservicios**: se descarta microservicios por sobreingeniería para el tamaño actual del proyecto (un restaurante, equipo pequeño). Los paquetes por dominio dejan la puerta abierta a separar el OCR más adelante si hiciera falta.
- **Migraciones de BBDD**: se recomienda introducir **Flyway** (o Liquibase) en vez de depender de `spring.jpa.hibernate.ddl-auto=update` (configuración actual) en cuanto el modelo de datos empiece a estabilizarse. `ddl-auto=update` es cómodo en el arranque pero no es seguro para evolucionar el esquema en equipo ni en producción.
- **Extracción OCR/IA de facturas — decidido en la Fase 2.3**: se opta por la opción gratuita/self-hosted como punto de partida, a petición explícita del usuario de no depender de un servicio de pago:
  1. **OCR self-hosted (Tesseract vía Tess4J) + PDFBox + parseo heurístico propio (elegido, Fase 2.3)**: gratuito, sin dependencia de un servicio externo ni coste por documento. La precisión en facturas de formato variado es sensiblemente peor que un LLM multimodal — mitigado con revisión humana obligatoria antes de confirmar (nunca se persiste sin confirmación) y con `FacturaExtractionService` como interfaz, para poder sustituir la implementación sin tocar el resto del código si la precisión resulta insuficiente en la práctica. Requiere el binario nativo de Tesseract instalado en cada entorno (no es una dependencia JVM autosuficiente). Detalle completo en `back/fase2-3-back.md`.
  2. **LLM multimodal (alternativa futura si la precisión de Tesseract no es suficiente)**: enviar la imagen/PDF a un modelo con visión (p.ej. la API de Claude) pidiendo un JSON estructurado. Mejor precisión con facturas variadas y sin post-procesado propio, a cambio de coste por documento y dependencia de un servicio externo (revisar tratamiento de datos si las facturas tienen información sensible). Al estar `FacturaExtractionService` diseñado como interfaz desde el principio, este cambio no requeriría tocar el controller ni el flujo de confirmación.
  3. **OCR cloud especializado** (Azure Document Intelligence, AWS Textract, Google Document AI): descartado por ahora — más integración y coste por volumen que las otras dos opciones, sin ventaja clara para el tamaño de este proyecto.

  En cualquier caso, el resultado crudo se guarda en `datos_extraidos_raw` y el usuario siempre revisa/confirma antes de que la factura pase a `REVISADA`. Los datos que el OCR detecta y no encajan en ningún campo fijo se guardan como campos dinámicos (`factura_campos_dinamicos`, patrón Entidad-Atributo-Valor) en vez de alterar el esquema de `facturas` en caliente — ver `bbdd/fase2-3-bbdd.md`.
- **Envío de email**: usar `spring-boot-starter-mail` con un SMTP (Gmail/Office365 corporativo, o un proveedor transaccional tipo SendGrid/Amazon SES si se quiere mejor entregabilidad y trazabilidad de envíos).
- **Cálculo de horas de fichaje**: se calcula en el backend en el momento de cerrar el fichaje (hora_salida), nunca en el frontend, para evitar inconsistencias por zona horaria/reloj del cliente.

## Riesgos y dependencias

- **Riesgo alto — precisión del OCR gratuito (Tesseract)**: sensiblemente peor que un LLM multimodal en facturas de formato libre; en particular, `producto` no se extrae de forma fiable por heurística y siempre requiere entrada manual. Mitigación: paso de revisión humana obligatorio antes de dar una factura por definitiva (nunca se persiste sin confirmación); `FacturaExtractionService` diseñado como interfaz para poder sustituirlo por un motor de pago más adelante sin tocar el resto del código, si resulta necesario. Detalle en `back/fase2-3-back.md`.
- **Riesgo medio — dependencia nativa de Tesseract**: a diferencia del resto de dependencias del proyecto (JARs de Maven autosuficientes), Tess4J requiere el binario de Tesseract y los datos de idioma instalados en cada entorno (desarrollo y despliegue) — hay que documentarlo como prerequisito, no solo declararlo en `pom.xml`.
- **Riesgo medio — formatos de imagen no soportados**: Tesseract/`ImageIO` no leen HEIC (formato por defecto de fotos de iPhone) sin conversión previa — fuera de alcance salvo que se confirme como caso de uso real.
- **Riesgo medio — matching automático de nóminas en un ZIP**: si el ZIP no sigue una convención de nombres consistente, el sistema no podrá asociar automáticamente cada PDF a su empleado. Mitigación: definir una convención de nombre de fichero desde el principio (p.ej. `DNI_AAAA-MM.pdf`) y dejar siempre una pantalla de asignación manual como red de seguridad.
- **Riesgo medio — entregabilidad de emails con nóminas adjuntas**: datos sensibles (nóminas) por email requieren cuidado; valorar cifrado del PDF con contraseña (p.ej. el DNI del empleado) o enviar un enlace de descarga temporal en vez del adjunto directo, según las políticas de protección de datos que se quieran aplicar.
- **Dependencia**: los módulos de Fichaje, Vacaciones, Facturas y Nóminas dependen todos de que la Fase 0 (empleados + auth) esté terminada primero.
- **Riesgo medio — mantenimiento del catálogo de festivos**: se siembra por SQL año a año (ver `bbdd/fase1-2-bbdd.md`); si no se actualiza, el cálculo de días laborables de vacaciones sobreestimará ligeramente los días consumidos a partir del año no sembrado.
- **Riesgo bajo — volumen de almacenamiento**: facturas y nóminas en disco local crecen con el tiempo; planear rotación/backup del directorio `uploads/` o migración a almacenamiento externo si el volumen lo justifica.

## Criterios de aceptación técnicos (a nivel de proyecto)

- Un Empleado puede loguearse, fichar entrada/salida, y ver únicamente sus propios fichajes; fichar entrada exige que su ubicación GPS esté dentro del radio configurado del restaurante (403 si no).
- Un Empleado puede solicitar y cancelar (mientras esté pendiente) sus propios días de vacaciones, ver su cupo anual y disponible, y ver en su calendario los festivos, sus días aprobados y sus días pendientes; no puede ver ni resolver solicitudes de otro empleado.
- Un Admin puede ver y corregir los fichajes de cualquier empleado (con aviso visual de días, semanas y meses por debajo del mínimo de horas de **ese** empleado, jornada parcial incluida), y puede ver una bandeja global de solicitudes de vacaciones pendientes de todos los empleados, aprobar/rechazar las de cualquier empleado, y asignarle días de vacaciones de oficio sin que este los solicite antes.
- Un Empleado **no** puede acceder ni a la vista ni a los endpoints de Facturas de proveedores ni de Nóminas (intentar acceder directamente por URL a esas rutas del front redirige/bloquea, y el backend responde 403 ante cualquier llamada a esos endpoints con un token de rol Empleado).
- Un Admin puede loguearse, gestionar empleados, ver y corregir fichajes de todos, gestionar facturas de proveedores y de suministros (listado paginado de 25 en 25, filtrable por proveedor/estado de pago/mes, exportable a Excel con todos los campos), y subir/enviar nóminas.
- Toda factura subida queda asociada a un proveedor (de tipo Proveedor o Suministro) y es consultable por proveedor/estado de pago/mes; si se dio de alta con extracción automática, los datos propuestos (incluidos los campos dinámicos detectados) son editables antes de confirmarse, y su origen queda registrado (`estado_extraccion`).
- El envío de una nómina por email requiere una acción explícita del Admin (nunca ocurre solo al subir el archivo) y queda registrado (fecha y quién lo envió). El único canal por el que un Empleado recibe su nómina es el email.
- Ningún endpoint de datos de otro empleado (fichajes) es accesible por un Empleado que no sea el propietario.

## Notas adicionales

- **Orden real de implementación (revisado tras cerrar la Fase 1)**: este plan asumía originalmente 0→1→2→3→4 (con la Fase 2 dividida en un bloque base y un bloque de OCR), pero el usuario decidió implementar la **Fase 3 (Nóminas) antes que la Fase 2 (Facturas)** por ser más rápida de construir — ambas son independientes entre sí (ninguna depende de datos ni de clases de la otra, solo de la Fase 0), así que el orden entre ellas no tiene impacto técnico, solo de priorización. El orden real pasa a ser 0→1→3→2→4.
- **Renumeración de la Fase 2 (revisado al detallar sus planes de subfase)**: el desglose original separaba "Fase 2" (MVP sin OCR) de "Fase 2.1" (OCR) como dos fases distintas. Al planificar el detalle se consolidan en una única **Fase 2** con tres subfases — **2.1** (modelo + listado en pestañas + alta manual), **2.2** (exportación a Excel) y **2.3** (lo que antes era "Fase 2.1": extracción OCR + campos dinámicos) — siguiendo el mismo esquema de numeración por bloques ya usado en la Fase 1 (1.1–1.4) y la Fase 3 (3.1–3.3). No cambia el alcance funcional, solo el agrupamiento y el orden en que se detalla (la exportación a Excel, no prevista en el desglose original, se intercala como 2.2 antes del OCR porque no depende de él).
- Este plan asume que las Fases se implementan en el orden 0→1→2→3→4, pero Fichajes/Vacaciones (Fase 1) y Facturas (Fase 2.1/2.2, sin OCR) son independientes entre sí y podrían desarrollarse en paralelo si hay más de una persona disponible, siempre que la Fase 0 ya esté cerrada.
- Dentro de la Fase 1, las vistas 1.1 (Fichaje empleado) y 1.2 (Vacaciones empleado) son independientes entre sí y se pueden desarrollar en paralelo; 1.3 depende de que 1.1 esté terminada (reutiliza su esquema y sus clases) y 1.4 depende de que 1.2 esté terminada, por la misma razón.
- Dentro de la Fase 2, **2.1 y 2.2 son independientes de 2.3** (el listado, los filtros y la exportación no dependen de que el OCR exista) — se pueden desarrollar y desplegar sin bloquear en el OCR, igual que ya se recomendaba en el desglose original. **2.2 depende de 2.1** (reutiliza `FacturaRepository`/entidades) y **2.3 depende de 2.1** (extiende su endpoint de alta) pero no de 2.2.
- Antes de arrancar la Fase 2.3 (OCR), conviene una prueba de concepto rápida con 5-10 facturas reales variadas (proveedores y suministros) para validar la precisión de Tesseract sobre casos reales antes de dar por buena la decisión tomada en "Decisiones técnicas", en vez de descubrirlo ya con el pipeline completo construido.
