# Plan de Implementación — Fase 3.1 (Backend): Nóminas — listado, subida individual y descarga

Reutiliza el modelo de datos de [`../bbdd/fase3-1-bbdd.md`](../bbdd/fase3-1-bbdd.md) (tabla `nominas`) y la entidad `Empleado`/`EmpleadoRepository` de `fase0-back.md`. Introduce por primera vez en el proyecto un `FileStorageService` (previsto desde el plan general, sin ninguna implementación real hasta ahora) y la subida de ficheros (`multipart/form-data`), también sin precedente en el código existente. Expone el contrato que consume [`../front/fase3-1-front.md`](../front/fase3-1-front.md).

## Contexto y objetivo

Endpoints para que el Admin: (a) vea, para un periodo (mes/año), el estado de la nómina de cada empleado activo — sin subir (**"pendiente"**), con archivo adjuntado pero sin enviar (**"adjuntada"**), o ya enviada (**"enviada"**) — filtrable por empleado y por periodo, paginado; (b) suba manualmente un PDF individual y lo asigne a un empleado concreto; (c) descargue el PDF ya subido para verificar que su contenido es correcto antes de enviarlo. El envío por email es [`fase3-3-back.md`](./fase3-3-back.md); la subida masiva vía ZIP con matching automático es [`fase3-2-back.md`](./fase3-2-back.md) — ambas construidas sobre lo que define este documento.

## Análisis y decisión de autorización

A diferencia de `fase1-3-back.md`/`fase1-4-back.md` (donde `FICHAJES:EDICION`/`VACACIONES:EDICION` los tiene también `EMPLEADO`, por lo que los endpoints de Admin tuvieron que protegerse con la authority de `EMPLEADOS`), **`NOMINAS:EDICION` ya es exclusiva de `ADMIN`** desde el seed de `fase0-bbdd.md` — `EMPLEADO` no tiene ninguna fila de permiso para la vista `NOMINAS`. Todos los endpoints de este documento se protegen simplemente con `@PreAuthorize("hasAuthority('NOMINAS:EDICION')")`, sin ningún rodeo adicional.

## Análisis y supuestos

- **"Búsqueda avanzada"** (enunciado del usuario) se interpreta como filtros combinables — empleado, periodo (mes/año) y estado — sobre el listado paginado, siguiendo el mismo patrón de filtros ya usado en `fichajes-admin`/`vacaciones-admin` (selects + parámetros de query), **no** un motor de búsqueda por texto libre. Si el usuario quería algo más (p. ej. buscar por nombre escribiendo texto), hay que revisarlo en implementación.
- **"Gmail" del enunciado del usuario** se interpreta como el campo `empleado.email` ya existente (validado con `@Email` desde `fase0-back.md`, `EmpleadoCreateDTO`), no una integración específica con la API de Gmail. El envío de email (`fase3-3-back.md`) usa ese mismo campo como destinatario.
- **El estado "PENDIENTE" nunca se persiste** (ver `bbdd/fase3-1-bbdd.md`): el listado se construye combinando **todos los empleados activos** (filtrados por `empleadoId` si el Admin lo indica) con su fila de `nominas` para el periodo elegido, si existe. Un empleado activo sin fila para ese periodo aparece como `PENDIENTE` con `nominaId = null`; uno con fila aparece con su `estado` real (`ADJUNTADA`/`ENVIADA`) y los metadatos de esa fila.
- **Periodo por defecto = mes/año actual** si el front no manda `periodoMes`/`periodoAnio`, igual que `vacaciones-admin` usa el año en curso por defecto.
- **Subir un archivo para un empleado/periodo que ya tiene nómina reemplaza el archivo anterior** (se borra el fichero físico viejo del disco vía `FileStorageService.eliminar`) — evita nóminas duplicadas para el mismo periodo. **Si la nómina reemplazada ya estaba `ENVIADA`, la operación la devuelve a `ADJUNTADA`** (limpia `fechaEnvio`/`enviadaPorAdminId`): el contenido que se envió por email ya no es el que hay ahora en el sistema, así que el listado no puede seguir diciendo "enviada" sobre un archivo distinto — hay que reenviarla explícitamente (`fase3-3-back.md`) para que vuelva a `ENVIADA`.

## Desglose de subtareas

