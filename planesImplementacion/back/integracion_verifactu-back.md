# Plan de Implementación — Integración VeriFactu (Backend)

Reutiliza el modelo de datos de [`../bbdd/integracion_verifactu-bbdd.md`](../bbdd/integracion_verifactu-bbdd.md) (tablas `configuracion_emisor_fiscal`, `registro_facturacion_verifactu`). Independiente del módulo `Factura`/`Proveedor` (facturas *recibidas*, `back/fase2-1-back.md` y siguientes) — no se toca ningún archivo de ese módulo. Introduce en el proyecto, por primera vez: cálculo de una **cadena de huellas criptográficas** (hash-chaining), generación de **códigos QR**, y un cliente HTTP con **autenticación mTLS por certificado de cliente** contra un servicio externo. Expone el contrato que consume [`../front/integracion_verifactu-front.md`](../front/integracion_verifactu-front.md).

## Contexto y objetivo

Alcance acordado explícitamente con el usuario (ver conversación de planificación): la app **no emite hoy facturas a clientes** — no existe tabla de clientes, líneas de venta ni TPV — por lo que esta fase **no construye ese módulo de facturación de venta**. En su lugar, prepara el terreno técnico reutilizable de VeriFactu para poder validarlo de extremo a extremo (local → preproducción de la AEAT) antes de que exista una factura de venta real que lo dispare automáticamente:

- `HuellaVerifactuService`: calcula la huella (hash) de un registro de facturación, encadenada con la del registro anterior.
- `VeriFactuQrService`: genera el contenido y la imagen del código QR exigido por la normativa.
- `VeriFactuClient`: envía un registro al entorno de preproducción (*pre*/sandbox) de la AEAT, autenticado con el certificado digital de la empresa.
- Un endpoint de **generación de registros de prueba** (`POST /api/verifactu/registros/prueba`), que simula los datos que en el futuro aportará una factura de venta real, para poder probar todo el pipeline sin depender de ese módulo.

Cuando exista el módulo de facturación de venta, llamará a `VeriFactuRegistroService.generarRegistro(...)` con los datos reales de cada factura emitida, en vez de al endpoint de prueba — ese es el punto de enganche futuro, documentado aquí para que quede localizado.

## Análisis y supuestos

- **El algoritmo exacto de la huella (orden y formato de los campos concatenados, mayúsculas/minúsculas del hash) y el WSDL/XSD del servicio SOAP de la AEAT deben validarse contra la documentación técnica oficial vigente antes de cualquier envío a un entorno real de la AEAT** (ni siquiera el de pre, que sigue siendo un sistema de la AEAT). Esta fase implementa la mejor interpretación pública disponible en el momento de escribir este documento (concatenación de NIF emisor, serie+número, fecha de expedición, tipo de registro, importe total, cuota total, huella anterior y fecha/hora de generación, seguida de SHA-256 en hexadecimal), aislada por completo detrás de `HuellaVerifactuService`/`VeriFactuClient` para poder corregirla sin tocar el resto del sistema si al validar contra la especificación oficial difiere en algún detalle.
- **Solo se contemplan los entornos `LOCAL_SIMULADO` y `PRE`** (alineado con `bbdd/integracion_verifactu-bbdd.md`): en `LOCAL_SIMULADO` se calcula huella y QR y se persiste el registro, pero **no** se llama a la AEAT (útil para desarrollar/depurar sin depender de red ni certificado); en `PRE` se llama de verdad al entorno de preproducción de la AEAT. `PRODUCCION` no existe todavía en el código — se añadirá junto con el módulo de facturación de venta real.
- **Requiere un certificado digital de la empresa (o uno específico de pruebas, si la AEAT ofrece uno para el sandbox) en formato PKCS#12** para autenticar la conexión mTLS con el entorno de pre — obtenerlo es un prerrequisito operativo, no bloquea escribir ni probar el código en `LOCAL_SIMULADO` (mismo criterio ya aplicado a la aprobación de la API de Google en `back/fase4-1-back.md`: no bloquea el desarrollo, sí el uso contra el sistema real).
- **No se modifica en ningún punto el módulo `Factura`/`Proveedor` existente**: es una decisión de alcance, no un descubrimiento técnico — esa tabla son facturas recibidas de proveedores, ajenas a la obligación VeriFactu (que regula las facturas que el negocio emite).

## Desglose de subtareas

