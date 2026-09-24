# Plan de Implementación — Fase 2.1 (Backend): Facturas — modelo, CRUD de proveedores y listado con pestañas

Reutiliza el modelo de datos de [`../bbdd/fase2-1-bbdd.md`](../bbdd/fase2-1-bbdd.md) (tablas `proveedores`, `facturas`) y el `FileStorageService`/patrón de subida `multipart/form-data` ya construidos en `fase3-1-back.md` (segunda vez que se usa en el proyecto, subcarpeta `"facturas"` en vez de `"nominas"`). Expone el contrato que consumen [`../front/fase2-1-front.md`](../front/fase2-1-front.md), [`fase2-2-back.md`](./fase2-2-back.md) (exportación) y [`fase2-3-back.md`](./fase2-3-back.md) (extracción OCR, que extiende el endpoint de alta de este documento).

## Contexto y objetivo

Endpoints para que el Admin: (a) gestione el catálogo de proveedores/suministradores (alta rápida, listado por tipo); (b) vea un listado paginado de facturas, filtrable por proveedor/estado de pago/periodo (mes+año), separado por pestaña según `proveedor.tipo`; (c) dé de alta una factura manualmente (datos + archivo adjunto obligatorio, sin extracción automática); (d) marque una factura como pagada/pendiente con una acción rápida.

> **Nota (revisión posterior a esta fase)**: `producto`, `fechaFactura` y `fechaVencimiento` — descritos más abajo tal como se diseñaron originalmente — se eliminaron por completo en una revisión posterior (`bbdd/fase2-3-bbdd.md` `V34`/`V35`). `fechaImpresion` (añadida en `fase2-3-back.md`, `V33`) pasó a ser la fecha de referencia única, ahora obligatoria, de la que se derivan `mes`/`anio`. Los DTOs de este documento se describen ya con el campo actual (`fechaImpresion`) en vez del original (`fechaFactura`) para no dejar una referencia de contrato desactualizada; ver `bbdd/fase2-3-bbdd.md` para el detalle de la migración y el motivo del cambio. También cambió el tamaño de página por defecto del front (ver punto 9 y "Criterios de aceptación técnicos").

## Análisis y decisión de autorización

Igual que `fase3-1-back.md` con `NOMINAS:EDICION`: la vista `FACTURAS` ya es de edición exclusiva de `ADMIN` desde el seed de `fase0-bbdd.md` (`V5__seed_catalogo_permisos.sql`) — `EMPLEADO` no tiene ninguna fila de permiso para `FACTURAS`. Todos los endpoints de este documento se protegen con `@PreAuthorize("hasAuthority('FACTURAS:EDICION')")`, sin ningún rodeo adicional.

## Análisis y supuestos

- **"Búsqueda avanzada"** (enunciado del usuario) se interpreta igual que en `fase3-1-back.md`: filtros combinables (proveedor, estado de pago, periodo mes/año) sobre el listado paginado, no un motor de texto libre.
- **"Mes" del enunciado del usuario** se interpreta como periodo mes+año (igual que `periodoMes`/`periodoAnio` de nóminas), no solo el número de mes sin año — filtrar solo por "marzo" sin año mezclaría facturas de años distintos.
- **Las dos pestañas del front son la misma entidad `Factura`, filtrada por `proveedor.tipo`**: no hay endpoints separados por pestaña, un único parámetro `tipo` en el listado (ver "Desglose de subtareas"). Ver la decisión de modelado completa en `bbdd/fase2-1-bbdd.md`.
- **"Listado de proveedores para gestionar facturas de proveedores"** (enunciado del usuario) se interpreta como: la vista principal de cada pestaña sigue siendo un listado de *facturas* (con las columnas Empresa/Producto/Cuantía/Fecha/Estado que pide el usuario), y la "gestión de proveedores" es una capacidad de soporte (alta rápida de proveedor, necesaria para poder registrar una factura) — no una pantalla CRUD de proveedores como vista principal. Si el usuario quería una pantalla de proveedores independiente y más completa (editar/dar de baja proveedores existentes), hay que revisarlo en implementación.
- **Edición de una factura ya creada, más allá de `estadoPago`, queda fuera de alcance de esta fase**: el enunciado del usuario no pide editar datos de una factura existente (solo listar, filtrar, exportar y dar de alta vía OCR), así que no se construye un formulario de edición completo — solo el toggle de estado de pago. Añadir edición completa más adelante es una extensión natural si se pide.
- **Sin endpoint de borrado de facturas en esta fase**: no solicitado por el usuario: evita comprometerse a una política de borrado (¿físico? ¿soft-delete?) sin un caso de uso real.

