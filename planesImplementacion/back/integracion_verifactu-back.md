# Plan de Implementación — Integración VeriFactu (Backend)

Reutiliza el modelo de datos de [`../bbdd/integracion_verifactu-bbdd.md`](../bbdd/integracion_verifactu-bbdd.md) (tablas `configuracion_emisor_fiscal`, `secuencia_venta`, `ventas`, `venta_desglose_iva`, `registro_facturacion_verifactu`). Independiente del módulo `Factura`/`Proveedor` (facturas *recibidas*, `back/fase2-1-back.md` y siguientes) — no se toca ningún archivo de ese módulo. Expone el contrato que consume [`../front/integracion_verifactu-front.md`](../front/integracion_verifactu-front.md).

**Revisión de alcance (esta versión)**: la versión anterior de este documento explícitamente **no** construía un módulo de facturación de venta ("la app no emite hoy facturas a clientes... esta fase no construye ese módulo") y dejaba como "punto de enganche futuro, documentado aquí para que quede localizado" el momento en que sí se construyera. Ese momento es este documento: a petición del usuario, se añade el módulo `Venta` (registro de lo que el restaurante emite a sus clientes) como disparador real de `VeriFactuRegistroService`, además de (no en sustitución de) el endpoint de generación de registros de prueba ya existente.

## Contexto y objetivo

Todo lo ya construido en la versión anterior se mantiene sin cambios de comportamiento:

- `HuellaVerifactuService`: calcula la huella (hash) de un registro de facturación, encadenada con la del registro anterior.
- `VeriFactuQrService`: genera el contenido y la imagen del código QR exigido por la normativa.
- `VeriFactuClient` (con sus dos implementaciones, simulada y AEAT real): envía un registro al entorno activo.
- `POST /api/verifactu/registros/prueba`: sigue existiendo, para poder seguir probando el pipeline sin depender de una venta real (por ejemplo, para validar un cambio en `HuellaVerifactuService` sin tener que dar de alta ventas de prueba).

Lo que se añade:

- **Módulo `Venta`**: `VentaService.crear(...)` (factura simplificada o completa, según si se identifican datos del cliente — ver `bbdd/integracion_verifactu-bbdd.md`) y `.anular(...)`. Cada alta y cada anulación de una venta dispara, en la misma transacción, la generación de su registro VeriFactu correspondiente — no existe un estado de "venta sin registrar": la obligación legal de registrar cada factura emitida es inherente al acto de emitirla, no una acción posterior opcional (Real Decreto 1007/2023).
- **`VeriFactuRegistroService`** se generaliza: la lógica común de "calcular huella + persistir `PENDIENTE` + intentar envío + actualizar estado" se extrae a un método privado compartido, usado tanto por `generarRegistroPrueba` (sin `ventaId`) como por los dos puntos de enganche nuevos (`generarRegistroVenta`/anulación, con `ventaId`) — resuelve limpiamente el fork que la versión anterior dejaba abierto.
- Numeración correlativa de ventas por serie, segura bajo concurrencia (`secuencia_venta` con bloqueo de fila).

## Análisis y supuestos

- **El algoritmo exacto de la huella y el esquema SOAP de la AEAT siguen sin validar contra la documentación técnica oficial vigente** — riesgo ya señalado en la versión anterior, **sin resolver todavía**: sigue aplicando con la misma severidad ahora que hay ventas reales detrás. No se recomienda procesar ventas reales de clientes contra el entorno `PRE` (y mucho menos `PRODUCCION`, ver "No-objetivos") hasta validarlo.
- **Solo se contemplan los entornos `LOCAL_SIMULADO` y `PRE`, igual que antes**: una venta real generada en `LOCAL_SIMULADO` calcula huella y QR y persiste el registro sin llamar a la AEAT (permite operar y probar el módulo de ventas de punta a punta sin depender de red ni certificado); en `PRE` se llama de verdad al entorno de preproducción.
- **La numeración de `ventas` (serie + número) es responsabilidad exclusiva de esta capa, nunca del cliente**: el front nunca envía `numero` — lo asigna `VentaService.crear(...)` de forma atómica (ver "Desglose de subtareas"). El cliente sí elige la `serie` (campo libre, p. ej. `A` o `2026`) y el resto de datos de la venta.
- **`importeTotal`/`cuotaTotal` de una venta se calculan siempre en el backend a partir de `venta_desglose_iva`, nunca se aceptan del cliente** — mismo criterio ya aplicado a `mes`/`anio` en `Factura` (`back/fase2-1-back.md`): un dato derivado no debe poder desincronizarse de los datos que lo originan.
- **No se modifica en ningún punto el módulo `Factura`/`Proveedor` existente**: sigue siendo una decisión de alcance, no un descubrimiento técnico.

