# Plan de Implementación — Fase 5.1 (Backend): Configuración — datos de empresa (nombre y logo)

Reutiliza el modelo de datos de [`../bbdd/fase5-1-bbdd.md`](../bbdd/fase5-1-bbdd.md) (tabla `configuracion_empresa`). Extiende `ConfiguracionController.java` ya existente (`back/fase1-1-back.md`) con una tercera sección — no crea un controller nuevo. Reutiliza `FileStorageService` (`back/fase3-1-back.md`), mismo patrón que el archivo de una factura. Expone el contrato que consume [`../front/fase5-1-front.md`](../front/fase5-1-front.md).

> **Ampliación posterior (misma sesión que la Fase 6.1 — recuperación de contraseña)**: se añadió el campo `emailRemitente` a `ConfiguracionEmpresa`, para poder elegir desde qué dirección salen los correos que envía la app (nóminas y recuperación de contraseña), en vez de depender siempre de `spring.mail.username`. Migración `V41__add_email_remitente_a_configuracion_empresa.sql` (documentada en [`../bbdd/fase6-1-bbdd.md`](../bbdd/fase6-1-bbdd.md), no en `bbdd/fase5-1-bbdd.md`, porque se implementó en esa entrega posterior). Ver el desglose marcado como "añadido" más abajo.

## Contexto y objetivo

Permitir al Admin configurar el nombre del negocio y subir/reemplazar su logo, y que **cualquier usuario autenticado** (no solo el Admin) pueda leer ese nombre y logo para mostrarlos en la cabecera de la aplicación — a diferencia de `configuracion_ubicacion`/`configuracion_vacaciones` (cuya lectura ya está restringida a `EMPLEADOS:EDICION` porque solo se consultan dentro de pantallas de Admin), aquí la lectura es de uso general en toda la app.

## Análisis y supuestos

- **Reutiliza `EMPLEADOS:EDICION` para la escritura**, igual que el resto de `ConfiguracionController` — no se introduce ninguna vista/permiso nuevo (ver `bbdd/fase5-1-bbdd.md`).
- **La lectura (`GET`) se abre a cualquier usuario autenticado, sin exigir ninguna autoridad concreta**: es una decisión deliberada, distinta del resto del controller — el nombre y el logo de la empresa se muestran en la cabecera de la app para todos (Admin y Empleado), no son un dato de gestión exclusivo de Admin. La escritura (`PUT`) sigue exigiendo `EMPLEADOS:EDICION`.
- **El logo es una imagen, nunca un PDF**: a diferencia de `facturas` (que admite `application/pdf`), aquí solo tiene sentido `image/jpeg`, `image/png`, `image/webp` — mismo `Set` de content-types que `FacturaService`, sin la entrada de PDF.
- **Actualizar solo el nombre, sin volver a subir logo, debe ser posible**: el `PUT` acepta el fichero de logo como opcional; si no se envía, se conserva el `logoRuta` ya existente.

## Desglose de subtareas

### Modelo
1. **[Backend, baja]** Entidad `ConfiguracionEmpresa` (`@Id` fijo a `1L`, sin `@GeneratedValue`, mismo patrón que `ConfiguracionUbicacion`): `id`, `nombreEmpresa`, `logoRuta` (nullable), `logoNombreOriginal` (nullable), `updatedAt`, `actualizadoPorAdminId` (nullable). `ConfiguracionEmpresaRepository extends JpaRepository<ConfiguracionEmpresa, Long>`: `findById(1L)` como único acceso real.
   - **[Backend, baja] Añadido (ampliación posterior, `V41`)**: nuevo campo `emailRemitente` (nullable, `@Column(name = "email_remitente")`) — remitente ("From") de los correos que envía la app; si es `null`, `EmailServiceImpl` cae a `spring.mail.username`.

