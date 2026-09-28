# Plan de Implementación — Fase 6.1 (Frontend): Recuperación de contraseña ("olvidé mi contraseña")

Consume el contrato de [`../back/fase6-1-back.md`](../back/fase6-1-back.md). Amplía `features/auth/login/` (ya existente) y añade una vista pública nueva `features/auth/restablecer-password/`. Segunda entrega de la Fase 6.1 (ver "División en entregas" en el documento de backend) — depende del contrato de esa entrega, pero puede maquetarse en paralelo mockeando `AuthService`.

## Contexto y objetivo

En la pantalla de login, un enlace "¿Olvidaste tu contraseña?" abre un modal (`ui-modal`, mismo componente ya usado en el resto del proyecto) donde el usuario introduce su email; al enviarlo, el backend manda un correo con un enlace de recuperación. Ese enlace lleva a una vista nueva y pública (`/restablecer-contrasena?token=...`) donde el usuario introduce y confirma su contraseña nueva.

## Estructura

```
core/models/recuperacion-password.model.ts
core/services/auth.service.ts                              (se amplía, ya existe)
features/auth/login/login.ts / .html / .scss                (se amplía: modal nuevo)
features/auth/restablecer-password/restablecer-password.ts / .html / .scss   (nuevo)
```

Ruta nueva `/restablecer-contrasena`, pública, **fuera** de `Shell`/`authGuard` — mismo nivel que `/login` en `app.routes.ts` (`path: 'login'` ya está fuera del bloque `component: Shell, canActivate: [authGuard]`).

## Modelos

### `core/models/recuperacion-password.model.ts`
```typescript
export interface SolicitarRecuperacionRequest {
  email: string;
}

export interface RestablecerPasswordRequest {
  token: string;
  password: string;
}
```

## Desglose de subtareas

### Servicio
1. **[Frontend, baja]** `AuthService` (se amplía, no un servicio nuevo — ambos endpoints viven bajo `/api/auth/...`, igual que `login`): `solicitarRecuperacion(email: string): Observable<void>`; `restablecerPassword(token: string, password: string): Observable<void>`.

### Login — modal "olvidé mi contraseña"
2. **[Frontend, media]** `Login` (se amplía): nuevo signal `mostrarModalRecuperacion`, `enviandoRecuperacion`, `recuperacionEnviada` (booleano — controla si el modal muestra el formulario o el mensaje de confirmación tras enviar con éxito) y `errorRecuperacion` (mensaje de error específico devuelto por el backend, ej. "no existe ningún empleado con ese email") — mismo patrón ya usado en el propio `Login.errorMessage()` (error gestionado a mano en el componente, no solo con el toast global del `errorInterceptor`, que también seguirá disparándose igual que ya ocurre hoy en el login).
3. **[Frontend, baja]** Enlace `<button type="button">` bajo el formulario de login (`'login.olvideContrasena' | translate`) que abre el modal (`mostrarModalRecuperacion.set(true)`), reiniciando `recuperacionEnviada`/`errorRecuperacion` a su estado inicial.
4. **[Frontend, media]** Modal (`ui-modal`, patrón de `empleados-list.html`): formulario con un único `ui-input` (`email`, `Validators.required`/`Validators.email`) y un botón "Enviar" (`[loading]="enviandoRecuperacion()"`). Al enviar, llama a `authService.solicitarRecuperacion(email)`; en éxito, `recuperacionEnviada.set(true)` (el modal cambia a mostrar un mensaje de confirmación, con un botón "Cerrar"); en error, `errorRecuperacion.set(...)` con el mensaje del backend (`error.error?.message`), dejando el formulario abierto para reintentar con otro email.

