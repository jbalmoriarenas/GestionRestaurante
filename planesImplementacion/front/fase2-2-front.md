# Plan de Implementación — Fase 2.2 (Frontend): Facturas — exportación a Excel y a ZIP de archivos originales

Consume el contrato de [`../back/fase2-2-back.md`](../back/fase2-2-back.md). Extiende `FacturasAdminComponent` de [`fase2-1-front.md`](./fase2-1-front.md) — sin componentes nuevos.

## Contexto y objetivo

Dos botones junto a los filtros de `FacturasAdminComponent`, ambos exigiendo un periodo (mes+año) seleccionado:

1. **"Exportar a Excel"**: descarga un `.xlsx` con **todas** las facturas que cumplen los filtros actualmente aplicados (no solo la página visible de 10), con título y cabeceras de columna traducidos al idioma activo de la app.
2. **"Exportar PDFs"**: descarga un `.zip` con el archivo original (PDF) de cada factura del periodo filtrado, todos dentro de una carpeta interna `facturas_<mes>_<anio>`.

> **Nota (revisión posterior a esta fase)**: la redacción original de este documento solo cubría el botón de Excel. El botón de ZIP, el periodo obligatorio para exportar, la eliminación de columnas (CIF/email de contacto/número de factura/id) y el título centrado son todos cambios posteriores a la primera implementación, incorporados directamente en el contenido de abajo (no como nota aparte) porque describen el mismo componente sin contradecir nada de lo ya construido.

## Desglose de subtareas

1. **[Frontend, baja]** `FacturaService.exportar(tipo, proveedorId, estadoPago, mes, anio, titulo, cabeceras): Observable<Blob>` y `exportarZip(tipo, proveedorId, estadoPago, mes, anio, nombreCarpeta): Observable<Blob>` (ambos `responseType: 'blob'`).
2. **[Frontend, baja]** `FacturasAdminComponent.resolverPeriodoParaExportar()`: si el filtro de periodo de la pestaña activa no tiene mes/año seleccionados, dispara una notificación de error (`facturas.admin.filtros.periodoRequeridoExportar`) y devuelve `null` sin llamar al backend; si lo tiene, devuelve `{mes, anio}`. Usado por ambos botones antes de exportar — coherente con que el backend exige `mes`/`anio` obligatorios en `/exportar-zip` (`back/fase2-2-back.md`) y, aunque `/exportar` los admite opcionales a nivel de contrato HTTP, el front nunca permite una exportación "de todos los periodos a la vez" desde la UI.
3. **[Frontend, baja]** `tituloExportar(mes, anio)`: genera el título traducido (`facturas.admin.exportar.tituloConPeriodo`, con el nombre del mes también traducido) que se envía como `titulo` a `/exportar` — se escribe centrado sobre la tabla en el Excel resultante (`back/fase2-2-back.md`, `escribirTitulo`).
4. **[Frontend, baja]** `nombreCarpetaExportar(mes, anio)`: genera `facturas_<mes en minúsculas>_<anio>` (p. ej. `facturas_julio_2026`), usado como `nombreCarpeta` del ZIP y como nombre de archivo descargado.
5. **[Frontend, baja]** `cabecerasExportar()`: array de 13 claves de cabecera traducidas (`facturas.admin.exportar.cabeceras.*`), enviado como `cabeceras` a `/exportar`. **El orden tiene que coincidir exactamente con `CABECERA_DEFECTO` y con el orden de escritura de columnas de `FacturaExportService` en el backend** (`back/fase2-2-back.md`) — un comentario cruzado en ambos ficheros marca este acoplamiento; reordenar una columna en un lado obliga a reordenar los otros dos.
6. **[Frontend, media]** Botones "Exportar a Excel" y "Exportar PDFs" junto a los filtros: ambos `[disabled]` mientras `filtrosForm.controls.periodo.value` esté vacío (además de la validación de `resolverPeriodoParaExportar()` al pulsar, por si el control se vacía justo antes del clic); al pulsar, llaman al método de servicio correspondiente con los filtros de la pestaña activa (**no** con `page`/`size` — no aplica), muestran `ui-spinner` mientras la petición está en curso (deshabilitan el botón para evitar doble clic), y en éxito disparan la descarga vía `descargarBlob()` (helper compartido por ambos botones: `URL.createObjectURL`/`URL.revokeObjectURL`, mismo patrón que `NominaService.descargar` de `fase3-1-front.md`) con el nombre generado en el propio front (`tituloExportar`/`nombreCarpetaExportar`), no leído de `Content-Disposition`.
7. **[Frontend, baja]** Nuevas claves i18n bajo `facturas.admin`: `acciones.{exportar,exportarPdfs}`, `filtros.periodoRequeridoExportar`, `exportar.tituloConPeriodo`, `exportar.cabeceras.*` (13 claves, una por columna), `notificaciones.{exportando,exportacionLista}`.