## No-objetivos

- **Entorno `PRODUCCION` real contra la AEAT**: sigue sin construirse, a propósito. Aunque esta fase construye el módulo de venta real, enviar registros a producción exige (a) el certificado digital definitivo de la empresa, y (b) validar el algoritmo de huella/esquema SOAP contra la especificación oficial vigente — ambos riesgos siguen abiertos (ver "Riesgos y dependencias" de la versión anterior de este documento, sin cambios). La abstracción `VeriFactuClient` (interfaz + `@ConditionalOnProperty` por entorno) ya deja la puerta abierta a añadir `VeriFactuClientProduccionImpl` sin tocar el resto del sistema cuando llegue ese momento — no se construye especulativamente aquí.
- **Generación de un ticket/PDF imprimible para entregar al cliente**: esta fase registra la venta y genera su huella/QR/registro VeriFactu — no genera un documento imprimible. Se asume que la entrega física del ticket al cliente (impresión, u otro medio) queda fuera de esta app, igual que hoy no existe una impresora de tickets integrada. Si se necesita, es una fase aparte.
- **Facturas rectificativas** (correcciones de importe manteniendo la venta viva, distintas de una anulación completa): el Reglamento las contempla como un tipo de operación adicional; esta fase solo cubre alta y anulación completa (ver "Contexto y objetivo").
- **Multi-emisor/multi-establecimiento**: sigue asumiéndose un único emisor (`configuracion_emisor_fiscal`, fila única), igual que la versión anterior.

## Desglose de subtareas

### Numeración de ventas
1. **[Backend, alta]** `VentaNumeracionService.siguienteNumero(String serie): Long` (`@Transactional`, `Propagation.REQUIRES_NEW` para poder invocarse desde dentro de la transacción de `VentaService.crear`): `SELECT ultimo_numero FROM secuencia_venta WHERE serie = :serie FOR UPDATE`; si no existe la fila, la inserta con `0` primero (`INSERT ... ON DUPLICATE KEY UPDATE ultimo_numero = ultimo_numero` o un `SELECT` previo + `INSERT IGNORE`, a elegir en implementación); incrementa y devuelve el nuevo valor. Acceso vía `EntityManager.createNativeQuery(...)` (no hay entidad JPA para `secuencia_venta`, ver `bbdd/integracion_verifactu-bbdd.md`) — el bloqueo de fila (`FOR UPDATE`) es el mecanismo real de exclusión mutua, no una anotación JPA de optimistic locking (que no serviría aquí: dos transacciones deben *esperarse*, no fallar y reintentar).

### Modelo de Venta
2. **[Backend, baja]** Entidad `Venta`: campos según `bbdd/integracion_verifactu-bbdd.md`. Enum `EstadoVenta {EMITIDA, ANULADA}`. `VentaRepository extends JpaRepository<Venta, Long>`: `findByFiltros(LocalDate desde, LocalDate hasta, EstadoVenta, Pageable)` (listado, mismo patrón que `FacturaRepository.findByFiltros`).
3. **[Backend, baja]** Entidad `VentaDesgloseIva` (`@ManyToOne` a `Venta`, `cascade = ALL, orphanRemoval = true` — las líneas de desglose viven y mueren con su venta, nunca se editan sueltas). `VentaDesgloseIvaRepository` no hace falta como repositorio propio si se accede siempre a través de `Venta.desgloseIva` (`@OneToMany`).

### Servicio de negocio — Venta
4. **[Backend, alta]** `VentaService.crear(VentaCreateDTO dto, Long adminId): VentaDetalleDTO` (`@Transactional`):
   - Valida `desgloseIva` no vacío y cada línea con `tipoImpositivo`/`baseImponible` `@Positive` o cero (permite tipo 0% para operaciones exentas); calcula `cuota = baseImponible * tipoImpositivo / 100` en el propio backend (nunca se acepta la cuota calculada por el cliente, para no confiar en aritmética de punto flotante ajena) y `importeTotal = Σ(baseImponible + cuota)`.
   - Si `clienteNif` viene informado, exige también `clienteNombre` (`@AssertTrue` a nivel de DTO o validación manual en el servicio) — una factura completa no puede identificar parcialmente al cliente. `clienteDomicilio` sigue siendo opcional incluso en factura completa (dato recomendable, no bloqueante para esta fase).
   - Pide el siguiente número (`VentaNumeracionService.siguienteNumero(dto.serie())`), persiste la `Venta` con `estado = EMITIDA`.
   - Llama a `VeriFactuRegistroService.generarRegistroVenta(venta, TipoRegistroVerifactu.ALTA, adminId)` (ver más abajo) **dentro de la misma transacción**: si `ConfiguracionEmisorFiscal` no está completa, la excepción existente `VeriFactuConfiguracionIncompletaException` (409) revierte también la creación de la venta — no puede quedar una venta persistida sin su registro de alta generado.