### Modelo y configuración
1. **[Backend, baja]** Entidad `ConfiguracionEmisorFiscal` (`@Id` fijo a `1L`, sin `@GeneratedValue`, mismo patrón que `ConfiguracionUbicacion`): `id`, `nif`, `razonSocial`, `nombreComercial`, `direccion`, `codigoPostal`, `municipio`, `provincia`, `updatedAt`, `actualizadoPorAdminId` — todos nullable salvo `id`. `ConfiguracionEmisorFiscalRepository extends JpaRepository<ConfiguracionEmisorFiscal, Long>`: `findById(1L)` como único acceso real (misma convención documentada, no forzada por el repositorio, que en `ConexionGoogleBusinessRepository`).
2. **[Backend, baja]** Enums `EntornoVerifactu {LOCAL_SIMULADO, PRE}`, `TipoRegistroVerifactu {ALTA, ANULACION}`, `EstadoEnvioVerifactu {PENDIENTE, ENVIADA, ACEPTADA, ACEPTADA_CON_ERRORES, RECHAZADA, ERROR_ENVIO}`.
3. **[Backend, baja]** Entidad `RegistroFacturacionVerifactu`: campos según `bbdd/integracion_verifactu-bbdd.md`, `respuestaAeat` mapeado como `String` (JSON crudo, sin convertidor a objeto — se persiste tal cual se recibe, se deserializa solo al construir el DTO de detalle). `RegistroFacturacionVerifactuRepository extends JpaRepository<RegistroFacturacionVerifactu, Long>`: `findTopByEntornoOrderByIdDesc(EntornoVerifactu): Optional<RegistroFacturacionVerifactu>` (para encontrar la `huellaAnterior` al generar uno nuevo) y `findByFiltros(EntornoVerifactu, EstadoEnvioVerifactu, Pageable)` (listado).
4. **[Backend, media]** Config nueva en `application.properties`, siguiendo el estilo ya usado para Google (sensible sin valor por defecto) pero con una diferencia deliberada: el arranque **no** debe fallar si falta el certificado, porque `LOCAL_SIMULADO` tiene que poder funcionar sin él (a diferencia de `GOOGLE_CLIENT_ID`, que si falta rompe el arranque siempre). La validación de que el certificado existe se hace de forma perezosa, la primera vez que se intenta un envío real a `PRE`.
   ```properties
   app.verifactu.entorno-activo=${VERIFACTU_ENTORNO:LOCAL_SIMULADO}
   app.verifactu.aeat.pre-url=${VERIFACTU_AEAT_PRE_URL:}
   app.verifactu.certificado.path=${VERIFACTU_CERT_PATH:}
   app.verifactu.certificado.password=${VERIFACTU_CERT_PASSWORD:}
   ```

### Huella y QR
5. **[Backend, alta]** `HuellaVerifactuService.calcular(DatosRegistroVerifactu datos, String huellaAnterior): String` — `DatosRegistroVerifactu` es un `record` interno con los campos que intervienen en la huella (nif emisor, serie+número, fecha expedición, tipo de registro, importe total, cuota total, fecha/hora de generación). Construye la cadena a firmar concatenando esos campos en el orden documentado en "Análisis y supuestos" y calcula `SHA-256` en hexadecimal mayúsculas (`MessageDigest.getInstance("SHA-256")`, sin dependencias externas). Determinista: la misma entrada produce siempre la misma huella; cualquier cambio en cualquiera de los campos, o en la huella anterior, cambia el resultado por completo.
6. **[Backend, media]** `VeriFactuQrService`:
   - `generarContenido(DatosRegistroVerifactu datos): String` — construye la URL de cotejo de facturas de la sede electrónica de la AEAT con los parámetros exigidos (NIF emisor, número+serie, fecha, importe total) codificados como query params.
   - `generarImagenPng(String contenido): byte[]` — usa **ZXing** (`com.google.zxing:core` + `com.google.zxing:javase`, dependencias nuevas en `pom.xml`) para generar el QR y volcarlo a PNG en memoria (`ByteArrayOutputStream`). Sin dependencias adicionales de manejo de imágenes más allá de las que ya trae el JDK.

