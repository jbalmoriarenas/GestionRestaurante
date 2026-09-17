# Plan de Implementación — Fase 0 (Backend)

Depende del esquema definido en [`../bbdd/fase0-bbdd.md`](../bbdd/fase0-bbdd.md) y expone el contrato que consume [`../front/fase0-front.md`](../front/fase0-front.md).

## Contexto y objetivo

Construir en `GestionRestauranteBack` la base transversal de la aplicación: autenticación JWT y un **sistema de permisos por tipo de usuario y vista** (`EDICION` / `VISUALIZACION` / sin acceso), en vez de un rol fijo hardcodeado — junto con el CRUD de empleados. Las bases de buenas prácticas (evitar N+1, DTOs explícitos, paginación, manejo de errores centralizado) sentadas aquí las siguen también los módulos de las fases siguientes.

## Stack

Spring Boot 4.1.1 / Spring Framework 7.0.9 / Java 21 (ya montado). Se añaden en esta fase:

- `spring-boot-starter-security`
- `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson` 0.13.0 (JJWT, emisión/validación de JWT)
- `org.springframework.boot:spring-boot-starter-flyway` (en Spring Boot 4 la autoconfiguración de Flyway se modularizó; `flyway-core` solo en el classpath ya no basta, hace falta el starter) + `org.flywaydb:flyway-mysql`
- `org.springframework.security:spring-security-test` (test)

## Análisis y supuestos

- El login se hace por `email` + `password`. La respuesta de login incluye el token **y ya el mapa de permisos** del tipo de usuario (`vista` → `nivelAcceso`), para que el frontend pueda montar el menú y los guards sin una llamada adicional.
- El JWT en sí solo lleva identidad (`sub`=email, `empleadoId`, `tipoUsuarioCodigo`, `nombre`), **no** los permisos — los permisos se resuelven en cada request contra la tabla `permisos` (tabla pequeña, con índice por `tipo_usuario_id`). Esto evita que un cambio de permisos hecho por un Admin quede "atascado" hasta que el token expire; el coste de la consulta extra es despreciable a este volumen de datos.
- No hay "recuperar contraseña" en Fase 0; el reseteo de contraseña de un empleado lo hace el Admin (`PATCH /api/empleados/{id}/password`).
- La restricción "un Empleado solo ve/edita **sus propios** fichajes" (cuando se implemente Fichajes en Fase 1) es lógica de negocio en el servicio, no algo que la tabla `permisos` module — `permisos` solo decide acceso a la vista en conjunto, no el alcance de los datos dentro de ella.

## Estructura de paquetes — organización por capas (MVC), no por feature

Se organiza **por tipo de artefacto** (Entity / Repository / Service / Controller / DTO...), no por módulo de negocio, para que cualquier archivo se localice sabiendo únicamente "qué es" (una entidad, un repositorio...), sin tener que recordar a qué feature pertenece:

```
com.gestionrestaurante.back
├── GestionRestauranteBackApplication.java
├── Wuris.java        ← única fuente de verdad de todas las rutas de la API (ver más abajo)
├── entity/           Empleado, TipoUsuario, Vista, Permiso, NivelAcceso (enum)
├── repository/       EmpleadoRepository, TipoUsuarioRepository, PermisoRepository
├── service/          EmpleadoService, AuthService, JwtService, PermisoService
├── controller/       AuthController, EmpleadoController, TipoUsuarioController
├── dto/              LoginRequestDTO/ResponseDTO, EmpleadoCreateDTO/UpdateDTO/ResponseDTO,
│                     PasswordResetDTO, PermisoDTO, TipoUsuarioDTO, ApiError
├── mapper/           EmpleadoMapper
├── security/         SecurityConfig, JwtAuthFilter, RestAuthenticationEntryPoint, RestAccessDeniedHandler
├── exception/        GlobalExceptionHandler y las excepciones de negocio
│                     (EmpleadoNotFoundException, EmailYaExisteException, DniYaExisteException,
│                     CredencialesInvalidasException, TipoUsuarioNotFoundException)
└── config/           AdminSeeder (arranque/bootstrap de datos)
```

Las fases siguientes (Fichajes, Facturas, Nóminas) añaden sus clases dentro de estas mismas carpetas (p.ej. `entity/Fichaje.java`, `service/FichajeService.java`), no una carpeta nueva por módulo.

### `Wuris` — todas las rutas centralizadas

Ningún controller ni la configuración de seguridad escriben una ruta HTTP "a mano" como literal repetido; todas las URLs de la API viven como constantes en `com.gestionrestaurante.back.Wuris`, agrupadas en clases anidadas por recurso (`Wuris.Auth.LOGIN`, `Wuris.Empleados.BASE`, `Wuris.Empleados.BY_ID`...). Los controllers anotan cada endpoint con `@GetMapping(Wuris.Empleados.BY_ID)` etc., y `SecurityConfig` usa `Wuris.Auth.PUBLIC_PATTERN` para el matcher de rutas públicas. Añadir un endpoint nuevo empieza siempre por añadir su constante aquí — es el único sitio donde buscar o cambiar una ruta de la API.

