# Plan de Implementación — Fase 2.2 (Backend): Facturas — exportación a Excel y a ZIP de archivos originales

Depende de [`fase2-1-back.md`](./fase2-1-back.md) (entidades `Proveedor`/`Factura`, `FacturaRepository.findByFiltros`, `FileStorageService`). Introduce **Apache POI** en el proyecto (primera generación de un fichero binario que no sea un PDF subido por el usuario) y usa `java.util.zip.ZipOutputStream` de la JDK para el export en ZIP (revisión posterior, sin dependencia nueva).

## Contexto y objetivo

Dos exportaciones sobre el mismo conjunto de facturas filtrado (`tipo`, `proveedorId`, `estadoPago`, `mes`, `anio`), ambas ignorando la paginación de la UI — traen todas las filas/archivos que cumplan el filtro, no solo la página de 10 visible en el front:

1. **Excel (`.xlsx`)**: una fila por factura con los campos de negocio (sin `id`, sin CIF/email/número de factura del proveedor — eliminados de las columnas exportadas en una revisión posterior, ver más abajo). Título y cabeceras de columna se los pasa el front ya traducidos al idioma activo de la app (el backend no tiene mecanismo de i18n para el resto de endpoints).
2. **ZIP**: el archivo original (PDF, tras la conversión de imagen a PDF de `fase2-1-back.md` si aplica) de cada factura del filtro, todos dentro de una única carpeta interna. A diferencia del Excel, exige `mes`/`anio` obligatorios (no tiene sentido descargar de golpe los archivos de todos los periodos).

## Análisis y supuestos

- **Ambas exportaciones ignoran la paginación de la UI**: mismos filtros que `GET /api/facturas`, sin `page`/`size` — traen todas las filas/archivos que cumplan el filtro.
- **`titulo`/`cabeceras` (Excel) y `nombreCarpeta` (ZIP) los genera el front, no el backend**: evita duplicar el mecanismo de i18n (ngx-translate) en el backend solo para estas dos cadenas; el backend los trata como texto libre que llega por query param (ver "Decisiones técnicas" sobre saneado/escapado).
- **`id`, CIF, email de contacto y número de factura se eliminaron de las columnas del Excel** (revisión posterior tras feedback directo del usuario): eran datos internos o de contacto del proveedor sin valor para el propósito real del exportable (conciliación de importes/fechas de pago), y el CIF/email en particular no deberían salir de la aplicación en un archivo que puede circular por email.

## Desglose de subtareas

### Excel
1. **[Backend, baja]** Dependencia `org.apache.poi:poi-ooxml` (Apache License 2.0, gratuita) en `pom.xml`.
2. **[Backend, alta]** `FacturaExportService.exportar(OutputStream out, TipoProveedor tipo, Long proveedorIdOpcional, EstadoPago estadoOpcional, Integer mesOpcional, Integer anioOpcional, String titulo, List<String> cabeceras)` — escribe directamente sobre el `OutputStream` (vía `StreamingResponseBody`, nunca un `byte[]` completo en memoria) usando `SXSSFWorkbook` (variante *streaming* de POI). Obtiene las filas de `FacturaRepository.findByFiltros(..., Pageable.unpaged())` y escribe, en este orden exacto, las 13 columnas `Empresa, Teléfono, Tipo, Fecha impresión, Fecha entrega, Importe base, IVA, Importe total, Mes, Año, Estado pago, Nombre archivo original, Fecha subida` — el orden está acoplado, columna a columna, con `CABECERA_DEFECTO` (fallback en castellano si el front no manda `cabeceras`, p. ej. una llamada manual a la API) y con `cabecerasExportar()` del front (`front/fase2-2-front.md`); solo se valida que el **número** de cabeceras recibidas coincida (`cabeceras.size() == CABECERA_DEFECTO.length`), no el orden — reordenar una columna aquí obliga a reordenar las otras dos. Si `titulo` viene informado, se escribe como primera fila, en una celda fusionada y centrada sobre el ancho de la tabla (`escribirTitulo`). Cabecera en negrita.
3. **[Backend, baja]** `GET /api/facturas/exportar?tipo=&proveedorId=&estadoPago=&mes=&anio=&titulo=&cabecera=` (`cabecera` repetido, uno por columna) → `ResponseEntity<StreamingResponseBody>`, `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition: attachment; filename="facturas_<tipo>_<yyyyMMdd-HHmmss>.xlsx"`, `@PreAuthorize("hasAuthority('FACTURAS:EDICION')")`. `tipo` obligatorio; `mes`/`anio` opcionales a nivel de contrato HTTP (el front siempre los manda porque su UI exige un periodo seleccionado antes de exportar, ver `front/fase2-2-front.md`, pero el endpoint no lo impone).

