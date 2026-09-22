# Plan de Implementación — Fase 2.2 (Frontend): Facturas — botón de exportación a Excel

Consume el contrato de [`../back/fase2-2-back.md`](../back/fase2-2-back.md). Extiende `FacturasAdminComponent` de [`fase2-1-front.md`](./fase2-1-front.md) — sin componentes nuevos.

## Contexto y objetivo

Botón "Exportar a Excel" en la pestaña activa de `FacturasAdminComponent`, que descarga un `.xlsx` con **todas** las facturas que cumplen los filtros actualmente aplicados (no solo la página visible de 25).

## Desglose de subtareas

1. **[Frontend, baja]** `FacturaService.exportar(tipo, proveedorId, estadoPago, mes, anio): Observable<Blob>` (`responseType: 'blob'`).
2. **[Frontend, media]** Botón "Exportar a Excel" junto a los filtros de `FacturasAdminComponent`: al pulsar, llama `exportar(...)` con los filtros actuales de la pestaña activa (**no** con `page`/`size` — no aplica), muestra `ui-spinner` mientras la petición está en curso (deshabilita el botón para evitar doble clic), y en éxito dispara la descarga con el mismo patrón blob + `URL.createObjectURL`/`URL.revokeObjectURL` que `NominaService.descargar` (`fase3-1-front.md`), usando el nombre de archivo devuelto en `Content-Disposition` (o uno generado en el propio front si no es trivial leer esa cabecera desde `HttpClient` — a confirmar en implementación cuál de las dos vías es más simple con el interceptor HTTP existente).
3. **[Frontend, baja]** Nuevas claves i18n bajo `facturas.admin`: `acciones.exportar`, `notificaciones.exportando`, `notificaciones.exportacionLista`.

## Decisiones técnicas

- **El botón exporta siempre el conjunto completo filtrado, nunca solo la página visible**: coherente con `back/fase2-2-back.md` — el propósito del botón es obtener el detalle completo fuera de la UI, no una copia en Excel de lo que ya se ve en pantalla.
- **Reutiliza el patrón de descarga por blob ya introducido en `fase3-1-front.md`**, sin ningún componente nuevo: la exportación es, desde el punto de vista del front, una descarga de archivo binario más.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase2-2-back.md`.
- **Riesgo bajo**: exportaciones grandes pueden tardar varios segundos — el `ui-spinner` con el botón deshabilitado evita que el Admin dispare varias descargas simultáneas por impaciencia, pero no hay indicador de progreso real (no viable con una descarga por blob simple); aceptable al volumen esperado.

## Criterios de aceptación técnicos

- Pulsar "Exportar a Excel" en la pestaña "Facturas de proveedores" descarga un `.xlsx` con solo facturas de tipo `PROVEEDOR`.
- Aplicar filtros antes de exportar (proveedor/estado/periodo) se refleja en el contenido del archivo descargado.
- El botón se deshabilita mientras la exportación está en curso y se reactiva al terminar (éxito o error).