## Decisiones técnicas

- **Ambos botones exigen un periodo seleccionado, deshabilitados si no lo hay**: a diferencia de la primera versión (exportaba todo lo filtrado, sin exigir periodo), en la práctica exportar "todas las facturas de siempre" de golpe no es el caso de uso real — el Admin exporta para revisar/archivar un mes concreto. El ZIP en particular no tendría sentido sin acotar por periodo (podría intentar descargar de golpe los archivos de años de facturas).
- **El título y las cabeceras del Excel los genera y traduce el front**, no el backend: evita duplicar el mecanismo de i18n (ngx-translate) en el backend — mismo criterio ya documentado en `back/fase2-2-back.md`.
- **El nombre de archivo descargado lo genera el propio front** (`tituloExportar`/`nombreCarpetaExportar`) en vez de leerlo de la cabecera `Content-Disposition` de la respuesta: más simple que depender de que el interceptor HTTP existente exponga esa cabecera, y da control total sobre el formato exacto (`facturas_<mes>_<anio>`).
- **El botón exporta siempre el conjunto completo filtrado del periodo, nunca solo la página visible**: coherente con `back/fase2-2-back.md` — el propósito del botón es obtener el detalle completo fuera de la UI, no una copia de lo que ya se ve en pantalla.
- **Reutiliza el patrón de descarga por blob ya introducido en `fase3-1-front.md`** (vía el helper compartido `descargarBlob()`), sin ningún componente nuevo: la exportación es, desde el punto de vista del front, una descarga de archivo binario más.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase2-2-back.md`, incluido el orden acoplado de `cabecerasExportar()` (ver punto 5).
- **Riesgo bajo**: exportaciones grandes pueden tardar varios segundos — el `ui-spinner` con el botón deshabilitado evita que el Admin dispare varias descargas simultáneas por impaciencia, pero no hay indicador de progreso real (no viable con una descarga por blob simple); aceptable al volumen esperado.
- **Riesgo bajo**: si una factura del ZIP tiene su archivo ilegible en disco, el backend la omite silenciosamente (`back/fase2-2-back.md`) — el front no compara el número de entradas del ZIP contra el número de facturas del periodo, así que el Admin no recibe ningún aviso de que faltan archivos.

## Criterios de aceptación técnicos

- Con el filtro de periodo vacío, ambos botones de exportación aparecen deshabilitados.
- Pulsar "Exportar a Excel" en la pestaña "Facturas de proveedores" con un periodo seleccionado descarga un `.xlsx` con solo facturas de tipo `PROVEEDOR` de ese periodo, con el título centrado y las cabeceras traducidas al idioma activo.
- El Excel descargado no incluye columnas de CIF, email de contacto, número de factura ni id.
- Pulsar "Exportar PDFs" descarga un `.zip` llamado `facturas_<mes>_<anio>.zip` con una carpeta interna del mismo nombre y un PDF por factura del periodo.
- Aplicar filtros antes de exportar (proveedor/estado, además del periodo obligatorio) se refleja en el contenido de ambos archivos descargados.
- Ambos botones se deshabilitan mientras su exportación está en curso y se reactivan al terminar (éxito o error).
