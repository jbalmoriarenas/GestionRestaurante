# Plan de Implementación — Fase 3.3 (Frontend): Nóminas — envío por email con confirmación

Consume el contrato de [`../back/fase3-3-back.md`](../back/fase3-3-back.md). Extiende `NominaService`/`NominasAdminComponent` de [`fase3-1-front.md`](./fase3-1-front.md), reutilizando el mismo patrón de modal de confirmación ya usado en `mis-vacaciones` (cancelar solicitud, `fase1-2-front.md`) y `empleados-list` (dar de baja, `fase0-front.md`).

## Contexto y objetivo

Botón "Enviar" (o "Reenviar", si la nómina ya se envió antes) en cada fila con nómina adjuntada, que abre un modal de verificación mostrando a quién y a qué email se va a enviar antes de confirmar; al confirmar, se envía por email y la fila pasa a "Enviada" sin recargar la página.

## Desglose de subtareas

1. **[Frontend, baja]** Extender `NominaService`: `enviar(nominaId: number): Observable<NominaListado>`.
2. **[Frontend, media]** Botón de envío en la columna Acciones, visible solo si `estado !== 'PENDIENTE'` (una nómina sin archivo no se puede enviar): texto "Enviar" si `estado === 'ADJUNTADA'`, texto "Reenviar" si `estado === 'ENVIADA'` (con la fecha del envío anterior visible, p. ej. en un `title`/tooltip o una columna adicional "Enviada el...", para que el Admin sepa que ya se envió antes de decidir reenviar).
3. **[Frontend, media]** `ui-modal` de confirmación: muestra el nombre del empleado, su email de destino, el periodo (mes/año) y un texto de aviso ("Se enviará la nómina de {periodo} a {email}. ¿Confirmar?"), con botones Cancelar/Confirmar. Se abre guardando en un `signal` la fila sobre la que se pulsó "Enviar", igual patrón que el modal de rechazo de `vacaciones-admin` (`fase1-4-front.md`).
4. **[Frontend, media]** Al confirmar: `signal guardandoEnvio` de loading en el botón de confirmar del modal → `NominaService.enviar(nominaId)`; en éxito, notificación de éxito, cierra el modal y actualiza la fila afectada a `estado = 'ENVIADA'` sin recargar toda la tabla; en error (502 por fallo de SMTP, ya mostrado por el `errorInterceptor` global como toast), el modal se mantiene abierto y `guardandoEnvio` se apaga, para que el Admin pueda reintentar sin tener que volver a abrirlo ni perder el contexto.
5. **[Frontend, baja]** Nuevas claves i18n bajo `nominas.admin`: `acciones.{enviar,reenviar}`, `envio.{titulo,aviso,confirmar,enviadaEl}`, `notificaciones.envioExito`.

## Decisiones técnicas

- **Reutiliza el patrón de modal de confirmación ya validado en el proyecto** (`mis-vacaciones`, `empleados-list`, `vacaciones-admin` para rechazar) en vez de inventar uno nuevo: abrir con el contexto de la fila → confirmar → cerrar y refrescar.
- **El modal permanece abierto tras un error de envío**, en vez de cerrarse y obligar a reabrirlo: el Admin puede reintentar de inmediato (p. ej. si el fallo fue transitorio de red), sin perder de vista a quién iba dirigido el envío.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase3-3-back.md`: sin su endpoint, este botón no tiene nada que llamar.
- **Riesgo bajo**: si el backend tarda en responder (SMTP lento, sin `@Async` según lo documentado en `back/fase3-3-back.md`), el botón de confirmar queda en estado de carga más tiempo del habitual en el resto de acciones de la app — se acepta el mismo trade-off ya documentado en el backend, sin ninguna mitigación adicional en el front para esta fase.

## Criterios de aceptación técnicos

- El botón de envío solo aparece en filas con estado "Adjuntada" o "Enviada", nunca en "Pendiente".
- El modal de confirmación muestra el email real al que se va a enviar antes de que el Admin confirme nada.
- Confirmar el envío deja la fila en "Enviada" sin recargar la página, y el botón pasa a decir "Reenviar".
- Si el envío falla, el modal permanece abierto con un mensaje de error visible y la fila no cambia de estado.