### Modelo y almacenamiento
1. **[Backend, baja]** Entidad `Nomina`: `id`, `empleado` (`@ManyToOne`, lazy), `periodoMes: Integer`, `periodoAnio: Integer`, `rutaArchivo: String`, `nombreArchivoOriginal: String`, `estado: EstadoNomina` (`@Enumerated(EnumType.STRING)`, enum `{ADJUNTADA, ENVIADA}`), `fechaSubida: LocalDateTime`, `subidaPorAdminId: Long`, `fechaEnvio: LocalDateTime` (nullable), `enviadaPorAdminId: Long` (nullable). `NominaRepository extends JpaRepository<Nomina, Long>`: `findByEmpleado_IdAndPeriodoMesAndPeriodoAnio(Long, Integer, Integer): Optional<Nomina>`, `findByPeriodoMesAndPeriodoAnio(Integer, Integer, @EntityGraph(attributePaths = "empleado")): List<Nomina>` (para combinarlas con la lista de empleados activos al listar, sin N+1).
2. **[Backend, media]** `FileStorageService` (interfaz nueva, `service/`) + `LocalFileStorageService` (implementación, primera vez que el proyecto guarda ficheros):
   - `guardar(MultipartFile archivo, String subcarpeta): String` — genera un nombre físico único (`UUID + extensión`, **nunca** el nombre original del usuario, para evitar colisiones y *path traversal*), lo escribe bajo `${app.storage.base-path}/<subcarpeta>/`, y devuelve la ruta relativa a persistir en `nominas.ruta_archivo`.
   - `cargarComoResource(String rutaRelativa): Resource` — para servir la descarga.
   - `eliminar(String rutaRelativa): void` — usado al reemplazar un archivo existente.
3. **[Backend, baja]** Config nueva en `application.properties` (no existía nada de multipart/almacenamiento en el proyecto hasta ahora): `spring.servlet.multipart.max-file-size=10MB`, `spring.servlet.multipart.max-request-size=10MB`, `app.storage.base-path=${STORAGE_BASE_PATH:./uploads}` — mismo patrón de variable de entorno con valor por defecto ya usado para `DB_USERNAME`/`DB_PASSWORD`.

### Listado con filtros y paginación
4. **[Backend, media]** `NominaService.listar(Long empleadoIdOpcional, int periodoMes, int periodoAnio, EstadoNominaFiltro estadoOpcional, Pageable): Page<NominaListadoDTO>` — obtiene los empleados activos (filtrados por `empleadoIdOpcional` si viene, vía `EmpleadoRepository`), los cruza en memoria con las filas de `nominas` de ese periodo (`findByPeriodoMesAndPeriodoAnio`, indexadas por `empleadoId`), calcula el estado real o `PENDIENTE`, filtra por `estadoOpcional` si viene, y pagina el resultado ya combinado. El cruce en memoria (en vez de una `@Query` nativa con `LEFT JOIN` + paginación) es aceptable al volumen esperado (la plantilla de un restaurante, no miles de empleados); si el volumen creciera mucho, revisar hacia una consulta nativa paginada en BBDD.
5. **[Backend, baja]** DTO `NominaListadoDTO {empleadoId, nombreEmpleado, email, periodoMes, periodoAnio, estado (String: "PENDIENTE"|"ADJUNTADA"|"ENVIADA"), nominaId (Long, nullable), nombreArchivoOriginal (String, nullable), fechaSubida (LocalDateTime, nullable), fechaEnvio (LocalDateTime, nullable)}`.
6. **[Backend, baja]** `GET /api/nominas?empleadoId=&periodoMes=&periodoAnio=&estado=&page=&size=` → `Page<NominaListadoDTO>`, `@PreAuthorize("hasAuthority('NOMINAS:EDICION')")`.

### Subida individual y descarga
7. **[Backend, media]** `NominaService.subir(Long empleadoId, int periodoMes, int periodoAnio, MultipartFile archivo, Long adminId): NominaResponseDTO` — valida que `archivo` sea un PDF (extensión `.pdf` **y** `content-type` `application/pdf`, ambas comprobaciones porque ninguna es fiable por separado); busca si ya existe fila para ese empleado/periodo (`findByEmpleado_IdAndPeriodoMesAndPeriodoAnio`): si existe, borra el fichero físico anterior y actualiza la fila (`estado = ADJUNTADA`, limpia `fechaEnvio`/`enviadaPorAdminId` si estaba `ENVIADA`, ver "Análisis y supuestos"); si no existe, la crea. Guarda el nuevo archivo vía `FileStorageService.guardar(archivo, "nominas")`.
8. **[Backend, baja]** DTO `NominaResponseDTO {id, empleadoId, periodoMes, periodoAnio, estado, nombreArchivoOriginal, fechaSubida, fechaEnvio}`.
9. **[Backend, baja]** `POST /api/nominas` `multipart/form-data {empleadoId, periodoMes, periodoAnio, archivo}` → `NominaResponseDTO`, `@PreAuthorize("hasAuthority('NOMINAS:EDICION')")`.
10. **[Backend, media]** `NominaService.descargar(Long nominaId): NominaDescargaDTO {Resource recurso, String nombreArchivoOriginal}` — `NominaNotFoundException` (404) si el `id` no existe. `GET /api/nominas/{id}/descargar` → `ResponseEntity<Resource>` con `Content-Type: application/pdf` y `Content-Disposition: attachment; filename="<nombreArchivoOriginal>"`.

