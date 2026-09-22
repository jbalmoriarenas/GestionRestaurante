# Plan de Implementación — Fase 2.2 (Backend): Facturas — exportación a Excel

Depende de [`fase2-1-back.md`](./fase2-1-back.md) (entidades `Proveedor`/`Factura`, `FacturaRepository.findByFiltros`). Introduce **Apache POI** en el proyecto, sin precedente hasta ahora (primera generación de un fichero binario que no sea un PDF subido por el usuario).

## Contexto y objetivo

Endpoint que exporta a `.xlsx` **todas** las facturas que cumplan los filtros activos (no solo la página de 25 visible en el front), con **todos los campos de la factura**, no solo las columnas mostradas en la tabla — es la vía para que el Admin consulte datos que la UI no muestra (número de factura, desglose base/IVA, fecha de vencimiento, quién la subió, etc.) sin necesitar un formulario de detalle por cada fila.

## Análisis y supuestos

- **La exportación ignora la paginación de la UI**: usa los mismos filtros (`tipo`, `proveedorId`, `estadoPago`, `mes`, `anio`) que `GET /api/facturas`, pero sin `page`/`size` — trae todas las filas que cumplan el filtro. Si el usuario quería exportar únicamente la página actual, hay que revisarlo en implementación (interpretación más útil: "quiero el listado completo en Excel", no "quiero una copia de lo que ya veo en pantalla").
- **No incluye todavía los campos dinámicos de OCR** (`factura_campos_dinamicos`): esa tabla no existe hasta `fase2-3-bbdd.md`. Este documento deja el punto de extensión señalado (ver "Riesgos y dependencias"); `fase2-3-back.md` amplía `FacturaExportService` para añadir una columna con esos datos una vez existan.

## Desglose de subtareas

1. **[Backend, baja]** Dependencia `org.apache.poi:poi-ooxml` (Apache License 2.0, gratuita) en `pom.xml`.
2. **[Backend, alta]** `FacturaExportService.generarExcel(TipoProveedor tipo, Long proveedorIdOpcional, EstadoPago estadoOpcional, Integer mesOpcional, Integer anioOpcional): void` — escribe directamente sobre un `OutputStream` (vía `StreamingResponseBody`, no un `byte[]` en memoria) usando `SXSSFWorkbook` (variante *streaming* de POI): obtiene todas las filas de `FacturaRepository.findByFiltros(..., Pageable.unpaged())` — o una variante sin paginar del repositorio — y escribe una fila de Excel por factura con las columnas: `ID, Empresa, CIF, Email contacto, Teléfono, Tipo, Nº factura, Producto, Fecha factura, Fecha vencimiento, Importe base, IVA, Importe total, Mes, Año, Estado pago, Nombre archivo original, Fecha subida`. Cabecera en negrita (primera fila), columnas de fecha con formato de celda de fecha (no texto plano), columnas de importe con formato numérico de 2 decimales.
3. **[Backend, baja]** `GET /api/facturas/exportar?tipo=&proveedorId=&estadoPago=&mes=&anio=` → `ResponseEntity<StreamingResponseBody>`, `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition: attachment; filename="facturas_<tipo>_<yyyyMMdd-HHmmss>.xlsx"`, `@PreAuthorize("hasAuthority('FACTURAS:EDICION')")`. `tipo` obligatorio, igual que en el listado.
4. **[Backend, baja]** Extender `FacturaRepository` con una variante de `findByFiltros` que acepte `Sort` sin `Pageable` (o reutilizar `Pageable.unpaged()`) para no duplicar la lógica de filtros entre listado y exportación.
5. **[Backend, media]** Test `@WebMvcTest`: token `EMPLEADO` recibe 403; la respuesta trae el `Content-Type`/`Content-Disposition` esperados; exportar sin resultados devuelve un `.xlsx` válido con solo la fila de cabecera (no un error).
6. **[Backend, baja]** Test de integración ligero de `FacturaExportService`: generar el Excel para un conjunto de facturas de prueba y verificar, leyendo el workbook resultante con la propia POI, que el número de filas y el contenido de una celda conocida coinciden.

## Decisiones técnicas

- **`SXSSFWorkbook` (streaming) en vez de `XSSFWorkbook`**: evita cargar en memoria todas las filas del workbook antes de escribirlas — relevante si el número de facturas crece con los años; a coste de volumen actual (un restaurante) `XSSFWorkbook` habría sido suficiente, pero el cambio no añade complejidad real y evita tener que revisar esta decisión más adelante.
- **Exportación siempre completa (sin paginar), nunca solo la página visible**: coherente con el propósito explícito del usuario ("extraer toda la información de la factura, no solo los campos mostrados en el front") — limitar la exportación a 25 filas visibles contradiría ese objetivo.
- **`StreamingResponseBody` en vez de construir el `.xlsx` entero en un `byte[]` antes de responder**: permite empezar a enviar la respuesta antes de tener todas las filas generadas, evitando un pico de memoria y un timeout de request en exportaciones grandes.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase2-1-back.md`: reutiliza `FacturaRepository`/`Proveedor`/`Factura` tal cual.
- **Punto de extensión conocido**: `fase2-3-back.md` debe ampliar `FacturaExportService` para incluir una columna adicional con los campos dinámicos de OCR (`factura_campos_dinamicos`) una vez esa tabla exista — documentado aquí para que no se olvide al implementar esa fase.
- **Riesgo bajo**: sin límite de filas en la exportación, un filtro muy amplio (p. ej. "todas las facturas de suministros de los últimos 10 años") podría generar un archivo grande y tardar varios segundos — aceptable al volumen esperado de un restaurante; si se vuelve un problema real, la vía de mitigación es limitar el rango de fechas exportable o mover la generación a un job asíncrono con descarga posterior, no necesario ahora.

## Criterios de aceptación técnicos

- `GET /api/facturas/exportar?tipo=PROVEEDOR` descarga un `.xlsx` con una fila por cada factura de tipo `PROVEEDOR` que exista en BBDD, sin limitarse a 25.
- Aplicar `proveedorId`/`estadoPago`/`mes`/`anio` en la exportación filtra igual que en el listado (`GET /api/facturas`).
- El Excel resultante incluye columnas que no aparecen en la tabla del front (p. ej. número de factura, CIF del proveedor, desglose base/IVA).
- Un token `EMPLEADO` recibe 403 en `/api/facturas/exportar`.