## Desglose de subtareas

### Modelo
1. **[Backend, baja]** Entidad `Proveedor`: `id`, `nombre: String`, `cif: String` (nullable), `emailContacto: String` (nullable), `telefono: String` (nullable), `tipo: TipoProveedor` (`@Enumerated(EnumType.STRING)`, enum `{PROVEEDOR, SUMINISTRO}`), `createdAt`/`updatedAt`. `ProveedorRepository extends JpaRepository<Proveedor, Long>`: `findByTipo(TipoProveedor): List<Proveedor>` (para el `ui-select` de cada pestaña).
2. **[Backend, media]** Entidad `Factura`: `id`, `proveedor` (`@ManyToOne`, lazy), `numeroFactura: String` (nullable), `fechaImpresion: LocalDate`, `fechaEntrega: LocalDate` (nullable), `importeBase: BigDecimal` (nullable), `iva: BigDecimal` (nullable), `importeTotal: BigDecimal`, `mes: Integer`, `anio: Integer`, `estadoPago: EstadoPago` (`@Enumerated(EnumType.STRING)`, enum `{PENDIENTE, PAGADA}`), `estadoExtraccion: EstadoExtraccion` (`fase2-3-back.md`), `rutaArchivo: String`, `nombreArchivoOriginal: String`, `subidaPorAdminId: Long`, `fechaSubida: LocalDateTime`. `FacturaRepository extends JpaRepository<Factura, Long>`: `findByFiltros(TipoProveedor tipo, Long proveedorId, EstadoPago estado, Integer mes, Integer anio, Pageable): Page<Factura>` — vía `@Query` con parámetros nullable (`:param IS NULL OR campo = :param`) y `JOIN proveedor`, **no** cruce en memoria como `fase3-1-back.md` — a diferencia de nóminas (acotadas al número de empleados), las facturas crecen sin límite superior con los años, así que conviene paginar a nivel de BBDD desde el principio.
3. **[Backend, baja]** Reutilizar `FileStorageService` de `fase3-1-back.md` sin cambios de interfaz; subcarpeta `"facturas"` al llamar `guardar(archivo, "facturas")`.

### CRUD de proveedores
4. **[Backend, baja]** DTOs `ProveedorDTO {id, nombre, cif, emailContacto, telefono, tipo}` y `ProveedorCreateDTO {nombre, cif (nullable), emailContacto (nullable), telefono (nullable), tipo}` (`@NotBlank nombre`, `@NotNull tipo`).
5. **[Backend, baja]** `ProveedorService.crear(ProveedorCreateDTO): ProveedorDTO`, `listar(TipoProveedor tipoOpcional): List<ProveedorDTO>`.
6. **[Backend, baja]** `ProveedorController`, `@PreAuthorize("hasAuthority('FACTURAS:EDICION')")`: `GET /api/proveedores?tipo=` → `List<ProveedorDTO>`; `POST /api/proveedores` → `ProveedorDTO` (201).