### Transversal
11. **[Backend, baja]** Nuevas excepciones: `NominaNotFoundException` (404), `ArchivoNoValidoException` (400, mensaje indicando el formato esperado) — registradas en `GlobalExceptionHandler` (mismo estilo que el resto: grupo de 404 existente, nueva entrada dedicada para la de 400).
12. **[Backend, media]** Tests `@WebMvcTest`: token `EMPLEADO` recibe 403 en los tres endpoints; listar sin filtro de estado devuelve `PENDIENTE` para un empleado activo sin fila en el periodo consultado; subir un archivo con `content-type` distinto de `application/pdf` responde 400 sin guardar nada; subir sobre una fila ya `ENVIADA` la deja en `ADJUNTADA` con `fechaEnvio`/`enviadaPorAdminId` a `null`; descargar un `id` inexistente responde 404.
13. **[Backend, baja]** Unit test de `LocalFileStorageService`: guardar y volver a cargar un archivo de prueba devuelve el mismo contenido; el nombre físico generado es distinto del nombre original recibido.

## Decisiones técnicas

- **`FileStorageService` como interfaz desde el primer momento** (primera vez que el proyecto sube/sirve ficheros): permite cambiar a almacenamiento externo (S3, Blob Storage) más adelante sin tocar `NominaService`, siguiendo la arquitectura ya prevista en `plan-implementacion-general.md`.
- **Nombre físico del archivo generado por el backend (UUID), nunca el nombre original del usuario**: evita *path traversal* (un nombre de archivo como `../../etc/passwd.pdf`) y colisiones entre archivos de distintos empleados/periodos con el mismo nombre original.
- **Reemplazar el archivo de una nómina ya `ENVIADA` la vuelve a `ADJUNTADA`**: evita que el sistema afirme "enviada" sobre un contenido que ya no es el que se envió realmente — obliga a una acción explícita de reenvío.
- **Estado `PENDIENTE` calculado, nunca persistido**: evita filas basura por cada empleado/periodo que nunca llega a tener nómina subida (empleados de baja, periodos futuros, etc.).
- **Cruce empleados↔nóminas en memoria, no una `@Query` con `LEFT JOIN` paginado**: al volumen esperado (un restaurante) es más simple de leer y mantener que una consulta nativa con paginación sobre un `LEFT JOIN`; documentado como decisión revisable si el volumen de empleados creciera mucho.

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase3-1-bbdd.md`: sin la tabla `nominas`, ningún endpoint de este documento tiene dónde persistir nada.
- **Riesgo medio**: sin límite de tamaño de archivo explícito antes de esta fase, el valor por defecto de Spring Boot (1MB) rechazaría PDFs de nómina reales sin ningún aviso claro — se fija explícitamente a 10MB (`spring.servlet.multipart.max-file-size`), ajustable si algún caso real lo necesita más grande.
- **Riesgo bajo**: almacenamiento en disco local del propio servidor de la aplicación — si en el futuro se despliega en varias instancias detrás de un balanceador, cada instancia solo vería los archivos que ella misma escribió; fuera de alcance mientras el despliegue sea de una sola instancia (ver `plan-implementacion-general.md`, "Riesgo bajo — volumen de almacenamiento").
- **Riesgo bajo**: el cruce en memoria de "todos los empleados activos" en cada listado no escala indefinidamente; aceptable para el tamaño de plantilla de un restaurante, a revisar si el proyecto creciera mucho en número de empleados.

## Criterios de aceptación técnicos

- `GET /api/nominas` sin filtros devuelve, para el periodo actual, todos los empleados activos con `estado = "PENDIENTE"` cuando no tienen nómina de ese mes, y con su estado real cuando sí la tienen.
- Subir un PDF para un empleado sin nómina de ese periodo crea la fila en `ADJUNTADA` y a partir de ese momento aparece con ese estado en el listado.
- Subir un PDF para un empleado que ya tenía una nómina `ENVIADA` de ese periodo la deja en `ADJUNTADA` (nunca en `ENVIADA` sin que se vuelva a enviar explícitamente).
- Subir un archivo que no sea un PDF real responde 400 sin crear ni modificar ninguna fila.
- Descargar una nómina existente devuelve exactamente los mismos bytes que se subieron, con el nombre de archivo original en `Content-Disposition`.
- Un token `EMPLEADO` recibe 403 en los tres endpoints de este documento (listar, subir, descargar).
