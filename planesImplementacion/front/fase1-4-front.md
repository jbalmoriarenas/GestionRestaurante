# Plan de Implementación — Fase 1.4 (Frontend): Vacaciones (vista Admin)

Consume el contrato de [`../back/fase1-4-back.md`](../back/fase1-4-back.md). Reutiliza `EmpleadoService`/`ui-select`/`ui-table`/`ui-modal` de [`fase0-front.md`](./fase0-front.md) y `VacacionesService`/`ui-calendar` de [`fase1-2-front.md`](./fase1-2-front.md).

## Contexto y objetivo

Vista Admin de vacaciones con tres capacidades (confirmadas con el usuario): (a) una **bandeja global** con todas las solicitudes pendientes de todos los empleados, para no tener que navegar uno a uno; (b) selección de un empleado concreto para ver su calendario completo y su historial de solicitudes, con aprobar/rechazar; (c) **asignar días de vacaciones de oficio** a un empleado, sin pasar por una solicitud suya.

## Estructura (se añade a `features/vacaciones/`)

```
features/vacaciones/vacaciones-admin/vacaciones-admin.ts / .html / .scss
```

Ruta `/vacaciones/admin` protegida con `permissionGuard('EMPLEADOS', 'EDICION')` (alineado con la authority que exige `back/fase1-4-back.md`).

## Desglose de subtareas

### Bandeja global de pendientes (confirmado con el usuario)
1. **[Frontend, media]** Extender `VacacionesService` (de `fase1-2-front.md`) con `listarTodasPendientes(page, size)`.
2. **[Frontend, media]** Sección superior de `VacacionesAdminComponent`: `ui-table` con `listarTodasPendientes()` — columnas Empleado / Fechas / Días / Acciones (Aprobar / Rechazar), visible siempre al entrar en la vista, **antes** de seleccionar ningún empleado. Al aprobar/rechazar una fila desde aquí, se refresca esta tabla y, si el empleado afectado coincide con el seleccionado en la sección de detalle (más abajo), también su calendario/listado.

### Detalle por empleado (ya previsto)
3. **[Frontend, baja]** Extender `VacacionesService` con `obtenerCalendarioComoAdmin(empleadoId, anio)`, `listarSolicitudesComoAdmin(empleadoId, estado, page, size)`, `aprobar(id)`, `rechazar(id, motivo)`.
4. **[Frontend, media]** Sección de detalle: `ui-select` de empleado (reutiliza `EmpleadoService.listar()`) + `ui-calendar` (mismo componente de `fase1-2-front.md`, sin cambios) + `ui-table` de su historial de solicitudes (filtro rápido por estado), con "Aprobar"/"Rechazar" visibles solo si `estado === 'PENDIENTE'`.
5. **[Frontend, baja]** "Rechazar" abre un `ui-modal` con un `ui-input` para el motivo (obligatorio en el front, coherente con el backend) antes de confirmar — reutilizado tanto desde la bandeja global como desde el detalle por empleado (un único modal, dos puntos de entrada).

### Asignación de oficio (confirmado con el usuario)
6. **[Frontend, media]** Extender `VacacionesService` con `asignarDeOficio(empleadoId, fechaInicio, fechaFin)`.
7. **[Frontend, baja]** En la sección de detalle por empleado (ya con un empleado seleccionado), botón "Asignar días de vacaciones" → `ui-modal` con dos `ui-input type="date"` (fechaInicio/fechaFin) + texto de aviso ("Estos días se aprobarán directamente y restarán del cupo del empleado") → `asignarDeOficio(...)`. Si el backend responde 409 (cupo insuficiente), se muestra el error sin cerrar el modal, para que el Admin pueda ajustar el rango.
8. **[Frontend, baja]** Tras asignar con éxito, refrescar el calendario y el listado de solicitudes de ese empleado (la fila nueva aparece ya en `APROBADA`, sin botones de acción).

## Decisiones técnicas

- **Cero lógica de cálculo de días en el front**: toda la aritmética de días laborables/disponibles/cupo vive únicamente en `back/fase1-2-back.md`/`fase1-4-back.md`.
- **Un único `ui-modal` de "rechazar" y uno de "asignar de oficio"**, reutilizados independientemente de si la acción se originó en la bandeja global o en el detalle por empleado — evita duplicar el formulario del modal en dos sitios.
- **La bandeja global y el detalle por empleado son secciones de la misma vista**, no dos rutas: comparten el mismo componente y el mismo servicio, y una acción en una puede refrescar la otra si aplica al mismo empleado.
- **Reutilización total de `ui-calendar`**: ninguna adaptación visual específica para el Admin.

## Riesgos y dependencias

- **Dependencia dura** con `back/fase1-4-back.md`: sin sus 7 endpoints, esta vista no puede probarse end-to-end.
- **Dependencia** con `front/fase1-2-front.md`: cualquier cambio en `ui-calendar` o en `DiaCalendario`/`SolicitudVacaciones` debe revisarse aquí también, al ser el mismo componente/modelos reutilizados.
- **Riesgo bajo**: si el volumen de solicitudes pendientes de toda la empresa crece mucho, la bandeja global ya está paginada desde el diseño del backend, por lo que no debería degradarse.

## Criterios de aceptación técnicos

- Al entrar en `/vacaciones/admin` sin haber seleccionado ningún empleado, ya se ve la bandeja global con las solicitudes pendientes de todos, con el nombre de cada empleado visible en la fila.
- Aprobar una solicitud desde la bandeja global la hace desaparecer de esa bandeja y, si ese empleado está seleccionado en el detalle, también actualiza su calendario.
- Seleccionar un empleado en el detalle carga su calendario y su historial de solicitudes sin necesidad de recargar la página.
- Asignar días de oficio a un empleado con cupo suficiente los deja visibles de inmediato como "aprobados" en su calendario, sin que el empleado tenga que hacer nada.
- Asignar días de oficio a un empleado sin cupo suficiente muestra el error del backend sin romper el formulario.
- Rechazar sin motivo no permite confirmar el modal, tanto desde la bandeja global como desde el detalle por empleado.
- Un usuario con rol `EMPLEADO` no ve el enlace a `/vacaciones/admin` en el menú y, si navega ahí directamente, es bloqueado por el guard.
