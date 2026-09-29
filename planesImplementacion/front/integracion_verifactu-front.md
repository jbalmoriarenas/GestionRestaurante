# Plan de Implementación — Integración VeriFactu (Frontend)

Consume el contrato de [`../back/integracion_verifactu-back.md`](../back/integracion_verifactu-back.md). Reutiliza `ui-card`/`ui-button`/`ui-badge`/`ui-modal`/`ui-spinner`/`ui-table`/`ui-select`/`ui-tabs` de [`fase0-front.md`](./fase0-front.md). La pantalla `/verifactu` sigue el patrón de integración externa de [`fase4-1-front.md`](./fase4-1-front.md); la pestaña "Ventas" sigue el patrón de pestañas y filtros ya construido en [`fase2-1-front.md`](./fase2-1-front.md) (`facturas-admin`).

**Revisión de alcance (esta versión)**: la versión anterior de este documento describía `/verifactu` explícitamente como "herramienta de configuración y pruebas técnicas, no un módulo de facturación de venta (que no existe todavía en la app)". A petición del usuario, ese módulo se construye ahora: una tercera subpestaña **"Ventas"** dentro de la vista existente `/facturas` (que hoy tiene "Facturas de proveedores"/"Facturas de suministros"), con su propio listado, alta y anulación — el disparador real de los registros VeriFactu, además del endpoint de prueba que `/verifactu` sigue ofreciendo.

## Contexto y objetivo

Dos superficies, con responsabilidades distintas y ya no acopladas por casualidad sino por diseño:

1. **`/facturas` → pestaña "Ventas" (nueva)**: donde el día a día del restaurante registra lo que le vende a sus clientes — factura simplificada (ticket, sin datos del cliente) por defecto, o completa si se identifica al cliente. Cada venta, al guardarse, genera y envía (según el entorno VeriFactu activo) su registro de facturación automáticamente — sin paso de confirmación posterior. Una venta puede anularse.
2. **`/verifactu` (ya existente, sin cambios de fondo)**: configuración fiscal del emisor, estado de la integración, y una herramienta de generación de **registros de prueba** para validar el pipeline sin depender de ventas reales. Su histórico ahora puede mostrar también los registros generados por ventas reales (distinguibles por `ventaId`), con enlace de vuelta a la venta que los originó.

## Estructura

```
core/models/venta.model.ts                                      (nuevo)
core/models/verifactu.model.ts                                  (se amplía: ventaId en el listado)
core/models/permiso.model.ts                                    (se amplía: 'VENTAS' en CodigoVista)
core/services/venta.service.ts                                  (nuevo)
core/services/verifactu.service.ts                               (sin cambios)
features/facturas/facturas-admin/facturas-admin.ts / .html        (se amplía: tercera pestaña)
features/facturas/ventas-tab/ventas-tab.ts / .html / .scss        (nuevo)
features/verifactu/verifactu-admin/verifactu-admin.ts / .html      (se amplía: columna/enlace a venta en el histórico)
```

No hay ruta nueva para Ventas — vive dentro de `/facturas`, ya protegida por `permissionGuard('FACTURAS', 'EDICION')`. La pestaña "Ventas" en sí se muestra u oculta según un permiso propio, `VENTAS` (ver "Decisiones técnicas"). `/verifactu` sigue protegida por `permissionGuard('VERIFACTU', 'EDICION')`, sin cambios.

## Modelos