### Servicio
2. **[Backend, media]** `ConfiguracionEmpresaService`:
   - `obtener(): ConfiguracionEmpresa` — `findById(1L)` (la fila siempre existe, sembrada en `V45`, igual que `ConfiguracionUbicacionService.obtener()`).
   - `actualizar(String nombreEmpresa, MultipartFile logo, Long adminId): ConfiguracionEmpresa` (`@Transactional`): valida `nombreEmpresa` no vacío (ya cubierto por `@NotBlank` en el DTO de entrada, ver punto 4). Si `logo != null`: valida su `contentType` contra `Set.of("image/jpeg", "image/png", "image/webp")` (si no coincide, `LogoEmpresaFormatoNoValidoException`, 400); guarda el nuevo fichero (`fileStorageService.guardar(logo, "empresa")`); si ya había un `logoRuta` anterior, lo borra (`fileStorageService.eliminar(rutaAnterior)`) **después** de que el nuevo se haya guardado con éxito (para no quedarse sin logo si el guardado del nuevo fallara a mitad); actualiza `logoRuta`/`logoNombreOriginal`. Si `logo == null`, no toca esos dos campos. Actualiza siempre `nombreEmpresa`, `updatedAt`, `actualizadoPorAdminId`.
   - ~~`actualizar(String, MultipartFile, Long)`~~ **Firma ampliada (`V41`)**: `actualizar(String nombreEmpresa, MultipartFile logo, String emailRemitente, Long adminId)`. Blank/vacío se normaliza a `null` (`emailRemitente != null && !emailRemitente.isBlank() ? emailRemitente : null`) — el front puede mandar `""` al vaciar el campo, y eso debe guardarse como "sin remitente configurado", no como una cadena vacía persistida.

### Controller
3. **[Backend, media]** Nuevas rutas en `Wuris.java` (`Wuris.Configuracion`, ampliando la clase existente): `EMPRESA = "/api/configuracion/empresa"`, `EMPRESA_LOGO = EMPRESA + "/logo"`.
4. **[Backend, media]** Ampliar `ConfiguracionController` (no crear uno nuevo) con:

   | Verbo | Ruta | Permiso | DTO request | DTO response |
   |---|---|---|---|---|
   | GET | `/api/configuracion/empresa` | Ninguno (cualquier autenticado) | — | `ConfiguracionEmpresaResponseDTO {nombreEmpresa, logoUrl: string \| null, emailRemitente: string \| null}` |
   | PUT | `/api/configuracion/empresa` (multipart) | `EMPLEADOS:EDICION` | `nombreEmpresa` (`@NotBlank @Size(max=150)`) + `logo` (`MultipartFile`, opcional) + `emailRemitente` (`@Email @Size(max=150)`, opcional) vía `@ModelAttribute` | `ConfiguracionEmpresaResponseDTO` |
   | GET | `/api/configuracion/empresa/logo` | Ninguno (cualquier autenticado) | — | `ResponseEntity<Resource>` (imagen) |

   `logoUrl` en `ConfiguracionEmpresaResponseDTO` se construye como `Wuris.Configuracion.EMPRESA_LOGO` (ruta fija) si `logoRuta != null`, o `null` si no hay logo configurado — el front nunca necesita conocer la ruta física, solo si hay o no logo y desde dónde pedirlo.
   - **Añadido (`V41`) — nota de revisión de código**: `emailRemitente` nació sin `@Size(max=150)`, solo con `@Email` — un email sintácticamente válido pero más largo que la columna (`VARCHAR(150)`) habría fallado con una excepción de BBDD (500) en vez de un 400 claro. Se corrigió añadiendo el mismo `@Size(max=150)` que ya tenía `nombreEmpresa` al lado.
5. **[Backend, baja]** `GET /api/configuracion/empresa/logo` sin logo configurado (`configuracionEmpresa.getLogoRuta() == null`) → `LogoEmpresaNoConfiguradoException` (404) — igual que pedir el archivo de una factura que no existiera.

