# Plan de Implementación — Fase 2.1 (Frontend): Facturas — pestañas, filtros y alta manual

Consume el contrato de [`../back/fase2-1-back.md`](../back/fase2-1-back.md). Reutiliza `ui-select`/`ui-input`/`ui-table`/`ui-modal`/`ui-badge`/`ui-button`/`ui-file-input`/`ui-spinner` de [`fase0-front.md`](./fase0-front.md) y `fase3-1-front.md`. La ruta `/facturas`, la entrada de menú (`labelKey: 'facturas'`) y el icono `'facturas'` **ya existen** desde Fase 0 (`menu-items.ts`), solo falta el componente. Introduce un componente nuevo, `ui-tabs`, sin precedente en el catálogo `ui-*` actual.

## Contexto y objetivo

Vista Admin `/facturas` con dos pestañas — **Facturas de proveedores** y **Facturas de suministros** — cada una con: filtros avanzados (proveedor, estado de pago, periodo mes/año), tabla paginada (25 filas) con columnas Empresa/Producto/Cuantía/Fecha/Estado, alta manual de una factura (formulario + adjunto de archivo obligatorio, sin extracción automática todavía) y acción rápida para marcar pagada/pendiente. La exportación a Excel es [`fase2-2-front.md`](./fase2-2-front.md); la subida con extracción automática (OCR) es [`fase2-3-front.md`](./fase2-3-front.md) — ambas extienden este mismo componente.

## Estructura (se añade a `features/`)

```
core/models/proveedor.model.ts
core/models/factura.model.ts
core/services/proveedor.service.ts
core/services/factura.service.ts
shared/ui/ui-tabs/ui-tabs.ts / .html / .scss   ← nuevo, reutilizable
features/facturas/facturas-admin/facturas-admin.ts / .html / .scss
```

Ruta `/facturas` protegida con `permissionGuard('FACTURAS', 'EDICION')` (alineado con `back/fase2-1-back.md`), sustituyendo el placeholder actual.

## Modelos

### `core/models/proveedor.model.ts`
```typescript
export type TipoProveedor = 'PROVEEDOR' | 'SUMINISTRO';

export interface Proveedor {
  id: number;
  nombre: string;
  cif: string | null;
  emailContacto: string | null;
  telefono: string | null;
  tipo: TipoProveedor;
}
```

### `core/models/factura.model.ts`
```typescript
export type EstadoPago = 'PENDIENTE' | 'PAGADA';

export interface FacturaListado {
  id: number;
  empresa: string;
  producto: string;
  importeTotal: number;
  fechaFactura: string;
  estadoPago: EstadoPago;
}

export interface FacturaDetalle extends FacturaListado {
  proveedorId: number;
  tipo: TipoProveedor;
  numeroFactura: string | null;
  fechaVencimiento: string | null;
  importeBase: number | null;
  iva: number | null;
  nombreArchivoOriginal: string;
  fechaSubida: string;
}
```

## Desglose de subtareas

### Componente reutilizable `ui-tabs`
1. **[Frontend, media]** `ui-tabs` (nuevo, `shared/ui/`): recibe `[tabs]: {id: string, label: string}[]` y `[activeTabId]`, emite `(tabChange)`; renderiza los encabezados de pestaña (estilo subrayado/activo, mismo lenguaje visual que el resto del catálogo `ui-*`) y proyecta el contenido activo vía `<ng-content>` condicionado por `@if` en el propio `FacturasAdminComponent` (no un `<router-outlet>` anidado — las dos pestañas comparten el mismo componente y servicio, solo cambia el `tipo` que filtra, así que no se justifica una subruta por pestaña).
2. **[Frontend, baja]** `ui-tabs.scss`: mismo lenguaje visual que `ui-card`/`ui-button` (colores, radio, tipografía ya existentes).

### Listado con filtros por pestaña
3. **[Frontend, baja]** `FacturaService`: `listar(tipo: TipoProveedor, proveedorId: number | null, estadoPago: EstadoPago | null, mes: number | null, anio: number | null, page: number, size: number): Observable<Page<FacturaListado>>`; `obtenerDetalle(id: number): Observable<FacturaDetalle>`; `crear(formData: FormData): Observable<FacturaDetalle>`; `cambiarEstadoPago(id: number, estadoPago: EstadoPago): Observable<FacturaDetalle>`.
4. **[Frontend, baja]** `ProveedorService`: `listar(tipo: TipoProveedor | null): Observable<Proveedor[]>`; `crear(dto: {nombre, cif, emailContacto, telefono, tipo}): Observable<Proveedor>`.
5. **[Frontend, alta]** `FacturasAdminComponent`:
   - `ui-tabs` con las dos pestañas; el `tipo` activo (`signal<TipoProveedor>`) determina qué se pide al backend — cambiar de pestaña resetea la página a 1 y recarga proveedores del `ui-select` de filtro (vía `ProveedorService.listar(tipoActivo)`), pero conserva el estado de filtros de cada pestaña por separado (dos `signal` de filtros, uno por `tipo`, para no perder lo tecleado en una pestaña al mirar la otra).
   - Filtros: `ui-select` de proveedor (opción adicional "Todos", sentinel `'TODOS'`, mismo patrón que `vacaciones-admin`/`nominas-admin`), `ui-select` de estado de pago (Pendiente/Pagada/Todas), `ui-input type="month"` para el periodo (igual que `fase3-1-front.md`, se parte en `mes`/`anio` antes de llamar al servicio).
   - `ui-table` paginada (tamaño fijo 25, sin selector de tamaño de página en esta fase — no pedido por el usuario): columnas Empresa / Producto / Cuantía (formateada como moneda) / Fecha / Estado (`ui-badge`, `warning` para pendiente, `success` para pagada) / Acciones.
