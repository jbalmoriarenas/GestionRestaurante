# Plan de Implementación — Fase 2.3 (Backend): Facturas — extracción automática (OCR) y campos dinámicos

Depende de [`fase2-1-back.md`](./fase2-1-back.md) (entidades `Proveedor`/`Factura`, endpoint de alta) y [`../bbdd/fase2-3-bbdd.md`](../bbdd/fase2-3-bbdd.md) (`estado_extraccion`, `datos_extraidos_raw`, tabla `factura_campos_dinamicos`). Extiende, no sustituye, el flujo manual de `fase2-1`: dar de alta una factura sin pasar por OCR sigue funcionando exactamente igual. Introduce **Tess4J**, **Apache PDFBox** y **Apache Commons Text** — las tres, librerías gratuitas (Apache License 2.0), sin ningún servicio de pago.

## Contexto y objetivo

El Admin sube una foto/PDF de una factura, elige el tipo (Proveedor/Suministro), el sistema extrae automáticamente texto y propone valores para los campos del formulario de alta (`fase2-1-back.md`), y cualquier dato reconocido que no encaje en los campos fijos se ofrece como **campo dinámico** — al confirmar, esos campos dinámicos se persisten en `factura_campos_dinamicos` sin ningún cambio de código ni de esquema.

## Análisis y decisiones técnicas

- **Extracción síncrona, sin cola de trabajos**: el Admin sube una factura a la vez desde un formulario (no hay subida masiva de facturas, a diferencia del ZIP de nóminas en `fase3-2-back.md`, que no está pedido aquí). Una llamada síncrona que tarda unos segundos es aceptable y mucho más simple que introducir un job asíncrono con *polling* — revisable si en el futuro se pide subida masiva de facturas.
- **`FacturaExtractionService` como interfaz desde el primer momento**, con `TesseractFacturaExtractionService` como única implementación de esta fase: dejar la puerta abierta a sustituirla por un motor de pago (p. ej. un LLM multimodal) más adelante sin tocar el controller ni el flujo de confirmación, si la precisión de Tesseract resulta insuficiente en la práctica — misma filosofía que `FileStorageService` en `fase3-1-back.md`.
- **Nunca se persiste nada automáticamente**: `POST /api/facturas/extraer` **no** crea ninguna fila en `facturas`; solo devuelve una previsualización editable. La creación real sigue pasando por `POST /api/facturas` (`fase2-1-back.md`), ahora aceptando además un mapa de campos dinámicos — coherente con el principio ya fijado en `plan-implementacion-general.md`: "ningún proceso automático debe ser 100% ciego".
- **`producto` no se extrae de forma fiable por heurística genérica** y se documenta así explícitamente: no existe un patrón universal para identificar el concepto de una factura en formato libre sin un modelo de lenguaje. Se deja siempre como campo a confirmar/rellenar por el Admin, incluso tras una extracción "exitosa" en el resto de campos.
- **Dependencia nativa de Tesseract**: a diferencia de cualquier otra dependencia del proyecto hasta ahora (todas JVM puro), Tess4J es un *wrapper* JNI sobre el binario nativo de Tesseract — requiere instalar Tesseract OCR (+ el paquete de idioma español, `spa.traineddata`) en cada máquina donde corra el backend (desarrollo y despliegue), no solo declarar la dependencia en `pom.xml`. Debe documentarse en el `README` junto al resto de prerequisitos (JDK, Maven, Node).

## Desglose de subtareas

### Dependencias e interfaz de extracción
1. **[Backend, baja]** Dependencias en `pom.xml`: `net.sourceforge.tess4j:tess4j`, `org.apache.pdfbox:pdfbox`, `org.apache.commons:commons-text` (las tres Apache License 2.0).
2. **[Backend, media]** Interfaz `FacturaExtractionService`: `extraer(File documento, String contentType): ExtraccionResultado`, donde `ExtraccionResultado` (record) agrupa `textoOcrCrudo: String`, `proveedorSugeridoId: Long` (nullable), `nombreProveedorSugerido: String` (nullable, cuando no hay match con un proveedor existente), `fechaFacturaSugerida: LocalDate` (nullable), `numeroFacturaSugerido: String` (nullable), `importeBaseSugerido: BigDecimal` (nullable), `ivaSugerido: BigDecimal` (nullable), `importeTotalSugerido: BigDecimal` (nullable), `camposAdicionales: Map<String, String>`.

