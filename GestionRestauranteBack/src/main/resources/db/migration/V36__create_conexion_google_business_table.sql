CREATE TABLE conexion_google_business (
    id                            BIGINT        PRIMARY KEY,
    location_resource_name        VARCHAR(255)  NOT NULL,
    nombre_ubicacion               VARCHAR(255)  NULL,
    refresh_token_cifrado          VARCHAR(1000) NOT NULL,
    fecha_conexion                 TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    conectado_por_admin_id         BIGINT        NOT NULL,
    fecha_ultima_sincronizacion    TIMESTAMP     NULL,
    ultimo_error                   VARCHAR(500)  NULL,

    CONSTRAINT fk_conexion_google_business_admin FOREIGN KEY (conectado_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT chk_conexion_google_business_id_unico CHECK (id = 1)
);

-- Sin INSERT inicial a proposito: la ausencia de fila representa "Desconectado" (mismo patron
-- de "sin fila = PENDIENTE" que nominas, fase3-1-bbdd.md). La fila (id = 1) la inserta el
-- backend solo al completar el flujo OAuth (back/fase4-1-back.md).