### `core/models/venta.model.ts`
```typescript
export type EstadoVenta = 'EMITIDA' | 'ANULADA';

export interface VentaDesgloseIva {
  tipoImpositivo: number; // 0, 4, 10, 21 (los tipos habituales de IVA en España)
  baseImponible: number;
  cuota: number; // calculada por el backend, solo lectura en el detalle
}

export interface VentaListado {
  id: number;
  serie: string;
  numero: number;
  fechaExpedicion: string;
  descripcionOperacion: string;
  clienteNombre: string | null; // null = factura simplificada
  importeTotal: number;
  estado: EstadoVenta;
}

export interface RegistroVerifactuResumen {
  id: number;
  entorno: 'LOCAL_SIMULADO' | 'PRE';
  tipoRegistro: 'ALTA' | 'ANULACION';
  estadoEnvio: string;
}

export interface VentaDetalle extends VentaListado {
  clienteNif: string | null;
  clienteDomicilio: string | null;
  desgloseIva: VentaDesgloseIva[];
  fechaAnulacion: string | null;
  motivoAnulacion: string | null;
  registros: RegistroVerifactuResumen[]; // enlaza al histórico de /verifactu
}

export interface VentaDesgloseIvaPayload {
  tipoImpositivo: number;
  baseImponible: number;
}

export interface VentaCreatePayload {
  serie: string;
  fechaExpedicion: string;
  descripcionOperacion: string;
  clienteNif?: string;
  clienteNombre?: string;
  clienteDomicilio?: string;
  desgloseIva: VentaDesgloseIvaPayload[];
}
```

### `core/models/verifactu.model.ts` (ampliación)
```typescript
export interface RegistroVerifactuListado {
  // ... campos ya existentes ...
  ventaId: number | null; // null = registro de prueba
}
```

### `core/models/permiso.model.ts` (ampliación)
```typescript
export type CodigoVista = 'EMPLEADOS' | 'FICHAJES' | 'VACACIONES' | 'FACTURAS' | 'NOMINAS' | 'VALORACIONES' | 'VERIFACTU' | 'VENTAS';
```

## Desglose de subtareas

### Servicio
1. **[Frontend, media]** `VentaService` (`core/services/venta.service.ts`), `BASE_URL = ${API_BASE_URL}/ventas`: `listar(desde?, hasta?, estado?, page, size): Observable<Page<VentaListado>>` (mismo patrón `buildFiltroParams()` que `FacturaService.listar`); `obtenerDetalle(id): Observable<VentaDetalle>`; `crear(payload: VentaCreatePayload): Observable<VentaDetalle>`; `anular(id, motivo: string): Observable<VentaDetalle>`.

### Pestaña "Ventas" (nuevo componente)
2. **[Frontend, alta]** `VentasTab` (`features/facturas/ventas-tab/`, componente standalone independiente, **no** una ampliación del `FormGroup`/tabla de `facturas-admin` ya existentes — ver "Decisiones técnicas"): filtros propios (`desde`, `hasta`, `estado`), tabla paginada (`ui-table`) con columnas serie+número, fecha, cliente (o "—" si es simplificada), importe, estado (`ui-badge`: verde `EMITIDA`, gris `ANULADA`), y una columna/acción de estado de envío VeriFactu del registro `ALTA` de esa venta (`ui-badge`, mismo criterio de color que ya usa `verifactu-admin`).
3. **[Frontend, alta]** Modal **"Nueva venta"** (`ui-modal`): formulario reactivo con `serie`, `fechaExpedicion`, `descripcionOperacion` (con placeholder/ayuda: "Describe el servicio prestado, evita términos genéricos como 'Varios'" — la normativa exige que no sea una descripción genérica; no se puede validar automáticamente que no lo sea, solo ayudar al usuario a no equivocarse). Bloque **"Datos del cliente (opcional)"**: `clienteNif`, `clienteNombre`, `clienteDomicilio` — si se rellena `clienteNif`, `clienteNombre` pasa a ser obligatorio en el propio formulario (validador cruzado), reflejando la regla de negocio ya validada también en el backend. Bloque **"Desglose de IVA"**: `FormArray` de líneas `{tipoImpositivo, baseImponible}` (selector de tipo con las opciones habituales de hostelería — 0%, 4%, 10%, 21% — más un botón "+ Añadir línea" para cubrir ventas con varios tipos en la misma venta, p. ej. comida al 10% y bebida alcohólica al 21%); `cuota` y el total se recalculan en el propio formulario en cada cambio (solo para mostrarlo al usuario antes de guardar — el valor con autoridad sigue siendo el que calcula el backend al persistir). Botón deshabilitado si `desgloseIva` está vacío.
4. **[Frontend, media]** Modal de **detalle de venta**: datos completos, líneas de desglose, y el/los registro(s) VeriFactu asociados (entorno, tipo, estado de envío) con un enlace ("Ver en VeriFactu") que navega a `/verifactu` con el registro preseleccionado (query param, ver subtarea 7). Si `estado === 'EMITIDA'`, botón **"Anular venta"** que abre un modal de confirmación pidiendo `motivo` (`@NotBlank` también en el front) y llama a `VentaService.anular(id, motivo)`; tras anular, refresca el detalle y la fila en la tabla.
5. **[Frontend, baja]** Nuevas claves i18n bajo `ventas.*`: `tab`, `nuevaVenta.{titulo,serie,fechaExpedicion,descripcionOperacion,descripcionOperacionAyuda,cliente.{titulo,nif,nombre,domicilio},desglose.{titulo,tipoImpositivo,baseImponible,cuota,anadirLinea,vacio},guardar}`, `detalle.{titulo,cliente,simplificada,desglose,registros,verEnVerifactu,anular}`, `anular.{titulo,motivo,confirmar}`, `tabla.columnas.*`, `estados.{EMITIDA,ANULADA}`, `notificaciones.{ventaCreada,ventaAnulada}`.