### Vista nueva de restablecimiento
5. **[Frontend, alta]** `RestablecerPassword` (componente standalone nuevo): lee `token` de `ActivatedRoute.snapshot.queryParamMap.get('token')` al iniciar; si no hay `token` en la URL, muestra directamente un mensaje de enlace inválido (sin llamar al backend). Formulario con `password` (`Validators.required`, `Validators.minLength(6)` — mismo mínimo ya validado en el backend) y `confirmarPassword`, con un validador a nivel de grupo (`passwordsCoincidenValidator`, nuevo — primera vez que el proyecto necesita comparar dos campos de contraseña, no hay uno existente que reutilizar) que marca error si no coinciden. Botón "Restablecer contraseña"; en éxito, notifica (`NotificationService.success`) y navega a `/login`; en error, muestra el mensaje del backend (token inválido/caducado) y un enlace de vuelta a `/login` para pedir uno nuevo.
6. **[Frontend, baja]** Actualizar `app.routes.ts`: añadir `{ path: 'restablecer-contrasena', loadComponent: () => import(...).then((m) => m.RestablecerPassword) }` **fuera** del bloque `Shell`, al mismo nivel que `path: 'login'`.
7. **[Frontend, baja]** Nuevas claves i18n (ES/EN) bajo `login.olvideContrasena`, `login.modalRecuperacion.{titulo,email,enviar,confirmacion,cerrar}` y un nuevo namespace `restablecerPassword.{titulo,password,confirmarPassword,passwordsNoCoinciden,restablecer,exito,enlaceInvalido,volverALogin}`.

## Decisiones técnicas

- **El modal vive dentro de `Login`, no como componente aparte**: es un formulario de una sola línea (email) directamente ligado al login — separarlo en su propio componente standalone no aporta nada y complicaría la comunicación de estado (abrir/cerrar, mostrar confirmación) sin ninguna ventaja de reutilización (no se usa en ningún otro sitio).
- **El modal muestra un mensaje de confirmación en vez de cerrarse solo tras enviar**: si se cerrara automáticamente, el usuario perdería la confirmación de que el correo se envió — mismo criterio de UX ya aplicado en otras confirmaciones del proyecto (p. ej. notificaciones tras guardar en `ConfiguracionAdmin`).
- **Error del modal gestionado a mano (signal), no solo con el toast global**: el mensaje específico ("no existe ningún empleado con ese email") debe quedar visible y persistente dentro del propio modal para que el usuario pueda corregir el email sin que el toast desaparezca antes de leerlo — mismo patrón ya usado en `Login.errorMessage()` para las credenciales inválidas.
- **`passwordsCoincidenValidator` como validador de grupo, no un `Validators` de campo único**: comparar dos campos requiere acceso a ambos a la vez, por lo que tiene que ser un validador del `FormGroup`, no de un control individual — patrón estándar de Angular, primera vez que se necesita en este proyecto.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase6-1-back.md`: sin sus endpoints, ni el modal ni la vista de restablecimiento tienen qué llamar.
- **Riesgo bajo**: sin `login.spec.ts` existente hoy (no hay ningún test de `Login` en el repo actual), esta fase no tiene un precedente de test que seguir para ese componente — se recomienda añadir al menos un spec nuevo para `Login` (cubriendo también el modal) y otro para `RestablecerPassword`, en vez de dejarlos sin cobertura como ya ocurre hoy con el propio login.

## Criterios de aceptación técnicos

- El enlace "¿Olvidaste tu contraseña?" abre el modal; enviar un email inexistente muestra el mensaje de error específico del backend sin cerrar el modal.
- Enviar un email válido muestra el mensaje de confirmación dentro del modal.
- Navegar a `/restablecer-contrasena` sin `token` en la URL muestra directamente el mensaje de enlace inválido, sin llamar al backend.
- Con un `token` válido en la URL, introducir dos contraseñas que no coinciden bloquea el envío con un mensaje de error visible, sin llamar al backend.
- Restablecer con éxito notifica y navega a `/login`; con un token inválido/caducado, muestra el mensaje del backend y un enlace para volver a pedir uno nuevo.
- `/restablecer-contrasena` es accesible sin haber iniciado sesión (no exige `authGuard`).