### Cliente AEAT
7. **[Backend, alta]** `VeriFactuClient` (interfaz, mismo patrón que `GoogleBusinessProfileClient`/`FacturaExtractionService`): `enviarRegistro(RegistroAeatRequest request): RegistroAeatResponse {aceptado: boolean, codigoRespuesta: String, descripcion: String, respuestaCruda: String}`.
8. **[Backend, alta]** `VeriFactuClientAeatImpl implements VeriFactuClient` (`@Component`, activo solo cuando `app.verifactu.entorno-activo=PRE`, vía `@ConditionalOnProperty`): construye un `RestClient` con un `ClientHttpRequestFactory` cuyo `SSLContext` carga el certificado PKCS#12 (`app.verifactu.certificado.path`/`.password`) para autenticación mTLS, apunta a `app.verifactu.aeat.pre-url`, construye el envelope SOAP/XML del servicio `SistemaFacturacion` de la AEAT (ver "Análisis y supuestos" sobre validar el esquema oficial) y parsea la respuesta. Si el certificado no está configurado o la llamada falla (red, TLS, respuesta de error), lanza `VeriFactuNoDisponibleException`.
9. **[Backend, media]** `VeriFactuClientSimuladoImpl implements VeriFactuClient` (`@Component`, activo cuando `app.verifactu.entorno-activo=LOCAL_SIMULADO`, vía `@ConditionalOnProperty` con `matchIfMissing = true`): no realiza ninguna llamada de red; devuelve siempre `{aceptado: true, codigoRespuesta: "SIMULADO", descripcion: "Registro no enviado — entorno LOCAL_SIMULADO", respuestaCruda: null}`. Evita que un desarrollador llame a la AEAT por accidente mientras trabaja en local.

### Servicio de negocio
10. **[Backend, alta]** `VeriFactuRegistroService.generarRegistroPrueba(RegistroVerifactuPruebaRequestDTO dto, Long adminId): RegistroFacturacionVerifactuDTO`:
    - Carga `ConfiguracionEmisorFiscal`; si `nif` o `razonSocial` están vacíos, lanza `VeriFactuConfiguracionIncompletaException` (409) — no se genera ningún registro sin datos fiscales del emisor.
    - Determina el entorno activo (`app.verifactu.entorno-activo`) y busca `huellaAnterior` con `findTopByEntornoOrderByIdDesc` (la cadena es independiente por `entorno`, ver `bbdd/integracion_verifactu-bbdd.md`).
    - Calcula `huellaActual` (`HuellaVerifactuService`) y `qrContenido`/imagen (`VeriFactuQrService`).
    - Persiste el registro con `estadoEnvio = PENDIENTE`.
    - Si el entorno activo es `PRE`, llama a `VeriFactuClient.enviarRegistro(...)` y actualiza `estadoEnvio` (`ACEPTADA`/`ACEPTADA_CON_ERRORES`/`RECHAZADA`/`ERROR_ENVIO`), `respuestaAeat` y `fechaEnvio` en el mismo registro ya creado — nunca antes de persistirlo (si la llamada a la AEAT lanza una excepción, el registro ya existe en BBDD con `estadoEnvio = ERROR_ENVIO`, no se pierde el intento).
11. **[Backend, media]** `VeriFactuRegistroService.reenviar(Long id): RegistroFacturacionVerifactuDTO` — solo permitido si `estadoEnvio` es `RECHAZADA` o `ERROR_ENVIO` (si no, `VeriFactuEstadoInvalidoException`, 409); repite la llamada a `VeriFactuClient` sobre el registro ya existente (mismo `huellaActual`, no se recalcula nada) y actualiza `estadoEnvio`/`respuestaAeat`/`fechaEnvio`.
12. **[Backend, baja]** `VeriFactuRegistroService.listar(EntornoVerifactu, EstadoEnvioVerifactu, Pageable): Page<RegistroFacturacionVerifactuListadoDTO>` y `obtenerDetalle(Long): RegistroFacturacionVerifactuDTO` (incluye la imagen QR en Base64 y `respuestaAeat` deserializada).
13. **[Backend, baja]** `ConfiguracionEmisorFiscalService.obtener(): ConfiguracionEmisorFiscalDTO` / `actualizar(ConfiguracionEmisorFiscalUpdateDTO, Long adminId): ConfiguracionEmisorFiscalDTO`.