5. **[Backend, alta]** `VentaService.anular(Long ventaId, String motivo, Long adminId): VentaDetalleDTO` (`@Transactional`):
   - Carga la venta; si `estado != EMITIDA`, lanza `VentaYaAnuladaException` (409).
   - Busca el `entorno` del registro `ALTA` ya generado para esa venta (`RegistroFacturacionVerifactuRepository.findByVentaIdAndTipoRegistro(ventaId, ALTA)`) y genera la anulación **en ese mismo entorno**, no en el que esté activo globalmente en ese momento (`app.verifactu.entorno-activo` puede haber cambiado entretanto) — de lo contrario la cadena de esa venta quedaría partida entre dos entornos distintos, cosa que no tiene sentido semánticamente.
   - Llama a `VeriFactuRegistroService.generarRegistroVenta(venta, TipoRegistroVerifactu.ANULACION, adminId)` con ese entorno explícito.
   - Actualiza `venta.estado = ANULADA`, `fechaAnulacion`, `motivoAnulacion`, `anuladaPorAdminId`.
6. **[Backend, baja]** `VentaService.listar(LocalDate desde, LocalDate hasta, EstadoVenta, Pageable): Page<VentaListadoDTO>` / `obtenerDetalle(Long): VentaDetalleDTO` (incluye las líneas de `venta_desglose_iva` y, si existen, un resumen de sus registros VeriFactu — id, entorno, estado de envío — para poder enlazar desde el detalle de la venta al histórico de `/verifactu`).

### `VeriFactuRegistroService` — generalización
7. **[Backend, alta]** Extraer `private RegistroFacturacionVerifactu generarYEnviar(DatosRegistroVerifactu datos, EntornoVerifactu entorno, TipoRegistroVerifactu tipo, Long ventaId, Long adminId)`: contiene exactamente la lógica ya descrita en la versión anterior de este documento (buscar `huellaAnterior` por entorno, calcular huella y QR, persistir `PENDIENTE`, llamar a `VeriFactuClient` si el entorno lo requiere, actualizar `estadoEnvio`/`respuestaAeat`/`fechaEnvio`) — `ventaId` puede ser `null` (registro de prueba).
8. **[Backend, media]** `generarRegistroPrueba(...)` (ya existente) pasa a ser una fachada fina sobre `generarYEnviar(..., ventaId = null)` — sin cambio de comportamiento observable.
9. **[Backend, media]** `generarRegistroVenta(Venta venta, TipoRegistroVerifactu tipo, Long adminId, EntornoVerifactu entornoForzado = null): RegistroFacturacionVerifactuDTO`: construye `DatosRegistroVerifactu` a partir de la venta (`numeroFactura = venta.serie + "-" + venta.numero`, `descripcionOperacion`, `nifEmisor`/`nombreEmisor` desde `ConfiguracionEmisorFiscal`, `clienteNif`/`clienteNombre` si los tiene la venta, `desgloseIva` copiado de `venta.desgloseIva`, `importeTotal`/`cuotaTotal` agregados) y llama a `generarYEnviar(..., entornoForzado ?? app.verifactu.entorno-activo, tipo, venta.getId(), adminId)`. Si `ConfiguracionEmisorFiscal` no está completa, la misma `VeriFactuConfiguracionIncompletaException` ya existente aplica igual para ventas que para pruebas.
10. **[Backend, baja]** `RegistroFacturacionVerifactuRepository`: añadir `findByVentaIdAndTipoRegistro(Long ventaId, TipoRegistroVerifactu tipo): Optional<RegistroFacturacionVerifactu>` y `findByVentaId(Long ventaId): List<RegistroFacturacionVerifactu>` (para el detalle de venta, subtarea 6).