6. **[Frontend, media]** Columna Acciones: botón "Ver detalle" (abre modal con `FacturaDetalleDTO` + enlace de descarga del archivo, mismo patrón blob que `fase3-1-front.md`) y botón toggle "Marcar pagada"/"Marcar pendiente" (llama `cambiarEstadoPago`, actualiza la fila en el signal de resultados sin recargar toda la tabla).
7. **[Frontend, media]** Modal "Nueva factura" (`ui-modal`): formulario con `ui-select` de proveedor (+ enlace "Nuevo proveedor" que abre un segundo `ui-modal` más pequeño con `nombre`/`cif`/`emailContacto`/`telefono`; al crear, se añade a la lista de proveedores y queda preseleccionado, sin cerrar el modal de factura), `ui-input` de producto/número de factura/fechas/importes, `ui-select` de estado de pago (default Pendiente), `ui-file-input[accept]="'image/jpeg,image/png,image/webp,application/pdf'"` obligatorio. Al confirmar: `FormData` + `FacturaService.crear`, notificación de éxito, cierra el modal y añade la fila a la tabla (o refresca la página actual si no correspondería a la posición de orden actual — más simple recargar la página vigente tras un alta que intentar insertar en la posición exacta).
8. **[Frontend, baja]** Nuevas claves i18n (`es.json`/`en.json`, bajo `facturas.admin`): `titulo`, `tabs.{proveedores,suministros}`, `filtros.{proveedor,estadoPago,periodo,todos}`, `tabla.{empresa,producto,cuantia,fecha,estado,acciones}`, `estados.{PENDIENTE,PAGADA}`, `alta.{titulo,nuevoProveedor,producto,archivo,confirmar}`, `acciones.{verDetalle,marcarPagada,marcarPendiente,descargar}`, `notificaciones.altaExito`.
9. **[Frontend, baja]** Actualizar `app.routes.ts` para que `/facturas` apunte a `FacturasAdminComponent`.

## Decisiones técnicas

- **`ui-tabs` con contenido único condicionado por `@if`, no dos componentes hijos separados**: ambas pestañas comparten exactamente la misma tabla/filtros/modal, solo cambia el `tipo` — duplicar el componente sería repetir toda la lógica de filtrado y paginación sin ninguna diferencia real de comportamiento.
- **Un `signal` de filtros por pestaña** (en vez de uno solo compartido): cambiar de pestaña no debe perder los filtros que el Admin ya había puesto en la otra — comportamiento esperado de una interfaz con pestañas reales, no un simple `if`.
- **Alta de proveedor en un modal anidado**, no en una pantalla CRUD aparte: cubre el caso de uso real (proveedor nuevo al registrar su primera factura) sin construir una vista no pedida — ver la misma decisión en `back/fase2-1-back.md`.
- **Recarga de la página vigente tras un alta**, en vez de inserción optimista en la posición exacta de orden: más simple y suficientemente bueno para el volumen esperado; evita reimplementar en el front la lógica de ordenación que ya vive en el backend.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase2-1-back.md`: sin sus endpoints, esta vista no puede probarse end-to-end.
- **Riesgo bajo**: `ui-tabs` es un componente nuevo sin ningún uso previo en el proyecto — su cobertura de tests (`ui-tabs.spec.ts`) debe verificar que `tabChange` se emite con el `id` correcto y que solo un contenido está visible a la vez.
- **Riesgo bajo**: sin selector de tamaño de página, un Admin que necesite ver muchas facturas de golpe (p. ej. para revisar visualmente antes de exportar) depende de la exportación a Excel (`fase2-2-front.md`) en vez de subir el tamaño de página en la UI — aceptado como suficiente, ver "Análisis y supuestos" de `back/fase2-1-back.md`.

## Criterios de aceptación técnicos

- Al entrar en `/facturas`, se ve la pestaña "Facturas de proveedores" activa por defecto, con la primera página (25 filas) de facturas de tipo `PROVEEDOR`.
- Cambiar a la pestaña "Facturas de suministros" muestra solo facturas cuyo proveedor es de tipo `SUMINISTRO`, con sus propios filtros (no los de la otra pestaña).
- Filtrar por proveedor, estado de pago y periodo combinados reduce la tabla correctamente dentro de la pestaña activa.
- Dar de alta una factura nueva (con un proveedor existente o uno creado en el momento) la hace aparecer en el listado sin recargar la página completa.
- Marcar una factura como pagada actualiza el badge de su fila inmediatamente.
- Un usuario con rol `EMPLEADO` no ve el enlace a `/facturas` en el menú y, si navega ahí directamente, es bloqueado por el guard.