### Implementación Tesseract
3. **[Backend, alta]** `TesseractFacturaExtractionService implements FacturaExtractionService`:
   - Si `contentType = application/pdf`: renderiza cada página a `BufferedImage` vía `PDFBox` (`PDFRenderer`, 300 DPI) y concatena el texto OCR de todas las páginas (lo habitual es una sola página, pero no se asume).
   - Si es una imagen (`image/jpeg`, `image/png`, `image/webp`): carga directamente vía `ImageIO.read`.
   - Preprocesado ligero antes de OCR: escala de grises + estiramiento de contraste sobre el `BufferedImage` (`Graphics2D`/`RescaleOp` del propio JDK, sin dependencia adicional de OpenCV — se documenta como el primer sitio a mejorar si la precisión resulta insuficiente en pruebas reales, no como una solución definitiva).
   - `Tesseract.doOCR(image)` (idioma `spa`) → `textoOcrCrudo`.
4. **[Backend, alta]** `FacturaTextParser` (clase de utilidad, sin dependencia de Spring, testeable de forma aislada):
   - **Fecha**: regex sobre formatos habituales (`dd/MM/yyyy`, `dd-MM-yyyy`, `dd de MMMM de yyyy`); de varias coincidencias, se descartan las que caen fuera de un rango razonable (no más de ~1 año en el futuro o el pasado respecto a hoy) y se toma la primera plausible restante.
   - **Importes**: regex sobre tokens con forma de cantidad monetaria (`1.234,56` y variante con punto decimal); el valor más alto encontrado se propone como `importeTotalSugerido` (en una factura, el total suele ser el mayor número); si aparecen exactamente dos importes plausibles y uno es aproximadamente un 4/10/21% mayor que el otro, se proponen como `importeBaseSugerido`/`ivaSugerido`; en cualquier otro caso se dejan ambos a `null` (el Admin los rellena a mano si los necesita).
   - **Proveedor**: sobre las primeras líneas no vacías del texto (donde habitualmente está el nombre de la empresa), *fuzzy matching* contra `proveedores.nombre` (filtrado por el `tipo` indicado en la petición) vía `JaroWinklerSimilarity` de `commons-text`; una coincidencia ≥ 0.85 se propone como `proveedorSugeridoId`; si no, la línea con mejor puntuación se devuelve como `nombreProveedorSugerido` (para un alta rápida de proveedor nuevo en el front, reutilizando el modal de `fase2-1-front.md`).
   - **Campos adicionales**: cualquier línea con forma `Etiqueta: Valor` o `ETIQUETA VALOR` que no haya sido ya consumida por las reglas anteriores se añade a `camposAdicionales` con la etiqueta normalizada (recortada, sin dos puntos finales) como clave.
5. **[Backend, baja]** Config `application.properties`: `app.ocr.tessdata-path=${TESSDATA_PATH:/usr/share/tesseract-ocr/5/tessdata}` (o la ruta equivalente en Windows para desarrollo local) — mismo patrón de variable de entorno con valor por defecto ya usado en el resto del proyecto.

### Endpoint de extracción y alta extendida
6. **[Backend, media]** DTO `FacturaExtraccionPreviewDTO {textoOcrCrudo, proveedorSugeridoId, nombreProveedorSugerido, fechaFacturaSugerida, numeroFacturaSugerido, importeBaseSugerido, ivaSugerido, importeTotalSugerido, camposAdicionales: Map<String,String>}`.
7. **[Backend, media]** `FacturaService.extraerPreview(MultipartFile archivo, TipoProveedor tipo): FacturaExtraccionPreviewDTO` — valida el `content-type` igual que en `fase2-1-back.md`, delega en `FacturaExtractionService.extraer`, **no persiste nada**.
8. **[Backend, baja]** `POST /api/facturas/extraer` `multipart/form-data {archivo, tipo}` → `FacturaExtraccionPreviewDTO`, `@PreAuthorize("hasAuthority('FACTURAS:EDICION')")`.
9. **[Backend, media]** Extender `FacturaCreateDTO` (`fase2-1-back.md`) con `camposAdicionales: Map<String, String>` (nullable, vacío si no viene) y `origenOcr: boolean` (default `false`) — el front lo marca `true` cuando la factura se confirma a partir de una previsualización de `/extraer` (aunque el Admin haya editado los valores propuestos).
10. **[Backend, media]** `FacturaService.crear` (extensión de `fase2-1-back.md`): si `origenOcr = true`, guarda `estadoExtraccion = REVISADA` y `datosExtraidosRaw` (el JSON de la previsualización que el front reenvía, o se recalcula del lado servidor — a decidir en implementación cuál es más simple sin duplicar la llamada a OCR); si `origenOcr = false`, `estadoExtraccion = MANUAL`. Tras guardar la `Factura`, itera `camposAdicionales` y hace *upsert* de una fila `FacturaCampoDinamico` por entrada (`nombreCampo`, `valor`) — **esta es la implementación concreta de "los campos nuevos se crean automáticamente"**: no requiere ningún cambio de esquema ni de código por cada campo nuevo que aparezca en una factura futura.
11. **[Backend, baja]** Extender `FacturaDetalleDTO` (`fase2-1-back.md`) con `estadoExtraccion` y `camposDinamicos: {clave: string, valor: string}[]`.