### Integración en `facturas-admin`
6. **[Frontend, media]** `facturas-admin.ts`: el tipo de `activeTab` pasa de `TipoProveedor` (`'PROVEEDOR' | 'SUMINISTRO'`) a una unión propia `TabFacturas = TipoProveedor | 'VENTAS'`; el `computed<UiTabItem[]>` de `tabs` añade una tercera entrada `{ id: 'VENTAS', label: ... }` **solo si** `authService.tieneAcceso('VENTAS')` (mismo patrón de filtrado por permiso ya usado en `Sidebar`/la pantalla de Ayuda — reutilizar `tieneAccesoItem`, `layout/menu-items.ts`, con `{ vista: 'VENTAS', nivelMinimo: 'EDICION' }`). `facturas-admin.html`: dentro del `<ui-tabs>`, el contenido existente (filtros/tabla/modales de Proveedores-Suministros) se envuelve en `@if (activeTab() !== 'VENTAS') { ... } @else { <app-ventas-tab /> }` — el contenido de Proveedores/Suministros **no se modifica**, solo se condiciona; toda la complejidad nueva vive aislada en `VentasTab` (ver "Decisiones técnicas").

### `/verifactu` — pequeña ampliación
7. **[Frontend, media]** `VerifactuAdminComponent`: la tabla histórico añade una columna "Origen" (`ui-badge`: "Prueba" si `ventaId === null`, "Venta" si no) y, cuando `ventaId` no es `null`, un enlace a la venta (navega a `/facturas` con `?tab=VENTAS&ventaId=…`, que `VentasTab` lee al inicializarse para abrir directamente el modal de detalle de esa venta — mismo mecanismo inverso al de la subtarea 4). El resto de la pantalla (formulario de emisor, estado, generación de registros de prueba) **no cambia**.

## Decisiones técnicas