## Modelo de autorización — de rol fijo a permisos dinámicos

En vez de `@PreAuthorize("hasRole('ADMIN')")` fijo en código, las autoridades de Spring Security se calculan **dinámicamente por request** a partir de la tabla `permisos`:

1. `JwtAuthFilter` valida el token y obtiene `tipoUsuarioCodigo` del claim.
2. `PermisoService.obtenerAutoridades(tipoUsuarioCodigo)` consulta `permisos` (vía `PermisoRepository`, una única query con `JOIN FETCH` a `vista`) y construye authorities con el formato **`"<VISTA>:<NIVEL_ACCESO>"`**, p.ej. `FICHAJES:EDICION`, `FACTURAS:VISUALIZACION`.
3. Esas authorities se cargan en el `SecurityContext` igual que roles normales de Spring Security.
4. Los controllers protegen endpoints con `@PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")` para escritura, o `@PreAuthorize("hasAnyAuthority('EMPLEADOS:EDICION','EMPLEADOS:VISUALIZACION')")` para lectura (`EDICION` implica poder también leer).

Ventaja clave: cuando en una fase futura se construya la pantalla de gestión de permisos y se cree un tipo de usuario nuevo, **no hace falta tocar ni una línea de `@PreAuthorize`** — las authorities se siguen derivando de la misma tabla.

## Desglose de subtareas

### Catálogo de permisos
1. **[Backend, baja]** Entidades `TipoUsuario`, `Vista`, `Permiso` (+ enum `NivelAcceso { EDICION, VISUALIZACION }`), mapeadas 1:1 con `bbdd/fase0-bbdd.md`.
2. **[Backend, baja]** `TipoUsuarioRepository`, `VistaRepository`, `PermisoRepository` (con `findByTipoUsuario_Codigo(String)` con `JOIN FETCH permiso.vista` para evitar N+1 al listar permisos de un tipo de usuario).
3. **[Backend, media]** `PermisoService.obtenerAutoridades(String tipoUsuarioCodigo): List<GrantedAuthority>` y `PermisoService.obtenerPermisos(String tipoUsuarioCodigo): List<PermisoDTO>` (este segundo, usado en el login para devolver el mapa de permisos al front).

### Seguridad y autenticación
4. **[Backend, media]** `SecurityConfig`: `SecurityFilterChain` stateless, `PasswordEncoder` = `BCryptPasswordEncoder`, `/api/auth/**` público, resto autenticado, `@EnableMethodSecurity` para habilitar `@PreAuthorize`; `CorsConfigurationSource` permitiendo `http://localhost:4200`.
5. **[Backend, media]** `JwtService`: generar token (claims `sub`, `empleadoId`, `tipoUsuarioCodigo`, `nombre`, `exp`) y parsear/validar. Secreto y expiración en `application.properties` (`app.jwt.secret`, `app.jwt.expiration-ms`).
6. **[Backend, media]** `JwtAuthFilter` (`OncePerRequestFilter`): valida el token, resuelve authorities vía `PermisoService.obtenerAutoridades(...)` y puebla el `SecurityContext`.
7. **[Backend, baja]** `RestAuthenticationEntryPoint` (401) y `RestAccessDeniedHandler` (403): escriben el mismo formato `ApiError` que `GlobalExceptionHandler`, para que el frontend tenga un único shape de error en toda la app, venga el rechazo de la capa de filtros o de un controller.
8. **[Backend, baja]** `AuthController` → `POST /api/auth/login` (público): `LoginRequestDTO {email, password}` → `LoginResponseDTO {token, empleadoId, nombre, apellidos, tipoUsuario: {codigo, nombre}, permisos: [{vista, nivelAcceso}]}`. 401 con mensaje genérico si credenciales inválidas.
9. **[Backend, baja]** `TipoUsuarioController` → `GET /api/tipos-usuarios` (`@PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")`): lista simple `[{codigo, nombre}]` de `tipos_usuarios`, usado por el formulario de alta/edición de empleados del front para poblar el selector sin hardcodear las opciones.

### CRUD de empleados
10. **[Backend, baja]** Entidad `Empleado` con `@ManyToOne` a `TipoUsuario` (FK `tipo_usuario_id`).
11. **[Backend, baja]** `EmpleadoRepository`: `findByEmail` (con `JOIN FETCH tipoUsuario` para el login sin N+1), `existsByEmail`, `existsByDniNie`, `findByActivo(boolean, Pageable)`.
12. **[Backend, media]** `EmpleadoService`: alta (hashea password), edición, baja lógica (`activo=false` + `fechaBaja`, nunca `DELETE` físico), listado paginado.
13. **[Backend, media]** `EmpleadoController` (`/api/empleados`):
    - `GET /api/empleados?page=&size=&activo=` — `@PreAuthorize("hasAnyAuthority('EMPLEADOS:EDICION','EMPLEADOS:VISUALIZACION')")` → `Page<EmpleadoResponseDTO>`
    - `GET /api/empleados/{id}` — igual anotación
    - `POST /api/empleados`, `PUT /api/empleados/{id}`, `PATCH /api/empleados/{id}/baja`, `PATCH /api/empleados/{id}/password` — `@PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")`