### Controller
11. **[Backend, baja]** Rutas nuevas en `Wuris.java` (clase `Wuris.Ventas`, mismo patrón que `Wuris.Facturas`): `BASE = /api/ventas`, `BY_ID = BASE + "/{id}"`, `BY_ID_ANULAR = BY_ID + "/anular"`.
12. **[Backend, media]** `VentaController`, todos los endpoints con `@PreAuthorize("hasAuthority('VENTAS:EDICION')")`:

    | Verbo | Ruta | DTO request | DTO response |
    |---|---|---|---|
    | GET | `/api/ventas` | query params (`desde`, `hasta`, `estado`, `Pageable`) | `Page<VentaListadoDTO>` |
    | GET | `/api/ventas/{id}` | — | `VentaDetalleDTO` |
    | POST | `/api/ventas` | `VentaCreateDTO` | `VentaDetalleDTO` (201) |
    | POST | `/api/ventas/{id}/anular` | `VentaAnularDTO {motivo: String @NotBlank}` | `VentaDetalleDTO` |

    `VentaCreateDTO {serie (@NotBlank), fechaExpedicion (@NotNull), descripcionOperacion (@NotBlank), clienteNif?, clienteNombre?, clienteDomicilio?, desgloseIva: List<VentaDesgloseIvaDTO> (@NotEmpty)}`, `VentaDesgloseIvaDTO {tipoImpositivo (@NotNull @PositiveOrZero), baseImponible (@NotNull @Positive)}` — igual criterio de validación (`@NotBlank`/`@NotNull`/`@Positive`) que el resto de DTOs de creación del proyecto.
13. **[Backend, baja]** `VerifactuController` (ya existente): sin cambios de rutas — `GET /api/verifactu/registros` puede ahora devolver filas con `ventaId` no nulo; el DTO `RegistroFacturacionVerifactuListadoDTO` se amplía con `ventaId: Long | null` para que el front pueda distinguir/enlazar un registro real de uno de prueba (ver `front/integracion_verifactu-front.md`).

### Transversal
14. **[Backend, baja]** Nuevas excepciones en `GlobalExceptionHandler`: `VentaYaAnuladaException` (409, junto al grupo de conflictos de estado ya usado por `VeriFactuEstadoInvalidoException`), `VentaNotFoundException` (404).
15. **[Backend, alta]** Tests de concurrencia de `VentaNumeracionService`: lanzar N hilos pidiendo `siguienteNumero("A")` simultáneamente produce N números consecutivos sin duplicados ni huecos (usar `ExecutorService` + `CountDownLatch`, patrón estándar de test de condición de carrera con JUnit).
16. **[Backend, media]** Tests unitarios de `VentaService.crear`: sin `clienteNombre` cuando `clienteNif` está informado, lanza error de validación; `importeTotal`/`cuotaTotal` calculados coinciden con la suma de `desgloseIva`; sin `ConfiguracionEmisorFiscal` completa, no se persiste ni la venta ni ningún registro (rollback completo de la transacción).
17. **[Backend, media]** Tests unitarios de `VentaService.anular`: anular una venta ya `ANULADA` lanza `VentaYaAnuladaException`; el registro de anulación generado usa el mismo `entorno` que el registro `ALTA` de esa venta, no el `entorno-activo` global si este ha cambiado.
18. **[Backend, media]** Tests unitarios de `VeriFactuRegistroService.generarRegistroVenta`: el registro persistido tiene `ventaId` no nulo y `desgloseIva` igual al de la venta; encadena correctamente con el último registro de ese `entorno` (venta o prueba, indistintamente — la cadena es por `entorno`, no por origen).
19. **[Backend, media]** Tests `@WebMvcTest` de `VentaController`: token `EMPLEADO` recibe 403 en todos los endpoints; `POST /api/ventas/{id}/anular` sobre una venta `ANULADA` devuelve 409.

## Decisiones técnicas