### Controller
14. **[Backend, baja]** Rutas nuevas en `Wuris.java` (clase `Wuris.Verifactu`, mismo patrón que `Wuris.Facturas`): `BASE = /api/verifactu`, `EMISOR = BASE + "/emisor"`, `REGISTROS = BASE + "/registros"`, `REGISTROS_PRUEBA = REGISTROS + "/prueba"`, `REGISTRO_BY_ID = REGISTROS + "/{id}"`, `REGISTRO_REENVIAR = REGISTRO_BY_ID + "/reenviar"`, `ESTADO = BASE + "/estado"`.
15. **[Backend, media]** `VerifactuController`, todos los endpoints con `@PreAuthorize("hasAuthority('VERIFACTU:EDICION')")`:

    | Verbo | Ruta | DTO request | DTO response |
    |---|---|---|---|
    | GET | `/api/verifactu/emisor` | — | `ConfiguracionEmisorFiscalDTO` |
    | PUT | `/api/verifactu/emisor` | `ConfiguracionEmisorFiscalUpdateDTO` | `ConfiguracionEmisorFiscalDTO` |
    | GET | `/api/verifactu/estado` | — | `VerifactuEstadoDTO {entornoActivo, certificadoConfigurado: boolean, emisorConfigurado: boolean, ultimoRegistro: RegistroFacturacionVerifactuListadoDTO \| null}` |
    | GET | `/api/verifactu/registros` | query params (`entorno`, `estadoEnvio`, `Pageable`) | `Page<RegistroFacturacionVerifactuListadoDTO>` |
    | GET | `/api/verifactu/registros/{id}` | — | `RegistroFacturacionVerifactuDTO` |
    | POST | `/api/verifactu/registros/prueba` | `RegistroVerifactuPruebaRequestDTO` | `RegistroFacturacionVerifactuDTO` (201) |
    | POST | `/api/verifactu/registros/{id}/reenviar` | — | `RegistroFacturacionVerifactuDTO` |

    `RegistroVerifactuPruebaRequestDTO {numeroFactura, serieFactura?, fechaExpedicion, importeTotal, cuotaTotal, tipoRegistro? (default ALTA)}` — simula los datos mínimos que en el futuro aportará una factura de venta real; se valida con `@NotBlank`/`@NotNull`/`@Positive` igual que el resto de DTOs de creación del proyecto (p. ej. `FacturaCreateDTO`).
16. **[Backend, baja]** `GET /api/verifactu/registros/{id}/qr` → devuelve la imagen PNG directamente (`ResponseEntity<byte[]>` con `Content-Type: image/png`), en vez de forzar al front a decodificar Base64 para pintar un `<img>` — mismo criterio que `GET /api/facturas/{id}/archivo` (descarga directa de un recurso binario).

### Transversal
17. **[Backend, baja]** Nuevas excepciones, registradas en `GlobalExceptionHandler` siguiendo el agrupamiento por código existente: `VeriFactuConfiguracionIncompletaException` (409, junto al grupo de conflictos de estado), `VeriFactuEstadoInvalidoException` (409), `VeriFactuNoDisponibleException` (502, en el mismo grupo que `OcrNoDisponibleException`/`ConexionGoogleNoDisponibleException`), `RegistroVerifactuNotFoundException` (404, junto a `FacturaNotFoundException`).
18. **[Backend, media]** Tests unitarios de `HuellaVerifactuService`: la misma entrada produce siempre la misma huella; cambiar cualquier campo (incluida `huellaAnterior`) cambia el resultado; huella con `huellaAnterior = null` (primer registro de una cadena) no lanza excepción.
19. **[Backend, baja]** Test unitario de `VeriFactuQrService`: el contenido generado incluye los parámetros esperados; la imagen PNG generada es decodificable (`ImageIO.read` sobre el `byte[]`).
20. **[Backend, media]** Tests unitarios de `VeriFactuRegistroService` con `VeriFactuClient` mockeado (**nunca** se llama al cliente SOAP real en tests, mismo criterio que Tesseract/Google): generar un registro sin `ConfiguracionEmisorFiscal` completa lanza `VeriFactuConfiguracionIncompletaException` sin llegar a calcular huella; dos registros generados consecutivamente en el mismo entorno encadenan correctamente (`huellaAnterior` del segundo = `huellaActual` del primero); un fallo del `VeriFactuClient` deja el registro persistido con `estadoEnvio = ERROR_ENVIO`, no lanza una excepción no controlada.
21. **[Backend, media]** Tests `@WebMvcTest`: token `EMPLEADO` recibe 403 en todos los endpoints; `POST /registros/{id}/reenviar` sobre un registro en `ACEPTADA` devuelve 409.

## Decisiones técnicas