### ZIP de archivos originales
4. **[Backend, media]** `FacturaExportService.exportarArchivos(OutputStream out, TipoProveedor tipo, Long proveedorIdOpcional, EstadoPago estadoOpcional, int mes, int anio, String nombreCarpeta)`: mismo filtro que el Excel (`mes`/`anio` aquí primitivos, obligatorios), escribe un `ZipOutputStream` con una entrada `<carpeta>/<id>_<nombreArchivoOriginal>` por factura (el ID antepuesto evita colisiones si dos facturas se subieron con el mismo nombre de archivo). Un archivo ilegible o borrado del disco (`UncheckedIOException` de `FileStorageService.cargarComoResource`) **no aborta el ZIP entero**: se omite esa factura, mismo criterio *best-effort* que la extracción OCR (`fase2-3-back.md`); cualquier otra `RuntimeException` sigue propagándose.
5. **[Backend, baja]** `FacturaExportService.sanearNombreCarpeta(String): String` (público, estático): quita `\`/`/` y colapsa secuencias `..` del `nombreCarpeta` recibido por query param, para que no pueda escribir entradas de ZIP fuera de la carpeta interna prevista si se llama a la API directamente con un valor manipulado (el front siempre manda un nombre seguro, p. ej. `facturas_julio_2026`, pero el saneado no depende de esa confianza).
6. **[Backend, baja]** `GET /api/facturas/exportar-zip?tipo=&proveedorId=&estadoPago=&mes=&anio=&nombreCarpeta=` → `ResponseEntity<StreamingResponseBody>`, `Content-Type: application/zip`, `Content-Disposition: attachment; filename="<carpeta>.zip"` (`<carpeta>` = `nombreCarpeta` saneado, o `facturas_<anio>-<mes>` si no viene), `@PreAuthorize("hasAuthority('FACTURAS:EDICION')")`. `tipo`, `mes`, `anio` obligatorios (`mes`/`anio` como `int` primitivo, no `Integer`, para forzarlo a nivel de firma del controller).

### Transversal
7. **[Backend, baja]** Extender `FacturaRepository.findByFiltros` para aceptar `Pageable.unpaged()` (ya construido así en `fase2-1-back.md`, reutilizado sin cambios por ambas exportaciones — no hay una query separada para exportar).
8. **[Backend, media]** Test `@WebMvcTest`: token `EMPLEADO` recibe 403 en ambos endpoints; la respuesta trae el `Content-Type`/`Content-Disposition` esperados; exportar sin resultados devuelve un `.xlsx`/`.zip` válido (vacío o solo cabecera, no un error).
9. **[Backend, baja]** Test de integración ligero de `FacturaExportService`: generar el Excel para un conjunto de facturas de prueba y verificar, leyendo el workbook resultante con la propia POI, que el número de filas y el contenido de una celda conocida coinciden. Tests unitarios de `sanearNombreCarpeta` (`FacturaExportServiceTest`): rutas con `/`, `\` y `..` quedan neutralizadas.

## Decisiones técnicas

- **`SXSSFWorkbook` (streaming) en vez de `XSSFWorkbook`**: evita cargar en memoria todas las filas del workbook antes de escribirlas — relevante si el número de facturas crece con los años; a coste de volumen actual (un restaurante) `XSSFWorkbook` habría sido suficiente, pero el cambio no añade complejidad real y evita tener que revisar esta decisión más adelante.
- **Exportación siempre completa (sin paginar), nunca solo la página visible**: coherente con el propósito explícito del usuario ("extraer toda la información de la factura, no solo los campos mostrados en el front") — limitar la exportación a 10 filas visibles contradiría ese objetivo.
- **`StreamingResponseBody` en vez de construir el `.xlsx`/`.zip` entero en un `byte[]` antes de responder**: permite empezar a enviar la respuesta antes de tener todas las filas/archivos generados, evitando un pico de memoria y un timeout de request en exportaciones grandes.
- **`escaparFormula`**: `titulo`/`cabeceras` son texto libre que llega por query param; si un valor empezara por `=`, `+`, `-` o `@` (sintaxis de fórmula/DDE en Excel), Excel podría interpretarlo como fórmula al abrir el archivo en vez de como texto plano — se antepone un apóstrofe para forzarlo siempre a texto. Aplicado tanto al título como a cada valor de cabecera.
- **Revisión posterior — bug de `AuthorizationFilter` en descargas `StreamingResponseBody`**: en pruebas reales, tanto `/exportar` como `/exportar-zip` fallaban en el cliente con `ERR_INCOMPLETE_CHUNKED_ENCODING` (la respuesta empezaba en 200 y se cortaba a mitad de la generación). La causa: Spring Security vuelve a ejecutar `AuthorizationFilter` en el redespacho `ASYNC` de Servlet que dispara `StreamingResponseBody`, pero `JwtAuthFilter` (`OncePerRequestFilter`) no reprocesa por defecto los despachos `ASYNC` — así que al re-evaluar la autorización ya no hay contexto de autenticación, y la petición se deniega tarde, con la respuesta ya empezada. Corregido con `JwtAuthFilter.shouldNotFilterAsyncDispatch() { return false; }` (fuera del alcance original de este documento, pero afecta a cualquier endpoint con `StreamingResponseBody` protegido por `@PreAuthorize`, incluidos ambos de aquí).

## Riesgos y dependencias

- **Dependencia dura** con `back/fase2-1-back.md`: reutiliza `FacturaRepository`/`Proveedor`/`Factura`/`FileStorageService` tal cual.
- **Riesgo bajo**: sin límite de filas/archivos en ninguna de las dos exportaciones, un filtro muy amplio podría generar un archivo grande y tardar varios segundos — aceptable al volumen esperado de un restaurante; mitigable en el futuro limitando el rango de fechas exportable o moviendo la generación a un job asíncrono, no necesario ahora.
- **Riesgo bajo**: el ZIP omite silenciosamente (sin fallar la descarga) facturas cuyo archivo esté ilegible/borrado en disco — aceptable como *best-effort*, pero significa que el número de entradas del ZIP puede ser menor que el número de facturas del filtro sin ningún aviso explícito en la respuesta; a revisar si en la práctica genera confusión sobre archivos "que faltan".

## Criterios de aceptación técnicos

- `GET /api/facturas/exportar?tipo=PROVEEDOR` descarga un `.xlsx` con una fila por cada factura de tipo `PROVEEDOR` que exista en BBDD, sin limitarse a 10.
- El Excel exportado no incluye columnas de `id`, CIF, email de contacto ni número de factura.
- Un `titulo` que empieza por `=` se escribe en la celda como texto literal (con apóstrofe inicial), nunca se interpreta como fórmula.
- `GET /api/facturas/exportar-zip?tipo=PROVEEDOR&mes=7&anio=2026` sin `mes`/`anio` responde 400 (parámetros obligatorios); con ambos, descarga un `.zip` con una entrada por factura del periodo, todas dentro de una única carpeta interna.
- Un `nombreCarpeta` con `../../etc` se sanea a un nombre sin barras ni `..` antes de usarse como carpeta interna del ZIP.
- Si el archivo de una factura no se puede leer del disco, el ZIP se genera igualmente con el resto de entradas (no aborta ni responde error).
- Un token `EMPLEADO` recibe 403 en ambos endpoints de exportación.