- **La generación del registro VeriFactu ocurre en la misma transacción que la creación/anulación de la venta, no de forma asíncrona ni en un paso de confirmación posterior**: no existe, legalmente, un estado intermedio de "venta emitida pero todavía sin registrar" — el Reglamento exige que el registro se genere de forma inherente al acto de facturar. Si la venta se persistiera sin su registro (por ejemplo, para permitir corregirla antes de "confirmar"), se estaría emitiendo una factura sin registrar, que es precisamente lo que la normativa prohíbe.
- **Eso no bloquea la operativa si la AEAT no responde**: igual que ya hacía `generarRegistroPrueba`, el registro se persiste con `estadoEnvio = PENDIENTE` *antes* de intentar la llamada a la AEAT; un fallo de red dentro de `generarYEnviar` deja el registro en `ERROR_ENVIO` (reintentable vía `POST /api/verifactu/registros/{id}/reenviar`, ya existente) sin abortar la transacción de la venta ni impedir que el negocio siga vendiendo — el envío puede reintentarse después, la venta no depende de que la AEAT esté disponible en el instante exacto.
- **La anulación de una venta se genera en el mismo `entorno` que su alta, nunca en el `entorno-activo` global del momento**: si no fuera así, cambiar la configuración global de entorno entre el alta y la anulación de una misma venta partiría su cadena de huellas entre dos entornos, lo cual no tiene sentido (la cadena de un `entorno` es una secuencia de eventos de ese entorno, no de una venta concreta).
- **Numeración con contador dedicado + bloqueo de fila, no `MAX(numero) + 1` calculado al vuelo**: ver justificación legal y técnica completa en `bbdd/integracion_verifactu-bbdd.md`.
- **`generarRegistroPrueba` y `generarRegistroVenta` comparten toda su lógica excepto el origen de los datos**: evita mantener dos copias del pipeline de huella/QR/envío — un cambio futuro en ese pipeline (por ejemplo, al validar el algoritmo de huella contra la especificación oficial) se aplica automáticamente a ambos caminos.

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/integracion_verifactu-bbdd.md`: sin sus tablas, ningún endpoint de este documento tiene dónde persistir nada.
- **Riesgo alto, ya señalado y sin resolver**: el algoritmo de huella y el esquema SOAP siguen sin validar contra la especificación oficial vigente de la AEAT — ahora con más motivo, al haber ventas reales de por medio. No usar contra `PRE` con datos de clientes reales hasta validarlo.
- **Riesgo alto — certificado digital de la empresa**: sigue sin resolverse (ver versión anterior); sin él, `PRE` no puede probarse contra la AEAT real, solo `LOCAL_SIMULADO`.
- **Riesgo medio — condición de carrera en la numeración, mitigado por diseño**: ver "Decisiones técnicas"/`bbdd/integracion_verifactu-bbdd.md"; cubierto por el test de concurrencia de la subtarea 15, pero conviene ejecutarlo bajo carga real (no solo el test unitario) antes de un uso con volumen alto de ventas simultáneas.
- **Riesgo medio — transacción larga**: `VentaService.crear` combina numeración (bloqueo de fila), persistencia y, si el entorno es `PRE`, una llamada de red síncrona a la AEAT dentro de la misma transacción — una AEAT lenta alarga el tiempo que la fila de `secuencia_venta` permanece bloqueada, pudiendo encolar ventas concurrentes de la misma serie. Aceptable para el volumen esperado de un restaurante; si se vuelve un cuello de botella real, la mitigación (fuera de esta fase) sería separar la numeración/persistencia de la llamada a la AEAT en dos pasos, aceptando el riesgo de un registro `PENDIENTE` más tiempo.
- **Riesgo bajo, heredado**: sin módulo de impresión/entrega de ticket (ver "No-objetivos"), el flujo de venta termina en la app sin un artefacto físico que entregar al cliente — asumido como fuera de alcance, no resuelto aquí.

## Criterios de aceptación técnicos

- `POST /api/ventas` sin `ConfiguracionEmisorFiscal` completa devuelve 409 y no persiste ni la venta ni ningún registro.
- `POST /api/ventas` con `clienteNif` pero sin `clienteNombre` devuelve 400.
- Dos llamadas concurrentes a `POST /api/ventas` con la misma `serie` reciben números consecutivos sin colisión ni hueco.
- Con `ConfiguracionEmisorFiscal` completa y entorno `LOCAL_SIMULADO`, `POST /api/ventas` crea la venta con `estado = EMITIDA` y su registro con `estadoEnvio = PENDIENTE`, sin ninguna llamada de red saliente.
- `POST /api/ventas/{id}/anular` sobre una venta `ANULADA` devuelve 409 sin generar un segundo registro de anulación.
- Anular una venta genera un registro `ANULACION` encadenado (`huella_anterior` = última huella de ese `entorno`), en el mismo `entorno` que el registro `ALTA` original de esa venta, con independencia del `entorno-activo` global en el momento de anular.
- `GET /api/ventas/{id}` devuelve el desglose de IVA y el estado de envío de su(s) registro(s) VeriFactu asociados.
- Un token `EMPLEADO` recibe 403 en todos los endpoints de `/api/ventas/**`.
- Todos los criterios de aceptación de la versión anterior de este documento (registro de prueba, reenvío, 403 en `/api/verifactu/**`) se mantienen intactos.
