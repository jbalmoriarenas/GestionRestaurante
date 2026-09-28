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
2. **[Frontend, media]** `Login` (se amplía): nuevo signal `mostrarModalRecuperacion`, `enviandoRecuperacion`, `recuperacionEnviada` (booleano — controla si el modal muestra el formulario o el mensaje de confirmación tras enviar con éxito) y `errorRecuperacion` (mensaje de error devuelto por el backend — mismo patrón ya usado en el propio `Login.errorMessage()`, error gestionado a mano en el componente, no solo con el toast global del `errorInterceptor`). ~~ej. "no existe ningún empleado con ese email"~~ **Ya no aplica** (el backend ya no distingue si el email existe, ver más abajo) — sigue siendo útil para otros errores del backend: cooldown (429, "ya se ha solicitado un enlace recientemente") o fallo de envío (502).
3. **[Frontend, baja]** Enlace `<button type="button">` bajo el formulario de login (`'login.olvideContrasena' | translate`) que abre el modal (`mostrarModalRecuperacion.set(true)`), reiniciando `recuperacionEnviada`/`errorRecuperacion` a su estado inicial.
4. **[Frontend, media]** Modal (`ui-modal`, patrón de `empleados-list.html`): formulario con un único `ui-input` (`email`, `Validators.required`/`Validators.email`) y un botón "Enviar" (`[loading]="enviandoRecuperacion()"`). Al enviar, llama a `authService.solicitarRecuperacion(email)`; en éxito, `recuperacionEnviada.set(true)` (el modal cambia a mostrar un mensaje de confirmación, con un botón "Cerrar"); en error, `errorRecuperacion.set(...)` con el mensaje del backend (`error.error?.message`), dejando el formulario abierto para reintentar con otro email.

