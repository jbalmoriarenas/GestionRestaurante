# Plan de Implementación — Fase 2.3 (Frontend): Facturas — subida con extracción automática y campos dinámicos

Consume el contrato de [`../back/fase2-3-back.md`](../back/fase2-3-back.md). Extiende el modal "Nueva factura" y la vista de detalle de [`fase2-1-front.md`](./fase2-1-front.md) — sin pantallas nuevas.

## Contexto y objetivo

El modal "Nueva factura" gana un primer paso opcional: subir la foto/PDF y pulsar "Extraer datos automáticamente", que pre-rellena el resto del formulario (proveedor, fecha, importes) y muestra los campos que el OCR detectó pero no encajan en ningún campo fijo como una lista editable de "Datos adicionales" — el Admin revisa/corrige todo antes de confirmar. La vista de detalle de una factura (`fase2-1-front.md`) muestra esos mismos datos adicionales cuando existen.

## Desglose de subtareas

### Extracción en el modal de alta
1. **[Frontend, baja]** Modelo `core/models/factura.model.ts`: añadir `FacturaExtraccionPreview {proveedorSugeridoId: number | null, nombreProveedorSugerido: string | null, fechaFacturaSugerida: string | null, numeroFacturaSugerido: string | null, importeBaseSugerido: number | null, ivaSugerido: number | null, importeTotalSugerido: number | null, camposAdicionales: Record<string, string>}`; añadir `estadoExtraccion: EstadoExtraccion` y `camposDinamicos: {clave: string, valor: string}[]` a `FacturaDetalle`.
2. **[Frontend, baja]** `FacturaService.extraer(archivo: File, tipo: TipoProveedor): Observable<FacturaExtraccionPreview>` (`FormData`).
3. **[Frontend, alta]** Extender el modal "Nueva factura" (`FacturasAdminComponent`, `fase2-1-front.md`): tras seleccionar el archivo en `ui-file-input`, aparece un botón "Extraer datos automáticamente" (no obligatorio — el Admin puede seguir rellenando todo a mano como en `fase2-1`). Al pulsarlo:
   - `ui-spinner` mientras la petición está en curso (puede tardar unos segundos).
   - En éxito: rellena los campos del formulario con los valores sugeridos (el Admin puede editar cualquiera antes de confirmar); si `proveedorSugeridoId` viene informado, preselecciona ese proveedor en el `ui-select`; si no, pero `nombreProveedorSugerido` sí, abre el modal de "nuevo proveedor" con el nombre ya prellenado (el Admin confirma o corrige antes de crear); `camposAdicionales` se vuelca en una lista editable (ver punto 4).
   - Muestra un aviso visual ("Datos detectados automáticamente — revísalos antes de guardar", mismo criterio ya fijado en `plan-implementacion-general.md` para OCR) mientras el formulario tenga valores sin confirmar explícitamente.
4. **[Frontend, media]** Lista editable de "Datos adicionales" dentro del propio modal (no un componente `ui-*` nuevo — uso único por ahora, ver "Decisiones técnicas"): una fila por entrada de `camposAdicionales` (dos `ui-input`, clave y valor) con botón de eliminar por fila, y un botón "Añadir campo" para que el Admin incluya alguno a mano aunque el OCR no lo haya detectado. Al confirmar el alta, este mapa se envía como `camposAdicionales` en el `FormData` (`fase2-3-back.md`).
5. **[Frontend, baja]** El campo `producto` **nunca** se pre-rellena desde la extracción (`fase2-3-back.md` no lo propone) — el formulario lo deja vacío y obligatorio igual que en `fase2-1-front.md`, sin ningún tratamiento especial.

### Detalle de factura
6. **[Frontend, media]** Extender el modal de "Ver detalle" (`fase2-1-front.md`): si `camposDinamicos` no está vacío, sección adicional "Datos adicionales" listando pares clave/valor de solo lectura; `ui-badge` con el `estadoExtraccion` (`neutral` para Manual, `success` para Revisada, `danger` para Error).

### Transversal
7. **[Frontend, baja]** Nuevas claves i18n bajo `facturas.admin`: `alta.{extraerDatos,extrayendo,avisoRevisar,camposAdicionales,anadirCampo,clave,valor}`, `detalle.{datosAdicionales,estadoExtraccion}`, `estadosExtraccion.{MANUAL,PENDIENTE,PROCESADA,ERROR,REVISADA}`.

## Decisiones técnicas

- **Lista de "Datos adicionales" como plantilla local del modal, no un componente `ui-*` reutilizable**: por ahora solo tiene un punto de uso; se extrae como componente si aparece un segundo caso de uso real, siguiendo el mismo criterio ya aplicado en el proyecto para introducir `ui-file-input`/`ui-tabs` solo cuando hicieron falta.
- **La extracción es un paso opcional dentro del mismo modal de alta**, no una pantalla/flujo separado: mantiene un único punto de entrada para dar de alta una factura, con o sin ayuda de OCR, en vez de duplicar el formulario.
- **`producto` nunca se autocompleta**, ni siquiera con un valor de baja confianza: coherente con `back/fase2-3-back.md`, que documenta explícitamente que no hay heurística fiable para ese campo — mostrar un valor inventado sería peor que dejarlo vacío.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase2-3-back.md`.
- **Riesgo bajo**: si el Admin sube una imagen borrosa o un documento no soportado, la única respuesta es el toast de error genérico del interceptor HTTP (mismo patrón que el resto del proyecto) — no hay un mensaje específico de "la extracción no pudo procesar la imagen" en esta fase; se puede refinar si en la práctica resulta confuso.
- **Riesgo bajo**: la lista de "Datos adicionales" no valida que el Admin no introduzca dos claves iguales a mano — el backend lo rechazaría en el *upsert* (`uk_campos_dinamicos_factura_nombre`) mostrando el error genérico; se podría añadir validación en el propio front si resulta un caso frecuente.

## Criterios de aceptación técnicos

- Subir una imagen de factura y pulsar "Extraer datos automáticamente" rellena al menos fecha e importe total en la mayoría de casos de prueba (no se exige acierto perfecto).
- Los campos detectados que no encajan en el formulario fijo aparecen como filas editables en "Datos adicionales", y se pueden añadir o quitar antes de confirmar.
- Confirmar el alta con campos adicionales los persiste; al abrir después el detalle de esa factura, aparecen en la sección "Datos adicionales".
- Dar de alta una factura sin usar "Extraer datos automáticamente" sigue funcionando exactamente igual que en `fase2-1-front.md`, con `estadoExtraccion = Manual` en el detalle.
- El campo `producto` nunca aparece pre-rellenado tras una extracción.
