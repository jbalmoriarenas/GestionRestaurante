# Viabilidad de integración con HioPOS

Sep 25, 2026 · @Jorge

## Resumen ejecutivo

**Veredicto: viable, pero no autoservicio.** No existe una API pública, documentada y de acceso libre para consultar ventas de cualquier instalación HioPOS — el único camino real y soportado es solicitar acceso técnico directamente a ICG Software (fabricante de HioPOS) a través de su canal de partners, o contratar a un integrador ya certificado que tenga esa relación. No hay ningún atajo gratuito ni de autoservicio fiable.

Antes de comprometer desarrollo de la vista de Métricas, el primer paso no es técnico: es abrir una conversación comercial/técnica con ICG Software para confirmar qué mecanismo de acceso ofrecen y en qué condiciones (ver «Próximos pasos»).

## Qué es HioPOS y cómo funciona

HioPOS es una de las tres divisiones de negocio de **ICG Software**, grupo catalán fundado en 1985 (sede y I+D en Torrefarrera, Lleida), fabricante histórico de TPV táctil para hostelería y retail — no está relacionado con Lightspeed Commerce (son fabricantes distintos, sin evidencia de que uno haya adquirido al otro) [ICG Group](https://www.icg.es/en/business/icg-group/).

Arquitectura, según la propia documentación de HioPOS: cada terminal (TPV Android, local en el restaurante) sincroniza sus datos con **HioPOS Cloud**, y **HiOffice** actúa como backoffice centralizado que "centraliza y gestiona todos los datos de tu negocio" y "visualiza la información en tiempo real" de ventas y stock de todos los terminales/establecimientos conectados [cide.cat/hiopos](https://cide.cat/hiopos/).

Esto encaja con lo que necesitamos: los datos de ventas de todas las cajas YA se centralizan en un punto único en la nube de ICG antes de llegar a nuestra app — el reto no es técnico-arquitectónico, es de acceso (qué puerta usa un tercero para leer esos datos).

## Opciones reales de acceso a los datos

| Vía | Qué es realmente | Coste | Dificultad |
| --- | --- | --- | --- |
| Exportaciones manuales desde HiOffice | Descargar informes (ventas, cierres, stock) del backoffice web como fichero, de forma periódica | Bajo/posiblemente 0€ | Baja — pero manual, no una integración en tiempo real |
| Acceso técnico oficial vía partner ICG | Pedir a ICG Software una API/webhook/exportación programática para nuestra app, a través de su canal de partners | A negociar con ICG | Media — depende de su respuesta |
| Integrador ya certificado (Bivium, Ordatic, GetOrder...) | Empresas que YA tienen acceso técnico a HioPOS (para pedidos de Glovo/Uber Eats, PMS de hoteles, etc.) y podrían construir el puente hacia nuestro backend | Cuota/proyecto con el integrador | Media — depende de si aceptan un caso de uso nuevo (lectura de ventas) |
| Acceso directo a la base de datos del TPV | Descartado | — | Alta — no recomendable |

**Sobre una API pública gratuita**: no hemos encontrado documentación oficial de ICG/HioPOS que describa endpoints, autenticación o especificación de una API REST abierta para terceros. La única página que aparenta ser «documentación de API de HioPOS» (apitracker.io) es un directorio genérico autogenerado sin ningún endpoint real — **no la tomes como evidencia de que existe una API pública** [API Tracker — HIOPOS APIs](https://apitracker.io/a/hiopos/apis). Lo que sí existe, confirmado, es un ecosistema de integraciones (pedidos de Glovo/Uber Eats/Deliveroo, PMS hotelero FrontHotel, pasarelas de pago, fidelización) construidas por partners con acceso privado a ICG, no expuestas públicamente [bivium.es/integraciones-hiopos](https://bivium.es/integraciones-hiopos/).

**Por qué descartar el acceso directo a la base de datos**: HioPOS Cloud corre en servidores propios de ICG (MariaDB); los TPV solo se conectan a esos servidores por internet, no exponen una base de datos local accesible. Además, los datos de facturación de un TPV en España están sujetos al Reglamento VERI\*FACTU (Real Decreto 1007/2023), que exige que el sistema de facturación mantenga un registro electrónico inalterable y trazable [BOE-A-2023-24840](https://www.boe.es/buscar/act.php?id=BOE-A-2023-24840) — tocar esos datos por una vía no oficial sería tanto inviable como legalmente arriesgado.

## Opción recomendada

En paralelo, no en secuencia:

1. **Contactar a ICG Software por el canal oficial de partners** ([hiopos.com/en/partners](https://www.hiopos.com/en/partners)) pidiendo específicamente acceso técnico de solo lectura a datos de ventas/cierres/productos para una integración interna (no una integración comercial de terceros) — es la única vía que puede dar una solución soportada, en tiempo real y mantenible a largo plazo.
2. **Mientras se espera respuesta, probar la vía de exportación manual desde HiOffice** como prueba de concepto: descargar un informe de ventas de un periodo y comprobar qué columnas/formato trae realmente. Esto no depende de que ICG conteste y ya deja validar si el formato de esos datos sirve para alimentar la futura vista de Métricas, aunque sea con una carga manual/periódica al principio.

No se recomienda invertir tiempo de desarrollo en construir nada contra HioPOS hasta que el punto 1 confirme qué mecanismo real ofrecen (API, webhook, export programático) y en qué condiciones.

## Limitaciones, riesgos y dependencias

- **Dependencia comercial**: el acceso técnico depende de que ICG Software acepte la solicitud; no hay SLA ni plazo de respuesta público, y puede implicar coste (licencia, cuota de partner, o desarrollo a medida por su parte).
- **Alcance del scope**: ICG puede ofrecer solo un subconjunto de los datos deseados (p.ej. ventas agregadas por cierre, pero no el detalle línea a línea de cada ticket) — hay que confirmarlo antes de diseñar la vista de Métricas sobre un supuesto no verificado.
- **Formato desconocido a priori**: no sabemos si la vía oficial será una API REST, un webhook, un fichero periódico (CSV/Excel) o un export programático — el diseño técnico de la futura vista de Métricas no debería cerrarse hasta confirmarlo.
- **Cumplimiento normativo (VERI\*FACTU)**: aunque no accedamos a datos de facturación fiscal en sí, cualquier integración con un sistema sujeto a VERI\*FACTU (obligatorio por fases entre 2026 y 2027 según facturación) debe pasar por sus canales oficiales, nunca por accesos no documentados.
- **Alternativa de integrador de terceros**: si ICG no da acceso directo o tarda demasiado, un integrador ya certificado (Bivium, Ordatic, GetOrder) es una vía de respaldo, pero añade un intermediario, coste recurrente, y depende de que acepten construir un caso de uso nuevo (lectura de métricas) en vez de su foco habitual (pedidos online).

## Próximos pasos concretos

1. Contactar a ICG Software (canal de partners) pidiendo, explícitamente, acceso de solo lectura a estos datos por establecimiento — llevar esta lista a la conversación para que la respuesta sea concreta, no genérica:
   - **Ventas**: ticket, fecha/hora, productos vendidos, cantidad, precio, descuentos, IVA, total, forma de pago, empleado, mesa
   - **Productos**: id, nombre, categoría, precio, coste, stock
   - **Cajas**: apertura, cierre, efectivo, tarjeta, otros pagos, diferencias
   - **Clientes**: id, nombre, email, historial de compras (si aplica y con base legal de protección de datos)
2. Preguntar explícitamente: mecanismo (API/webhook/export), formato, frecuencia de actualización, coste, y si hay entorno de pruebas/sandbox.
3. En paralelo, entrar a HiOffice con la cuenta actual y comprobar qué informes se pueden exportar hoy mismo sin esperar respuesta de ICG — sirve de validación rápida de qué datos hay disponibles como mínimo.
4. Con la respuesta de ICG y la prueba de exportación manual, decidir el diseño técnico de la vista de Métricas (fuera del alcance de este documento).