### Transversal
6. **[Backend, baja]** Nuevas excepciones en `GlobalExceptionHandler`: `LogoEmpresaFormatoNoValidoException` (400, junto a `ArchivoFacturaNoValidoException`), `LogoEmpresaNoConfiguradoException` (404, junto a `FacturaNotFoundException`).
7. **[Backend, media]** Tests unitarios de `ConfiguracionEmpresaService.actualizar`: cambiar solo el nombre (sin `logo`) no toca `logoRuta`; subir un logo nuevo cuando ya había uno llama a `fileStorageService.eliminar` con la ruta anterior (mock, verificado con `verify(...)`) después de guardar el nuevo; un `contentType` no permitido (p. ej. `application/pdf`) lanza `LogoEmpresaFormatoNoValidoException` sin llamar a `fileStorageService.guardar`. **Añadidos (`V41`)**: `emailRemitente` con un valor se guarda tal cual; `emailRemitente` en blanco (`"   "`) se guarda como `null`.
8. **[Backend, baja]** Tests `@WebMvcTest`: `GET /api/configuracion/empresa` y `.../logo` devuelven 200 con un token `EMPLEADO` (sin `EMPLEADOS:EDICION`); `PUT /api/configuracion/empresa` con ese mismo token devuelve 403. **Añadido (`V41`)**: `PUT` con `emailRemitente` de formato inválido devuelve 400.

## Decisiones técnicas

- **`GET` abierto a cualquier autenticado, `PUT` restringido a `EMPLEADOS:EDICION`, dentro del mismo controller**: es la primera vez que `ConfiguracionController` mezcla dos niveles de autorización distintos entre sus propios endpoints — justificado porque, a diferencia de ubicación/vacaciones, este dato tiene un consumidor de lectura genuinamente distinto (la cabecera de la app, para todos) del que edita (el Admin).
- **Reutilizar `ConfiguracionController`/`ConfiguracionEmpresaService` como piezas nuevas dentro de la estructura existente, no un `EmpresaController` aparte**: sigue exactamente el patrón ya establecido por ubicación/vacaciones — una "sección" más del mismo panel de Configuración, no un módulo con entidad propia de primer nivel.
- **Borrar el logo anterior solo después de guardar el nuevo con éxito**: si `fileStorageService.guardar` fallara (disco lleno, permisos), la empresa se queda con el logo anterior en vez de sin ninguno — mismo criterio de "no destruir hasta confirmar el reemplazo" ya aplicado en otras partes del proyecto con archivos.
- **Sin *caché* ni cabecera `ETag`/`Cache-Control` especial en `GET /logo`**: el logo cambia con muy poca frecuencia y el volumen de peticiones de una app interna de un restaurante no lo justifica; si se detectara un problema real de rendimiento, es una optimización aparte, no construida aquí sin necesidad demostrada.

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase5-1-bbdd.md`: sin la tabla, no hay dónde persistir nada.
- **Dependencia dura** con `FileStorageService` (`back/fase3-1-back.md`): reutiliza su interfaz tal cual, sin ninguna extensión.
- **Riesgo bajo**: al abrir la lectura a cualquier autenticado, un Empleado sin ningún permiso concreto asignado puede ver el nombre/logo de la empresa — aceptable y esperado (es información pública dentro de la propia empresa, no un dato sensible).

## Criterios de aceptación técnicos

- `GET /api/configuracion/empresa` con un token `EMPLEADO` devuelve 200 con el nombre de la empresa y `logoUrl` (o `null` si no hay logo).
- `PUT /api/configuracion/empresa` con un token `EMPLEADO` (sin `EMPLEADOS:EDICION`) devuelve 403.
- Subir un logo válido (`image/png`) actualiza `logoUrl` en la siguiente lectura; subir un fichero con `contentType` no permitido devuelve 400 sin modificar la configuración existente.
- Actualizar el nombre sin adjuntar `logo` conserva el logo ya configurado.
- Reemplazar un logo existente por uno nuevo borra el fichero físico anterior del almacenamiento.
- `GET /api/configuracion/empresa/logo` sin ningún logo configurado devuelve 404.