### Vista nueva de restablecimiento
5. **[Frontend, alta]** `RestablecerPassword` (componente standalone nuevo): lee `token` de `ActivatedRoute.snapshot.queryParamMap.get('token')` al iniciar; si no hay `token` en la URL, muestra directamente un mensaje de enlace inválido (sin llamar al backend). Formulario con `password` (`Validators.required`, ~~`Validators.minLength(6)` — mismo mínimo ya validado en el backend~~ **reforzado en revisión de código posterior**: `Validators.pattern(...)` — mínimo 8 caracteres con mayúscula, minúscula y dígito, mismo patrón que ya exige `EmpleadoForm` al crear un empleado y que el backend reforzó a la vez, ver `back/fase6-1-back.md` — y `confirmarPassword`, con un validador a nivel de grupo (`passwordsCoincidenValidator`, nuevo — primera vez que el proyecto necesita comparar dos campos de contraseña, no hay uno existente que reutilizar) que marca error si no coinciden. Botón "Restablecer contraseña"; en éxito, notifica (`NotificationService.success`) y navega a `/login`; en error, muestra el mensaje del backend (token inválido/caducado) y un enlace de vuelta a `/login` para pedir uno nuevo.
6. **[Frontend, baja]** Actualizar `app.routes.ts`: añadir `{ path: 'restablecer-contrasena', loadComponent: () => import(...).then((m) => m.RestablecerPassword) }` **fuera** del bloque `Shell`, al mismo nivel que `path: 'login'`.
7. **[Frontend, baja]** Nuevas claves i18n (ES/EN) bajo `login.olvideContrasena`, `login.modalRecuperacion.{titulo,email,enviar,confirmacion,cerrar}` y un nuevo namespace `restablecerPassword.{titulo,password,confirmarPassword,passwordsNoCoinciden,restablecer,exito,enlaceInvalido,volverALogin,errorGenerico}`.
8. **[Frontend, media] Añadido en revisión de código posterior — no estaba en el alcance original**: icono de "mostrar/ocultar contraseña" (ojo) añadido directamente en el componente compartido `UiInput` (no en `Login`/`RestablecerPassword` por separado) — se activa solo cuando `type="password"`, con dos nuevos inputs `showPasswordLabel`/`hidePasswordLabel` (obligatorios de pasar traducidos por quien use un campo de contraseña) y dos iconos nuevos en `ui-icon` (`eye`, `eye-off`). Al vivir en el componente compartido, se propaga gratis a los tres campos de contraseña existentes en la app (login, las dos de restablecer-contraseña, y el de creación de empleado).
9. **[Frontend, baja] Añadido en revisión de código posterior**: atributos `autocomplete` en los campos de email/contraseña (`username`, `current-password`, `new-password`, `email` según el caso) — sin esto, el navegador puede autorrellenar el campo de contraseña del login con una contraseña antigua guardada, ya que el cambio de contraseña ocurre en un formulario distinto al de login y el navegador no sabe por sí solo que la contraseña guardada dejó de ser válida.

## Decisiones técnicas

- **El modal vive dentro de `Login`, no como componente aparte**: es un formulario de una sola línea (email) directamente ligado al login — separarlo en su propio componente standalone no aporta nada y complicaría la comunicación de estado (abrir/cerrar, mostrar confirmación) sin ninguna ventaja de reutilización (no se usa en ningún otro sitio).
- **El modal muestra un mensaje de confirmación en vez de cerrarse solo tras enviar**: si se cerrara automáticamente, el usuario perdería la confirmación de que el correo se envió — mismo criterio de UX ya aplicado en otras confirmaciones del proyecto (p. ej. notificaciones tras guardar en `ConfiguracionAdmin`).
- **Error del modal gestionado a mano (signal), no solo con el toast global**: el mensaje del backend (cooldown, fallo de envío) debe quedar visible y persistente dentro del propio modal, sin que el toast desaparezca antes de leerlo — mismo patrón ya usado en `Login.errorMessage()` para las credenciales inválidas. ~~El mensaje específico ("no existe ningún empleado con ese email")~~ ya no aplica: la corrección de enumeración de emails en el backend (`back/fase6-1-back.md`) hace que un email inexistente ya no dé ningún error, se comporta igual que uno válido.
- **`passwordsCoincidenValidator` como validador de grupo, no un `Validators` de campo único**: comparar dos campos requiere acceso a ambos a la vez, por lo que tiene que ser un validador del `FormGroup`, no de un control individual — patrón estándar de Angular, primera vez que se necesita en este proyecto.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase6-1-back.md`: sin sus endpoints, ni el modal ni la vista de restablecimiento tienen qué llamar.
- ~~Riesgo bajo: sin `login.spec.ts` existente hoy... se recomienda añadir al menos un spec nuevo~~ **Resuelto en revisión de código posterior**: se detectó en review que esta entrega se cerró sin ningún test para `Login` ni `RestablecerPassword` (mientras que `ConfiguracionAdmin`, una feature más secundaria del mismo periodo, sí los tenía) — se añadieron `login.spec.ts` (modal: abrir/cerrar con reset de estado, envío inválido, éxito sin cerrar el modal, error del backend mostrado sin cerrar el modal; más los casos básicos de `submit()`) y `restablecer-password.spec.ts` (sin token, con token, contraseñas que no coinciden, contraseña que no cumple el patrón, éxito con notificación+navegación, token inválido/caducado, `passwordErrorMessage()`).

## Criterios de aceptación técnicos

- El enlace "¿Olvidaste tu contraseña?" abre el modal.
- ~~Enviar un email inexistente muestra el mensaje de error específico del backend sin cerrar el modal~~ **Actualizado tras revisión de seguridad del backend**: el backend ya no distingue si el email existe (`back/fase6-1-back.md`, corrección de enumeración de emails) — enviar cualquier email, exista o no, muestra el mismo mensaje de confirmación ("Si el email existe, te hemos enviado un enlace...").
- Enviar un email válido muestra el mensaje de confirmación dentro del modal.
- Navegar a `/restablecer-contrasena` sin `token` en la URL muestra directamente el mensaje de enlace inválido, sin llamar al backend.
- Con un `token` válido en la URL, introducir dos contraseñas que no coinciden bloquea el envío con un mensaje de error visible, sin llamar al backend.
- Restablecer con éxito notifica y navega a `/login`; con un token inválido/caducado, muestra el mensaje del backend y un enlace para volver a pedir uno nuevo.
- `/restablecer-contrasena` es accesible sin haber iniciado sesión (no exige `authGuard`).
