# Plan de Implementación — Fase 2.3 (Frontend): Facturas — subida con extracción automática

Consume el contrato de [`../back/fase2-3-back.md`](../back/fase2-3-back.md). Extiende el modal "Nueva factura" y la vista de detalle de [`fase2-1-front.md`](./fase2-1-front.md) — sin pantallas nuevas.

## Contexto y objetivo

El modal "Nueva factura" gana un primer paso opcional: subir la foto/PDF y pulsar "Extraer datos automáticamente", que pre-rellena las fechas e importes del formulario — únicamente los campos fijos que la heurística puede proponer de forma fiable. El proveedor **nunca** se pre-rellena (ver nota histórica más abajo): el Admin siempre lo selecciona o lo da de alta a mano, con o sin extracción. El Admin revisa/corrige todo antes de confirmar. La vista de detalle de una factura (`fase2-1-front.md`) muestra el origen de esos datos (`estadoExtraccion`) y permite descargar el PDF/imagen original para consultar cualquier dato que no esté entre los campos fijos.

> **Nota histórica**: una versión anterior de este documento incluía además una lista editable de "Datos adicionales" (clave/valor) para los campos que el OCR detectaba fuera del formulario fijo. Se eliminó junto con el mecanismo de campos dinámicos del backend (`back/fase2-3-back.md`, `bbdd/fase2-3-bbdd.md` `V31`): en la práctica generaba una lista larga de datos poco útiles por factura. El PDF/imagen original, siempre descargable, cubre esa misma necesidad sin ese ruido. En la misma revisión, `numeroFactura` y `producto` dejaron de pedirse como obligatorios en el modal de alta (`V32`; facturas con varias líneas de artículos no encajan en un único campo de texto), y se añadieron dos campos opcionales nuevos, "Fecha de impresión" y "Fecha de entrega" (`V33`), que un ticket de venta al por mayor suele traer por separado.

> **Nota histórica (revisión posterior a esta fase)**: esta versión del documento también describía una previsualización con `proveedorSugeridoId`/`nombreProveedorSugerido` (*fuzzy matching* de proveedor contra el texto OCR) que, si no encontraba coincidencia, abría automáticamente el modal de "nuevo proveedor" con el nombre detectado prellenado; y una "fecha de venta" (`fechaFacturaSugerida`) relabelada como campo distinto de fecha de impresión/entrega. Ambas se eliminaron (ver `back/fase2-3-back.md` para el detalle técnico): el *fuzzy matching* de proveedor no llegó a usarse en la práctica, y `fecha_factura` desapareció de la BBDD (`bbdd/fase2-3-bbdd.md` `V34`) al confirmarse que casi siempre coincidía con `fecha_impresion`, que pasó a ser la fecha de referencia única y obligatoria. El modal de "nuevo proveedor" (`fase2-1-front.md`) se sigue abriendo desde su propio enlace "Nuevo proveedor", nunca automáticamente tras una extracción — además se redujo a solo Nombre/Teléfono en la misma revisión.

## Desglose de subtareas

### Extracción en el modal de alta
1. **[Frontend, baja]** Modelo `core/models/factura.model.ts`: `FacturaExtraccionPreview {fechaImpresionSugerida: string | null, fechaEntregaSugerida: string | null, importeBaseSugerido: number | null, ivaSugerido: number | null, importeTotalSugerido: number | null}` — sin nada de proveedor ni `numeroFacturaSugerido` (ver nota histórica arriba: el proveedor nunca se propone, siempre se selecciona a mano).
2. **[Frontend, baja]** `FacturaService.extraer(archivo: File, tipo: TipoProveedor): Observable<FacturaExtraccionPreview>` (`FormData`).
3. **[Frontend, alta]** Extender el modal "Nueva factura" (`FacturasAdminComponent`, `fase2-1-front.md`): tras seleccionar el archivo en `ui-file-input`, aparece un botón "Extraer datos automáticamente" (no obligatorio — el Admin puede seguir rellenando todo a mano como en `fase2-1`). Al pulsarlo:
   - `ui-spinner` mientras la petición está en curso (puede tardar unos segundos).
   - En éxito: rellena los campos de fecha de impresión/entrega e importes con los valores sugeridos (el Admin puede editar cualquiera antes de confirmar); el `ui-select` de proveedor **no se toca** — sigue vacío hasta que el Admin lo selecciona o crea uno nuevo desde el enlace "Nuevo proveedor" (`fase2-1-front.md`), exactamente igual que en un alta sin extracción.
   - Marca `origenOcr = true` en el `FormData` enviado al confirmar (determina `estadoExtraccion = REVISADA`, `back/fase2-3-back.md`); muestra un aviso visual ("Datos detectados automáticamente — revísalos antes de guardar", mismo criterio ya fijado en `plan-implementacion-general.md` para OCR) mientras el formulario tenga valores sin confirmar explícitamente.