### Listado de facturas con filtros y paginación
7. **[Backend, baja]** DTO `FacturaListadoDTO {id, empresa (proveedor.nombre), fechaImpresion, fechaEntrega, importeBase, iva, importeTotal, estadoPago}` — columnas de la tabla del front (Empresa/Fecha de impresión/Fecha de entrega/Base imponible/IVA/Cuantía/Estado); el resto de campos de la factura solo se exponen en el detalle (`FacturaDetalleDTO`) y en la exportación (`fase2-2-back.md`), nunca en el listado, para mantenerlo ligero.
8. **[Backend, media]** `FacturaService.listar(TipoProveedor tipo, Long proveedorIdOpcional, EstadoPago estadoOpcional, Integer mesOpcional, Integer anioOpcional, Pageable): Page<FacturaListadoDTO>`.
9. **[Backend, baja]** `GET /api/facturas?tipo=PROVEEDOR|SUMINISTRO&proveedorId=&estadoPago=&mes=&anio=&page=&size=` → `Page<FacturaListadoDTO>`, `@PreAuthorize("hasAuthority('FACTURAS:EDICION')")`. `tipo` es **obligatorio** (identifica la pestaña activa); `size` por defecto `25` si el cliente no lo especifica (`@PageableDefault(size = 25)`, tope máximo `100` validado en el servicio para evitar que el front pida páginas arbitrariamente grandes) — en la práctica el front (`front/fase2-1-front.md`) siempre envía `size=10` explícito, el valor por defecto del backend solo protege a otros clientes de la API.
10. **[Backend, baja]** DTO `FacturaDetalleDTO {id, proveedorId, empresa, tipo, numeroFactura, fechaImpresion, fechaEntrega, importeBase, iva, importeTotal, estadoPago, estadoExtraccion, nombreArchivoOriginal, fechaSubida}` — usado por la vista de detalle del front. `estadoExtraccion` se añade en `fase2-3-back.md`; no existe ningún `camposDinamicos` (mecanismo descartado, ver `bbdd/fase2-3-bbdd.md`).
11. **[Backend, baja]** `GET /api/facturas/{id}` → `FacturaDetalleDTO`, `FacturaNotFoundException` (404) si no existe.

### Alta manual y cambio de estado de pago
12. **[Backend, media]** DTO `FacturaCreateDTO` (campos de formulario `multipart/form-data`): `proveedorId`, `numeroFactura` (nullable), `fechaImpresion` (`@NotNull`), `fechaEntrega` (nullable), `importeBase` (nullable), `iva` (nullable), `importeTotal` (`@NotNull @Positive`), `estadoPago` (nullable, default `PENDIENTE` si no viene), `archivo` (`MultipartFile`, `@NotNull`), `origenOcr` (`fase2-3-back.md`, determina `estadoExtraccion`).
13. **[Backend, media]** `FacturaService.crear(FacturaCreateDTO, Long adminId): FacturaDetalleDTO` — valida que `archivo` sea de un tipo soportado (`image/jpeg`, `image/png`, `image/webp`, `application/pdf`, comprobando **content-type real**, mismo criterio que la validación de PDF en `fase3-1-back.md`); si es una imagen, se convierte a PDF antes de guardarse (ver nota más abajo); calcula `mes`/`anio` desde `fechaImpresion` (nunca confiar en un valor enviado por el cliente); guarda el archivo vía `FileStorageService`.
14. **[Backend, baja]** `POST /api/facturas` `multipart/form-data` → `FacturaDetalleDTO` (201), `@PreAuthorize("hasAuthority('FACTURAS:EDICION')")`.
15. **[Backend, baja]** `FacturaService.cambiarEstadoPago(Long facturaId, EstadoPago nuevoEstado): FacturaDetalleDTO`; `PATCH /api/facturas/{id}/estado-pago` body `{estadoPago}` → `FacturaDetalleDTO`, `@PreAuthorize("hasAuthority('FACTURAS:EDICION')")`.
16. **[Backend, baja]** `GET /api/facturas/{id}/archivo` → `ResponseEntity<Resource>` (mismo patrón de descarga que `GET /api/nominas/{id}/descargar` de `fase3-1-back.md`), `Content-Disposition: attachment; filename="<nombreArchivoOriginal>"`.

### Transversal
17. **[Backend, baja]** Nuevas excepciones: `FacturaNotFoundException` (404), `ProveedorNotFoundException` (404), `ArchivoNoValidoException` (reutilizable, ya existente desde `fase3-1-back.md` si el mensaje se generaliza; si no, una nueva específica de facturas) — registradas en `GlobalExceptionHandler`.
18. **[Backend, media]** Tests `@WebMvcTest`: token `EMPLEADO` recibe 403 en todos los endpoints; `GET /api/facturas` sin `tipo` responde 400; el tamaño de página por defecto es 25 y un `size` mayor que el tope configurado se recorta; crear una factura con `content-type` no soportado responde 400 sin guardar nada ni crear fila; `PATCH .../estado-pago` solo modifica ese campo.
19. **[Backend, baja]** Unit test de `FacturaService.crear`: `mes`/`anio` calculados coinciden con la `fechaImpresion` recibida en el DTO, siempre recalculados en el backend (nunca aceptados directamente del cliente, que no los envía — `mes`/`anio` no existen en `FacturaCreateDTO`, ver punto 12).

