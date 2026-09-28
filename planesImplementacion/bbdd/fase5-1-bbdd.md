# Plan de Implementación — Fase 5.1 (Base de datos): Configuración — datos de empresa (nombre y logo)

Depende únicamente de [`fase0-bbdd.md`](./fase0-bbdd.md) (tabla `empleados`, catálogo de permisos — no se necesita ninguna vista/permiso nueva, ver "Análisis y decisiones técnicas"). Numerado a partir de **V45**, asumiendo que [`integracion_verifactu-bbdd.md`](./integracion_verifactu-bbdd.md) (V39-V41) y [`fichajes_vacaciones_2027-bbdd.md`](./fichajes_vacaciones_2027-bbdd.md) (V42-V44) se aplican antes — si se decide implementar esta fase antes que esas dos, renumerar en consecuencia. Fuente de verdad de nombres para [`../back/fase5-1-back.md`](../back/fase5-1-back.md).

## Contexto y objetivo

Una tabla nueva, `configuracion_empresa`, fila única (`id = 1`), con el nombre del negocio y la ruta del logo subido — el dato de "marca" que se muestra en la cabecera de la aplicación a todos los usuarios y en los documentos exportados (facturas, nóminas, informes de fichaje). No debe confundirse con `configuracion_ubicacion` (geovalla de fichaje) ni con `configuracion_emisor_fiscal` (datos fiscales para VeriFactu, `integracion_verifactu-bbdd.md`, si se llega a implementar) — son tres tablas de configuración con propósitos distintos, aunque las tres sigan el mismo patrón de fila única ya establecido en el proyecto (`configuracion_ubicacion`, `configuracion_vacaciones`).

## Análisis y decisiones técnicas

- **Sin vista/permiso nuevo en el catálogo de permisos**: a diferencia de `VALORACIONES`/`VERIFACTU` (que introdujeron una vista propia), esta configuración reutiliza `EMPLEADOS:EDICION` — mismo criterio ya aplicado y documentado en `ConfiguracionController.java` para `configuracion_ubicacion`/`configuracion_vacaciones` ("editar esto es una capacidad de Admin, no de un módulo concreto"). No hace falta ninguna migración de `vistas`/`permisos`.
- **Fila única (`id = 1`) con `INSERT` inicial, mismo patrón que `configuracion_ubicacion`**: la fila siempre existe, sembrada con el nombre real del negocio (`'Sidrería La Zapica'`, ya usado como referencia en `fase4-1-back.md`) y `logo_ruta` a `NULL` hasta que el Admin suba un logo — evita que la cabecera de la app tenga que manejar "no existe ninguna configuración de empresa" como caso aparte, solo "todavía no hay logo".
- **`logo_ruta` guarda una ruta relativa gestionada por `FileStorageService`** (`back/fase5-1-back.md`), igual que `facturas.ruta_archivo` — nunca la ruta absoluta del sistema de ficheros ni un *blob* en BBDD.
- **`logo_nombre_original` se guarda solo a título informativo** (para mostrarlo en el panel de Admin, "última imagen subida: `logo-restaurante.png`"), igual que `facturas.nombre_archivo_original` — no tiene ningún uso funcional más allá de eso.
- **Sin columna de color/tema corporativo**: no solicitado por el usuario ("nombre de empresa, logo... etc" se interpreta aquí de forma acotada); si se pide en el futuro, es una columna adicional sobre esta misma tabla, no una fase nueva.

## Desglose de subtareas

1. **[BBDD, baja]** `V45__create_configuracion_empresa_table.sql`.

## DDL

### `V45__create_configuracion_empresa_table.sql`

```sql
CREATE TABLE configuracion_empresa (
    id                       BIGINT        PRIMARY KEY,
    nombre_empresa            VARCHAR(150)  NOT NULL,
    logo_ruta                 VARCHAR(255)  NULL,
    logo_nombre_original       VARCHAR(255)  NULL,
    updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    actualizado_por_admin_id   BIGINT        NULL,

    CONSTRAINT fk_configuracion_empresa_admin FOREIGN KEY (actualizado_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT chk_configuracion_empresa_id_unico CHECK (id = 1)
);

-- Con INSERT inicial a proposito (mismo patron que configuracion_ubicacion, fase1-1-bbdd.md):
-- la fila siempre existe, con el nombre real del negocio ya sembrado; logo_ruta nace NULL
-- hasta que el Admin suba un logo desde el panel de Configuracion.
INSERT INTO configuracion_empresa (id, nombre_empresa) VALUES (1, 'Sidrería La Zapica');
```

**Mapeo de columnas ↔ campo de entidad JPA / DTO** (debe coincidir con `back/fase5-1-back.md`):

| Columna | Campo entidad | Tipo Java | Tipo TS (front) |
|---|---|---|---|
| `nombre_empresa` | `nombreEmpresa` | `String` | `string` |
| `logo_ruta` | `logoRuta` | `String` (nullable) | — (no se expone; el front recibe una URL calculada, `logoUrl`) |
| `logo_nombre_original` | `logoNombreOriginal` | `String` (nullable) | `string \| null` |
| `updated_at` | `updatedAt` | `LocalDateTime` | — (informativo, no imprescindible en el DTO) |

## Riesgos y dependencias

- **Dependencia dura** con `fase0-bbdd.md`: sin `empleados`, `actualizado_por_admin_id` no tiene a qué apuntar.
- **Riesgo bajo**: si se borra manualmente el fichero de logo del disco sin pasar por el backend, `logo_ruta` quedaría apuntando a un fichero inexistente — mismo riesgo ya aceptado hoy para `facturas.ruta_archivo`, no es una debilidad nueva introducida por esta fase.

## Criterios de aceptación técnicos

- Tras `V45`, existe exactamente una fila en `configuracion_empresa` (`id = 1`) con `nombre_empresa = 'Sidrería La Zapica'` y `logo_ruta = NULL`.
- Insertar una segunda fila falla por clave primaria; insertar con `id != 1` falla por `chk_configuracion_empresa_id_unico`.