- **Dos implementaciones de `VeriFactuClient` seleccionadas por `@ConditionalOnProperty` (simulada vs. AEAT real), en vez de un único cliente con un `if` interno**: mismo criterio de abstracción ya aplicado en `FacturaExtractionService`/`GoogleBusinessProfileClient` — permite que `LOCAL_SIMULADO` no cargue ni valide nunca la configuración del certificado, y hace explícito en el árbol de beans qué implementación está activa en cada entorno desplegado.
- **La cadena de huellas se rompe deliberadamente entre `LOCAL_SIMULADO` y `PRE`** (una cadena por `entorno`, ver `bbdd/integracion_verifactu-bbdd.md`): permite repetir pruebas locales sin condicionar ni depender de lo ya enviado al sandbox de la AEAT.
- **El registro se persiste con `estadoEnvio = PENDIENTE` antes de intentar enviarlo, y se actualiza después** (nunca se calcula todo en memoria y se persiste solo si el envío tiene éxito): si la llamada a la AEAT falla, el registro (con su huella ya calculada y encadenada) no se pierde — queda con `estadoEnvio = ERROR_ENVIO` y se puede reintentar (`reenviar`) sin recalcular la huella, que ya forma parte de la cadena.
- **Sin llamada a la AEAT en el arranque de la aplicación**: a diferencia de `GOOGLE_CLIENT_ID` (falla el arranque si falta), la configuración del certificado se valida de forma perezosa — de otro modo, arrancar el backend en `LOCAL_SIMULADO` (el caso común en desarrollo) exigiría igualmente tener un certificado de la AEAT configurado, lo que no aporta nada en ese entorno.
- **QR generado al vuelo desde `qr_contenido`, nunca persistido como imagen**: es un dato derivado, barato de regenerar y no tiene sentido ocupar espacio ni mantenerlo sincronizado si cambiara la lógica de generación de la imagen.

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/integracion_verifactu-bbdd.md`: sin sus tablas, ningún endpoint de este documento tiene dónde persistir nada.
- **Riesgo alto — algoritmo de huella y esquema SOAP sin validar contra la especificación oficial vigente de la AEAT**: esta fase implementa la mejor interpretación pública disponible (ver "Análisis y supuestos"); antes de cualquier envío a un entorno real de la AEAT (incluido *pre*) hay que contrastar el formato exacto de la cadena a firmar y el WSDL/XSD publicados oficialmente — aislado detrás de `HuellaVerifactuService`/`VeriFactuClient` para poder corregirlo sin impacto en el resto del sistema.
- **Riesgo alto — certificado digital de la empresa**: sin él, no se puede probar nada contra el entorno de pre real (solo `LOCAL_SIMULADO`); conseguirlo/confirmar si la AEAT ofrece uno específico de pruebas para el sandbox es un trámite fuera del control de este documento.
- **Riesgo medio — dependencia de un servicio externo fuera de control del proyecto**: cambios en el esquema, límites de cuota o caídas del entorno de pre de la AEAT se traducen en `VeriFactuNoDisponibleException`; mitigado por el estado `ERROR_ENVIO` visible y reintentable (`reenviar`), sin tumbar el resto de la app.
- **Riesgo bajo**: al no existir todavía el módulo de facturación de venta real, todos los registros generados son de prueba — cuando ese módulo se construya, tendrá que decidir explícitamente si reutiliza `VeriFactuRegistroService.generarRegistroPrueba` (renombrándolo/generalizándolo) o si añade un método `generarRegistro` paralelo con datos reales; no resuelto aquí a propósito.

## Criterios de aceptación técnicos

- `POST /api/verifactu/registros/prueba` sin `ConfiguracionEmisorFiscal` completa devuelve 409 sin crear ningún registro.
- Con la configuración fiscal completa y entorno `LOCAL_SIMULADO`, `POST /api/verifactu/registros/prueba` crea un registro con `estadoEnvio = PENDIENTE` sin ninguna llamada de red saliente.
- Con entorno `PRE` y certificado configurado, `POST /api/verifactu/registros/prueba` llama de verdad al sandbox de la AEAT y actualiza `estadoEnvio` según la respuesta recibida.
- Generar dos registros consecutivos en el mismo entorno produce `huellaAnterior` del segundo igual a `huellaActual` del primero; en entornos distintos, sus cadenas no se mezclan.
- `POST /api/verifactu/registros/{id}/reenviar` sobre un registro en `ACEPTADA` devuelve 409 sin llamar al `VeriFactuClient`.
- Un fallo simulado del `VeriFactuClient` (mock que lanza excepción) deja el registro en BBDD con `estadoEnvio = ERROR_ENVIO`, sin perder la huella ya calculada.
- Un token `EMPLEADO` recibe 403 en todos los endpoints de `/api/verifactu/**`.
