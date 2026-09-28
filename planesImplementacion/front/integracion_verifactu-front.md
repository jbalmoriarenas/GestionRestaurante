# Plan de Implementación — Integración VeriFactu (Frontend)

Consume el contrato de [`../back/integracion_verifactu-back.md`](../back/integracion_verifactu-back.md). Reutiliza `ui-card`/`ui-button`/`ui-badge`/`ui-modal`/`ui-spinner`/`ui-table`/`ui-select` de [`fase0-front.md`](./fase0-front.md). Se inspira en el patrón de pantalla de integración externa de [`fase4-1-front.md`](./fase4-1-front.md) (Valoraciones/Google), adaptado: aquí no hay un flujo OAuth, sino un formulario de configuración fiscal y un panel de pruebas.

## Contexto y objetivo

Vista Admin nueva `/verifactu`, pensada explícitamente como **herramienta de configuración y pruebas técnicas**, no como un módulo de facturación de venta (que no existe todavía en la app — ver alcance acordado en `back/integracion_verifactu-back.md`). Muestra: los datos fiscales del emisor (formulario), el estado de la integración (entorno activo, si hay certificado/emisor configurados, último registro), un formulario para generar un "registro de prueba" contra ese entorno, y un histórico de los registros generados con su huella, estado de envío y QR. La UI deja visible en todo momento que los registros generados aquí son de prueba mientras no exista una factura de venta real que los origine.

## Estructura (se añade a `features/`)

```
core/models/verifactu.model.ts
core/services/verifactu.service.ts
features/verifactu/verifactu-admin/verifactu-admin.ts / .html / .scss
```

Ruta `/verifactu` protegida con `permissionGuard('VERIFACTU', 'EDICION')` (alineado con `back/integracion_verifactu-back.md`). Nueva entrada en `layout/menu-items.ts`: `{ vista: 'VERIFACTU', ruta: '/verifactu', labelKey: 'verifactu', icono: 'verifactu' }`. Nuevo icono `'verifactu'` en el catálogo `ui-icon` (`shared/ui/ui-icon/ui-icon.ts`, ampliando `type IconName`) y nueva entrada `'VERIFACTU'` en `CodigoVista` (`core/models/permiso.model.ts`).

## Modelos

### `core/models/verifactu.model.ts`
```typescript
export type EntornoVerifactu = 'LOCAL_SIMULADO' | 'PRE';
export type EstadoEnvioVerifactu = 'PENDIENTE' | 'ENVIADA' | 'ACEPTADA' | 'ACEPTADA_CON_ERRORES' | 'RECHAZADA' | 'ERROR_ENVIO';

export interface ConfiguracionEmisorFiscal {
  nif: string | null;
  razonSocial: string | null;
  nombreComercial: string | null;
  direccion: string | null;
  codigoPostal: string | null;
  municipio: string | null;
  provincia: string | null;
}

export interface VerifactuEstado {
  entornoActivo: EntornoVerifactu;
  certificadoConfigurado: boolean;
  emisorConfigurado: boolean;
  ultimoRegistro: RegistroVerifactuListado | null;
}

export interface RegistroVerifactuListado {
  id: number;
  entorno: EntornoVerifactu;
  numeroFactura: string;
  fechaExpedicion: string;
  importeTotal: number;
  estadoEnvio: EstadoEnvioVerifactu;
  fechaGeneracion: string;
}

export interface RegistroVerifactuDetalle extends RegistroVerifactuListado {
  huellaAnterior: string | null;
  huellaActual: string;
  respuestaAeat: unknown | null;
}

export interface RegistroVerifactuPruebaPayload {
  numeroFactura: string;
  serieFactura?: string;
  fechaExpedicion: string;
  importeTotal: number;
  cuotaTotal: number;
}
```

## Desglose de subtareas

### Servicio
1. **[Frontend, baja]** `VerifactuService`: `obtenerEmisor(): Observable<ConfiguracionEmisorFiscal>`; `actualizarEmisor(payload): Observable<ConfiguracionEmisorFiscal>`; `obtenerEstado(): Observable<VerifactuEstado>`; `listarRegistros(entorno?, estadoEnvio?, page, size): Observable<Page<RegistroVerifactuListado>>`; `obtenerRegistro(id): Observable<RegistroVerifactuDetalle>`; `generarRegistroPrueba(payload: RegistroVerifactuPruebaPayload): Observable<RegistroVerifactuDetalle>`; `reenviar(id): Observable<RegistroVerifactuDetalle>`; `urlQr(id): string` (construye la URL de `GET /api/verifactu/registros/{id}/qr` para usarla directamente como `src` de una imagen).