- **`VentasTab` es un componente standalone independiente, no una ampliación del `FormGroup`/tabla ya existentes en `facturas-admin`**: el componente actual mantiene un `FormGroup` de filtros y una tabla *por tab*, pero ambas pestañas existentes comparten el mismo dominio (facturas recibidas, filtradas por `Proveedor.tipo`) y por eso comparten estructura. Ventas es un dominio distinto de principio a fin — entidad distinta, filtros distintos (`desde`/`hasta`/`estado` en vez de `proveedorId`/`estadoPago`/`periodo`), columnas distintas, y un modal de alta con un `FormArray` de desglose de IVA que no tiene equivalente en el alta de una factura recibida. Forzarlo dentro del mismo bloque de plantilla y del mismo `Record<TipoProveedor, FiltrosFormGroup>` habría mezclado dos modelos de datos no relacionados en la misma clase — un componente hijo aislado, como ya hace el propio proyecto para bloques de complejidad equivalente (p. ej. `ui-calendar`, `ui-table`), es más consistente con el estilo del repo que "encajar con calzador" un tercer caso en una estructura pensada para dos.
- **Pestaña "Ventas" condicionada a un permiso propio (`VENTAS`), independiente de `FACTURAS`**: como en el backend (`bbdd/integracion_verifactu-bbdd.md`), un Admin podría querer delegar la gestión de facturas de proveedores sin dar acceso a los datos de facturación de venta del negocio (más sensibles: ingresos reales). **Suposición**: se asume que quien tiene `VENTAS` también tiene `FACTURAS` (para poder llegar siquiera a `/facturas`, que sigue exigiendo ese permiso en el guard de ruta) — no se construye una forma de combinar permisos con "O" en `permissionGuard`, que hoy no lo soporta; si en la práctica se necesita un Admin con `VENTAS` pero sin `FACTURAS`, sería una ampliación aparte del guard.
- **El cálculo de `cuota`/total en el formulario de alta es solo una vista previa, nunca el valor que se envía como autoridad**: el payload manda `tipoImpositivo`/`baseImponible` por línea; es el backend quien calcula `cuota` e `importeTotal` (`back/integracion_verifactu-back.md`) — evita que un redondeo distinto en JavaScript diverja del valor que efectivamente queda registrado y enviado a la AEAT.
- **Sin confirmación en dos pasos para "Nueva venta"**: al guardar, se genera y (según entorno) se envía el registro VeriFactu de inmediato, sin un estado de borrador — ver justificación legal completa en `back/integracion_verifactu-back.md` ("Decisiones técnicas"). El formulario, por tanto, sí debe dejar revisar el desglose y el total calculado *antes* de pulsar "Guardar" (ver subtarea 3), porque no hay vuelta atrás sin pasar por "Anular".
- **"Anular", no "Eliminar"**: coherente con que `ventas`/`registro_facturacion_verifactu` nunca borran filas (`bbdd/integracion_verifactu-bbdd.md`) — la UI no ofrece ninguna acción de borrado, solo anulación con motivo obligatorio, igual que exige la normativa.

## Riesgos y dependencias

- **Dependencia dura** con `back/integracion_verifactu-back.md`: sin sus endpoints (`/api/ventas/**`), esta pestaña no puede construirse ni probarse.
- **Riesgo bajo, heredado de la versión anterior**: el formulario de alta pide un `desgloseIva` coherente (bases positivas, tipos válidos) pero no valida más allá de eso — aceptable dado que el propio backend recalcula todo con autoridad.
- **Riesgo medio — usabilidad del `FormArray` de desglose**: para una venta con un único tipo de IVA (el caso más común), obligar a interactuar con un `FormArray` en vez de un campo simple añade fricción; mitigado con una línea ya pre-añadida por defecto al abrir el modal (con `tipoImpositivo` vacío para que el usuario solo tenga que elegir el tipo y escribir la base), en vez de partir de una lista vacía.
- **Riesgo bajo**: sin generación de ticket/PDF (ver "No-objetivos" en `back/integracion_verifactu-back.md`), tras guardar una venta el usuario no tiene nada que imprimir/entregar desde la propia app — asumido como fuera de alcance.

## Criterios de aceptación técnicos

- Un usuario con permiso `FACTURAS` pero sin `VENTAS` ve las pestañas "Facturas de proveedores"/"Facturas de suministros" pero no "Ventas".
- Crear una venta sin datos de cliente la deja como factura simplificada (columna "Cliente" muestra "—"); rellenar `clienteNif` exige `clienteNombre` antes de poder guardar.
- El total mostrado en el formulario antes de guardar coincide con el que aparece después en el listado (ambos calculados con la misma lógica de desglose, aunque el que persiste es el del backend).
- Guardar una venta en entorno `LOCAL_SIMULADO` no produce ningún error visible ni requiere ninguna acción adicional del usuario para "completar" el registro VeriFactu — queda hecho en el mismo guardado.
- "Anular venta" solo está disponible en ventas `EMITIDA`; tras anular, la fila pasa a `ANULADA` y desaparece el botón.
- Desde el histórico de `/verifactu`, un registro con origen "Venta" enlaza de vuelta a su venta; uno con origen "Prueba" no muestra ningún enlace.
- Un usuario con rol `EMPLEADO` no ve la pestaña "Ventas" (ni, como ya ocurría, el enlace a `/verifactu`) y, si llama directamente a los endpoints de `/api/ventas/**`, recibe 403 del backend.