## Decisiones técnicas

- **Paginación a nivel de BBDD (`@Query` + `Pageable`) desde el principio**, a diferencia del cruce en memoria de `fase3-1-back.md`: las facturas no están acotadas por una entidad finita (como los empleados), se acumulan indefinidamente con los años — paginar en memoria dejaría de escalar mucho antes.
- **`tipo` obligatorio en el listado**: el front siempre está en una pestaña concreta; no existe un caso de uso de "ver todas las facturas de golpe" en esta fase (podría añadirse una pestaña "Todas" más adelante si se pide).
- **Alta de proveedor embebida en el flujo de alta de factura** (vía `POST /api/proveedores` desde un modal de "nuevo proveedor" en el front, ver `front/fase2-1-front.md`), sin una pantalla CRUD de proveedores independiente: cubre el caso de uso real (dar de alta un proveedor nuevo al registrar su primera factura) sin construir una vista completa no pedida explícitamente por el usuario.
- **Reutilización íntegra de `FileStorageService`**: segunda vez que se usa en el proyecto (la primera fue nóminas). Amplía su interfaz con una sobrecarga `guardar(byte[] contenido, String nombreOriginal, String subcarpeta)` (revisión posterior, ver abajo) para poder guardar un PDF generado en memoria sin pasar por `MultipartFile`.
- **Conversión automática de imagen a PDF antes de guardar** (revisión posterior tras confirmar con el usuario que el Admin sube fotos de facturas desde el móvil): si `archivo.getContentType()` empieza por `image/`, `FacturaService` lo convierte a un PDF de una sola página (`ImagenPdfConverter`, Apache PDFBox — imagen centrada y escalada sobre A4, horizontal si la foto es más ancha que alta) y guarda el PDF resultante, nunca la imagen original; si ya es `application/pdf`, se guarda tal cual. Así el archivo almacenado (`rutaArchivo`) queda siempre en un formato consistente con independencia del origen — coherente con el criterio ya fijado en `plan-implementacion-general.md` de tratar PDF como formato de almacenamiento canónico para documentos adjuntos.

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase2-1-bbdd.md`: sin las tablas `proveedores`/`facturas`, ningún endpoint de este documento tiene dónde persistir nada.
- **Dependencia dura** con `fase3-1-back.md`: reutiliza `FileStorageService` tal cual — si su interfaz cambia en el futuro, este módulo se ve afectado.
- **Riesgo medio**: sin edición completa de factura en esta fase, un error de captura manual (p. ej. importe mal tecleado) solo se puede corregir borrando y recreando — no hay endpoint de borrado tampoco (ver "Análisis y supuestos"). Si en la práctica esto resulta molesto, es la primera extensión natural a añadir.
- **Riesgo bajo**: `findByFiltros` con varios parámetros nullable vía `@Query` puede degradar el uso de índices en MySQL si no se escribe con cuidado (`:param IS NULL OR campo = :param` a veces impide que el optimizador use el índice) — a vigilar en pruebas de carga si el volumen de facturas crece mucho; los índices de `bbdd/fase2-1-bbdd.md` cubren los filtros individuales más comunes.

## Criterios de aceptación técnicos

- `GET /api/facturas?tipo=PROVEEDOR` devuelve solo facturas cuyo proveedor tiene `tipo = PROVEEDOR`, paginadas de 10 en 10 (tamaño que envía el front; 25 si un cliente de la API no especifica `size`).
- Combinar `proveedorId`+`estadoPago`+`mes`+`anio` filtra correctamente sobre el resultado ya filtrado por `tipo`.
- Crear una factura con un archivo que no sea `image/jpeg`, `image/png`, `image/webp` ni `application/pdf` responde 400 sin crear ninguna fila.
- Crear una factura con `fechaImpresion = 2026-03-15` persiste `mes = 3`, `anio = 2026` aunque el cliente no los envíe.
- Crear una factura subiendo una imagen (`image/jpeg`/`image/png`/`image/webp`) guarda un PDF (no la imagen original) en `rutaArchivo`.
- `PATCH /api/facturas/{id}/estado-pago` cambia únicamente `estadoPago`, sin tocar el resto de campos.
- Un token `EMPLEADO` recibe 403 en los endpoints de este documento.
