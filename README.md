# GestionRestaurante

Aplicación de gestión interna para un restaurante/sidrería: fichajes de empleados, facturas de proveedores (con reconocimiento automático de datos vía OCR), nóminas, vacaciones y valoraciones de Google del negocio — todo en un único panel web con control de acceso por rol.

## Funcionalidades

- **Empleados**: alta/gestión de la plantilla, tipos de usuario (`ADMIN`/`EMPLEADO`) y permisos por vista.
- **Fichajes**: entrada/salida de cada empleado, geolocalizados dentro de un radio configurable alrededor del local; vista de administración con corrección manual y cálculo de horas.
- **Facturas**: subida de facturas de proveedores en PDF/imagen, con extracción automática de fecha, base/IVA/total mediante OCR (Tesseract) — el dato propuesto siempre se revisa y confirma antes de guardar, nunca se persiste ciego. Incluye exportación a Excel.
- **Nóminas**: subida individual o en lote (ZIP) de nóminas en PDF por empleado/mes, con envío por email opcional (activable solo cuando hay un SMTP real configurado).
- **Vacaciones**: solicitud y aprobación de vacaciones, calendario por empleado, configuración de días anuales (global y por empleado).
- **Valoraciones**: conexión OAuth con la cuenta de Google Business Profile del restaurante para sincronizar automáticamente sus reseñas de Google (media, distribución por estrellas, listado) — ver [guía de vinculación](planesImplementacion/guia-vinculacion-valoraciones-google.md).

Cada vista tiene su propio permiso (`EMPLEADOS`, `FICHAJES`, `FACTURAS`, `NOMINAS`, `VACACIONES`, `VALORACIONES`) asignable por rol, para que un `EMPLEADO` solo vea lo que le corresponde (típicamente su propio fichaje y sus vacaciones) frente a un `ADMIN`, que gestiona todo.

## Arquitectura

```
GestionRestaurante/
├── GestionRestauranteFront/   Angular 22 (SPA) — ver su README
├── GestionRestauranteBack/    Spring Boot 4 / Java 21 (API REST) — ver su README
└── planesImplementacion/      Documentación de diseño, planes por fase y guías operativas
```

Front y back son dos aplicaciones independientes desplegables por separado, comunicándose por HTTP (`/api/**`, JWT en `Authorization`). La base de datos es MySQL, con el esquema versionado por Flyway (las migraciones viven en el propio backend, se aplican solas al arrancar).

## Stack tecnológico (resumen)

| Capa | Tecnología |
|---|---|
| Frontend | Angular 22 (standalone, zoneless), TypeScript, SCSS (sistema de diseño propio), ngx-translate (ES/EN), Vitest |
| Backend | Spring Boot 4 / Spring Framework 7, Java 21, Spring Security + JWT, Spring Data JPA / Hibernate, Flyway |
| Base de datos | MySQL |
| Integraciones | Tesseract/Tess4J (OCR de facturas), Google Business Profile API (reseñas), SMTP (envío de nóminas) |

El detalle completo de versiones y librerías está en el README de cada subproyecto:
- [`GestionRestauranteFront/README.md`](GestionRestauranteFront/README.md)
- [`GestionRestauranteBack/README.md`](GestionRestauranteBack/README.md)

## Arrancar en local

1. **Base de datos**: crea la BBDD MySQL (`GestionRestauranteBack/scripts/create-database.sql`) — las tablas las crea Flyway solo al primer arranque del backend.
2. **Backend**: `cd GestionRestauranteBack && DB_USERNAME=... DB_PASSWORD=... ./mvnw spring-boot:run` (perfil `local` recomendado en desarrollo — ver su README para OCR y Valoraciones de Google).
3. **Frontend**: `cd GestionRestauranteFront && npm install && npm start` → `http://localhost:4200`.

Al primer arranque del backend con la tabla `empleados` vacía se crea un Admin inicial (`admin@gestionrestaurante.local`, contraseña por defecto `Admin.2026` salvo que se sobreescriba) — cámbiala desde la app en cuanto entres.

## Documentación adicional

Todo en [`planesImplementacion/`](planesImplementacion/):

- [`plan-implementacion-general.md`](planesImplementacion/plan-implementacion-general.md) — visión general del proyecto y decisiones de diseño transversales.
- `back/`, `front/`, `bbdd/` — planes de implementación detallados por fase y capa.
- [`guia-despliegue-aws.md`](planesImplementacion/guia-despliegue-aws.md) — cómo desplegar la aplicación completa en un servidor AWS con dominio propio y HTTPS.
- [`guia-vinculacion-valoraciones-google.md`](planesImplementacion/guia-vinculacion-valoraciones-google.md) — cómo conectar la cuenta de Google Business Profile del restaurante.
- [`guia-viabilidad-hiopos.md`](planesImplementacion/guia-viabilidad-hiopos.md) — análisis de viabilidad para integrar datos de ventas de las cajas HioPOS (de cara a una futura vista de Métricas).