14. **[Backend, baja]** `EmpleadoMapper` (entidad ↔ DTO); `EmpleadoResponseDTO` nunca incluye `passwordHash` e incluye `tipoUsuario: {codigo, nombre}` en vez de un `rol` plano.

### Transversal
15. **[Backend, baja]** `GlobalExceptionHandler` (`@RestControllerAdvice`) con `ApiError { timestamp, status, error, message, path }` para validación (400), conflictos de email/DNI (409), no encontrado (404), credenciales inválidas (401), acceso denegado (403), fallback (500).
16. **[Backend, baja]** `spring.jpa.open-in-view=false` — ver razones en "Decisiones técnicas".
17. **[Backend, baja]** `AdminSeeder` (`CommandLineRunner`): si `empleadoRepository.count() == 0`, crea el Admin inicial usando el `TipoUsuario` de código `ADMIN` (ya sembrado por la migración `V5`) y una contraseña hasheada en caliente con el `PasswordEncoder` inyectado — evita tener que precomputar un hash BCrypt a mano en SQL.
18. **[Backend, baja]** Tests: `@DataJpaTest` (constraints de `empleados` y `permisos`), `@WebMvcTest` de `EmpleadoController` (verifica 403 sin la authority adecuada), unit test de `JwtService` y de `PermisoService.obtenerAutoridades`.

## Decisiones técnicas (buenas prácticas transversales para todas las fases)

- **Autorización dinámica basada en datos, no en roles hardcodeados**: como se describe arriba, toda la lógica de `@PreAuthorize` usa `hasAuthority('<VISTA>:<NIVEL>')`, nunca `hasRole('ADMIN')`. Esto es lo que hace que el sistema sea extensible a nuevos tipos de usuario sin redeploy de lógica de autorización.
- **Nada de N+1 desde el diseño**:
  - Todo listado se pagina (`Pageable`/`Page<T>`), nunca `findAll()` sin límite.
  - Todo endpoint devuelve **DTOs**, nunca entidades JPA directamente.
  - `spring.jpa.open-in-view=false` para que cualquier acceso perezoso fuera de la capa de servicio falle rápido en desarrollo (`LazyInitializationException`) en vez de esconderse como N llamadas SQL en producción.
  - `PermisoRepository.findByTipoUsuario_Codigo` usa `JOIN FETCH` sobre `vista` — se resuelve en 1 query, no N.
  - `EmpleadoRepository.findByEmail` usa `JOIN FETCH tipoUsuario` — el login no dispara una segunda query para leer el tipo de usuario.
  - Regla documentada para las fases futuras con relaciones (`Fichaje.empleado`, `Factura.proveedor`, `Nomina.empleado`): `JOIN FETCH` o `@EntityGraph` en cualquier listado que necesite datos del lado "uno" de la relación.
- **Baja lógica, no borrado físico** de empleados (necesario porque Fichajes/Nóminas de fases futuras referencian al empleado por FK).
- **DTOs de entrada y salida separados** de la entidad, para no exponer campos sensibles ni acoplar el contrato HTTP al modelo de persistencia.
- **Convención de errores única** (`ApiError`), consistente entre `GlobalExceptionHandler` y los manejadores de seguridad (401/403).

## Riesgos y dependencias

- **Dependencia dura** con `bbdd/fase0-bbdd.md`: sin `V1`-`V5` aplicadas, no hay `tipos_usuarios`/`vistas`/`permisos` con los que construir ni el login ni las authorities.
- **Riesgo bajo — coste de consultar permisos por request**: al no cachear, cada request autenticado hace una query a `permisos`. Con el volumen esperado (pocos tipos de usuario, pocas vistas) es insignificante; si en el futuro se nota, se puede cachear `PermisoService.obtenerAutoridades` por `tipoUsuarioCodigo` con una invalidación sencilla al modificar permisos.
- **Riesgo bajo**: el secreto JWT en Fase 0 vive en `application.properties`; antes de cualquier despliegue real debe moverse a variable de entorno.

## Criterios de aceptación técnicos

- `POST /api/auth/login` con el Admin seed devuelve 200, un token válido y `permisos` con `EDICION` en las 4 vistas.
- Login con el mismo email pero tipo de usuario `EMPLEADO` (una vez exista, Fase 1) devuelve `permisos` con `EDICION` solo en `FICHAJES`.
- Un token cuyo tipo de usuario no tiene ninguna fila en `permisos` para `EMPLEADOS` recibe 403 en cualquier endpoint de `/api/empleados`.
- `GET /api/empleados` responde paginado y nunca incluye `passwordHash`.
- Crear dos empleados con el mismo email devuelve 409 con un `ApiError` legible, no un 500.
- Ningún endpoint de Fase 0 ejecuta más de 1 query SQL por relación necesaria (verificable con logs de Hibernate: login = 1 query a `empleados` + 1 a `permisos`, no más).