### Componente
2. **[Frontend, media]** `VerifactuAdminComponent` — tarjeta **"Datos fiscales del emisor"**: formulario reactivo (`nif`, `razonSocial`, `nombreComercial`, `direccion`, `codigoPostal`, `municipio`, `provincia`; `nif`/`razonSocial` obligatorios) que carga con `obtenerEmisor()` y guarda con `actualizarEmisor()`; `ui-badge` de aviso si `nif`/`razonSocial` están vacíos ("Sin datos fiscales — no se pueden generar registros").
3. **[Frontend, media]** Tarjeta **"Estado de la integración"**: `ui-badge` con el `entornoActivo` (distinto color si es `LOCAL_SIMULADO` vs. `PRE`, para que nunca se confunda un envío simulado con uno real), indicadores de `certificadoConfigurado`/`emisorConfigurado`, y resumen del `ultimoRegistro` (número, estado, fecha) si existe.
4. **[Frontend, alta]** Tarjeta **"Generar registro de prueba"**: formulario reactivo con `RegistroVerifactuPruebaPayload` (validadores: `numeroFactura` obligatorio, `importeTotal`/`cuotaTotal` numéricos positivos, `fechaExpedicion` obligatoria); botón deshabilitado si `emisorConfigurado = false` (con tooltip/aviso explicando por qué); al enviar, llama a `generarRegistroPrueba()`, muestra `ui-spinner` durante la llamada, y en éxito refresca el histórico y el estado, y notifica el resultado (`estadoEnvio` devuelto). **Banner de aviso visible siempre sobre este formulario**: "Genera un registro de prueba simulado — no representa una factura real emitida a un cliente" (ver "Decisiones técnicas").
5. **[Frontend, alta]** Tabla histórico (`ui-table`, paginada) con columnas: entorno (`ui-badge`), número de factura, fecha, importe, huella (truncada, ej. `abcd1234…`, con tooltip del valor completo), estado de envío (`ui-badge`, color según valor), fecha de generación. Fila con acción "Ver QR" (abre `ui-modal` con `<img [src]="verifactuService.urlQr(id)">` y la huella completa) y, si `estadoEnvio` es `RECHAZADA` o `ERROR_ENVIO`, acción "Reintentar envío" que llama a `reenviar(id)` y refresca la fila.
6. **[Frontend, baja]** Nuevas claves i18n bajo `verifactu.admin`: `titulo`, `emisor.{titulo,nif,razonSocial,nombreComercial,direccion,codigoPostal,municipio,provincia,guardar,incompleto}`, `estado.{titulo,entorno,certificado,emisor,ultimoRegistro,sinRegistros}`, `prueba.{titulo,aviso,numeroFactura,serieFactura,fechaExpedicion,importeTotal,cuotaTotal,generar}`, `historico.{titulo,columnas.*,verQr,reintentar,sinDatos}`, `notificaciones.{emisorGuardado,registroGenerado,reenvioOk,reenvioError}`.
7. **[Frontend, baja]** Nueva entrada en `layout/menu-items.ts` e icono `'verifactu'` (`shared/ui/ui-icon/`, mismo patrón SVG *outline* que el resto del catálogo).
8. **[Frontend, baja]** Actualizar `app.routes.ts` para añadir `/verifactu` → `VerifactuAdminComponent`, y añadir `'VERIFACTU'` a `CodigoVista` (`core/models/permiso.model.ts`).

## Decisiones técnicas

- **Vista separada de `/facturas`, no una pestaña dentro del módulo existente**: `/facturas` gestiona facturas *recibidas* de proveedores; VeriFactu es una pieza de infraestructura para facturas *emitidas* (que hoy no existen en la app) — mezclar ambas en la misma pantalla induciría a pensar que están relacionadas.
- **El QR se pide como imagen directa (`GET .../qr`, `Content-Type: image/png`) usada como `src` de un `<img>`, no como Base64 decodificado en TypeScript**: más simple, y coherente con cómo ya se descarga el archivo de una factura recibida (`GET /api/facturas/{id}/archivo`).
- **Aviso visible y persistente de que los registros son de prueba** (punto 4): dado que esta pantalla existe antes de que haya un módulo de facturación de venta real, es importante que nadie interprete un registro generado aquí como una factura válida emitida a un cliente — ni siquiera cuando el entorno activo es `PRE` (ahí sí se envía de verdad al sandbox de la AEAT, pero sigue siendo un dato simulado, no una venta real).
- **Sin botón de "eliminar" registros**: los registros de prueba se acumulan (ver "Riesgos" en `back/integracion_verifactu-back.md`); no se construye una función de borrado en esta fase por ser una tabla pensada como append-only — si el volumen de pruebas molesta, es una tarea de mantenimiento aparte.

## Riesgos y dependencias

- **Dependencia dura** con `back/integracion_verifactu-back.md`: sin sus endpoints, esta vista no puede probarse.
- **Riesgo bajo**: al no haber una factura de venta real detrás, el formulario de "registro de prueba" pide datos que un usuario podría rellenar de forma incoherente entre sí (p. ej. `cuotaTotal` mayor que `importeTotal`) — no se valida esa coherencia en el front en esta fase (no lo valida tampoco el backend, ver `back/integracion_verifactu-back.md`); aceptable en una herramienta de pruebas técnicas, a revisar si se generaliza al módulo de venta real.

## Criterios de aceptación técnicos

- Sin datos fiscales configurados, el formulario de "Generar registro de prueba" aparece deshabilitado con el aviso correspondiente.
- Rellenar y guardar los datos fiscales del emisor habilita el formulario de registro de prueba sin recargar la página.
- Generar un registro de prueba en `LOCAL_SIMULADO` lo añade al histórico con `estadoEnvio = PENDIENTE`, sin ningún indicador de envío real.
- Generar un registro de prueba en `PRE` refleja en el histórico el `estadoEnvio` devuelto por la AEAT tras la llamada real.
- "Ver QR" abre un modal con la imagen del código QR y la huella completa del registro.
- "Reintentar envío" solo aparece en filas con `estadoEnvio` `RECHAZADA` o `ERROR_ENVIO`, y tras usarla la fila refleja el nuevo estado.
- Un usuario con rol `EMPLEADO` no ve el enlace a `/verifactu` en el menú y, si navega ahí directamente, es bloqueado por el guard.