### Exportación (extiende `fase2-2-back.md`)
12. **[Backend, media]** Extender `FacturaExportService.generarExcel` (`fase2-2-back.md`): añade una columna `"Datos adicionales"` con el contenido de `factura_campos_dinamicos` de cada factura concatenado como texto (`"campo1: valor1; campo2: valor2"`) — se descarta una columna por cada nombre de campo distinto (el conjunto de campos dinámicos varía por factura y crecería sin control el ancho de la hoja) a favor de una única columna de texto legible, más simple de generar y de leer.

### Transversal
13. **[Backend, baja]** `FacturaCampoDinamico` entidad (`@ManyToOne Factura`, `nombreCampo: String`, `valor: String`), `FacturaCampoDinamicoRepository extends JpaRepository<FacturaCampoDinamico, Long>`: `findByFactura_Id(Long): List<FacturaCampoDinamico>`.
14. **[Backend, media]** Tests: mockear `FacturaExtractionService` en los tests del controller (**no** ejecutar Tesseract real en CI — sería lento y frágil, y requeriría el binario nativo instalado en el runner; se documenta como decisión de testing explícita), verificando que `/extraer` no persiste ninguna fila y que `/facturas` con `origenOcr=true` y `camposAdicionales` no vacío crea las filas correspondientes en `factura_campos_dinamicos`.
15. **[Backend, media]** Tests unitarios de `FacturaTextParser` (sin Spring) contra cadenas de texto OCR fijas (fixtures) que simulan una factura real: verifica que extrae la fecha y el importe total esperados, y que una línea `CUPS: ES001...` acaba en `camposAdicionales`.

## Riesgos y dependencias

- **Riesgo alto — precisión de OCR gratuito** (heredado de `plan-implementacion-general.md`): ningún resultado se da por bueno sin revisión humana; `producto` en particular nunca se rellena solo. Mitigado por el paso de revisión obligatorio ya integrado en el flujo (el Admin siempre ve y puede editar la previsualización antes de confirmar).
- **Riesgo medio — dependencia nativa**: Tesseract debe estar instalado (binario + datos de idioma) en cada entorno donde corra el backend; a diferencia del resto de dependencias del proyecto (JARs de Maven, autosuficientes), esto es un paso de instalación manual de infraestructura que hay que documentar y no olvidar en despliegue.
- **Riesgo medio — formatos de imagen no soportados**: Tesseract/`ImageIO` no leen HEIC (formato por defecto de fotos en iPhone) sin una librería de conversión adicional — fuera de alcance de esta fase salvo que se confirme que es un caso real de uso; si lo es, es una extensión acotada (añadir `commons-imaging` o similar solo para la conversión, sin tocar el resto del pipeline).
- **Riesgo bajo**: la heurística de importe base/IVA (basada en proporciones típicas 4/10/21%) puede fallar con tipos de IVA reducidos poco comunes o descuentos aplicados — aceptable porque ambos campos son opcionales y siempre editables antes de confirmar.
- **Dependencia dura** con `bbdd/fase2-3-bbdd.md` y `back/fase2-1-back.md`.

## Criterios de aceptación técnicos

- `POST /api/facturas/extraer` con una imagen de factura real no crea ninguna fila en `facturas` ni en `factura_campos_dinamicos`.
- Confirmar la previsualización sin editar nada (`POST /api/facturas` con `origenOcr=true`) crea la factura con `estadoExtraccion = REVISADA` y `datosExtraidosRaw` relleno.
- Si la extracción detecta una etiqueta no reconocida (p. ej. `"CUPS"`), y el Admin la confirma, aparece como fila en `factura_campos_dinamicos` sin haber requerido ningún cambio de código.
- Una factura creada sin pasar por `/extraer` tiene `estadoExtraccion = MANUAL` y ninguna fila en `factura_campos_dinamicos` salvo que el Admin añada alguna manualmente desde el front (ver `front/fase2-3-front.md`).
- La exportación a Excel (`fase2-2-back.md` extendido) incluye la columna "Datos adicionales" con los campos dinámicos de cada factura, vacía cuando no tiene ninguno.
- Un token `EMPLEADO` recibe 403 en `/api/facturas/extraer`.