4. **[Frontend, baja]** Los campos `producto` y `numeroFactura` ya no son obligatorios en el modal de alta (ver nota histórica arriba); `numeroFactura` sigue existiendo como campo opcional, `producto` no existe en ningún modelo del front (eliminado por completo, `bbdd/fase2-3-bbdd.md` `V34`).

### Detalle de factura
5. **[Frontend, baja]** Extender el modal de "Ver detalle" (`fase2-1-front.md`): `ui-badge` con el `estadoExtraccion` (`neutral` para Manual, `success` para Revisada, `danger` para Error), filas de "Fecha de impresión"/"Fecha de entrega" (`—` cuando no vienen informadas). El botón "Descargar" ya existente (`fase2-1-front.md`) es la vía para consultar cualquier dato de la factura que no esté entre los campos fijos mostrados.

### Transversal
6. **[Frontend, baja]** Nuevas claves i18n bajo `facturas.admin`: `alta.{extraerDatos,extrayendo,avisoRevisar}` (las etiquetas de fecha de impresión/entrega son las mismas del formulario base, `fase2-1-front.md`, sin duplicarlas aquí), `detalle.estadoExtraccion`, `estadosExtraccion.{MANUAL,PENDIENTE,PROCESADA,ERROR,REVISADA}`.

## Decisiones técnicas

- **La extracción es un paso opcional dentro del mismo modal de alta**, no una pantalla/flujo separado: mantiene un único punto de entrada para dar de alta una factura, con o sin ayuda de OCR, en vez de duplicar el formulario.
- **`producto` se elimina del modal por completo, no solo se deja de autocompletar**: probar el alta con facturas reales de muchas líneas de artículos distintos mostró que pedir un único "producto" de texto libre no aporta valor y genera fricción — la vía correcta para ese detalle es una futura vista de almacén con su propio modelo de líneas de factura, no este campo. `numeroFactura` se mantiene, pero como opcional.
- **Sin preselección ni apertura automática de proveedor** (revisión posterior — ver nota histórica arriba): una versión anterior abría el modal de "nuevo proveedor" automáticamente cuando la extracción detectaba un nombre sin match. Se eliminó junto con el *fuzzy matching* del backend; el Admin siempre selecciona o crea el proveedor a mano, con el mismo flujo que un alta sin OCR.
- **Sin lista editable de "Datos adicionales"**: ver nota histórica arriba. Si en el futuro se necesita consultar el detalle de productos comprados en una factura (p. ej. para una vista de almacén/inventario), es una funcionalidad distinta que requiere su propio modelo de datos (líneas de factura, no campos libres) y su propia fase — no una reintroducción de este mecanismo.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase2-3-back.md`.
- **Riesgo bajo**: si el Admin sube una imagen borrosa o un documento no soportado, la única respuesta es el toast de error genérico del interceptor HTTP (mismo patrón que el resto del proyecto) — no hay un mensaje específico de "la extracción no pudo procesar la imagen" en esta fase; se puede refinar si en la práctica resulta confuso.
- **Riesgo bajo**: `fechaEntrega` es puramente informativa, sin ningún uso en filtros/listado — aceptado. `fechaImpresion` sí se usa (deriva `mes`/`anio`, filtro de periodo), pero la extracción nunca la garantiza: si el OCR no la detecta, el Admin debe rellenarla a mano antes de poder confirmar (campo obligatorio, `fase2-1-front.md`).

## Criterios de aceptación técnicos

- Subir una imagen de factura y pulsar "Extraer datos automáticamente" rellena al menos la fecha de impresión e importe total en la mayoría de casos de prueba (no se exige acierto perfecto).
- Un documento con fecha de impresión y de entrega distintas rellena ambos campos del formulario correctamente, sin tocar el `ui-select` de proveedor.
- Tras una extracción, el proveedor sigue sin seleccionar — el Admin lo elige o crea uno nuevo exactamente igual que en un alta sin OCR (`fase2-1-front.md`).
- Dar de alta una factura sin usar "Extraer datos automáticamente" sigue funcionando exactamente igual que en `fase2-1-front.md`, con `estadoExtraccion = Manual` en el detalle.
- El modal de alta no muestra ni pide "Producto".
- El detalle de una factura permite descargar el PDF/imagen original independientemente de si vino de OCR o de alta manual.
