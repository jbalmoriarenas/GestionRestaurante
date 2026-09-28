CREATE TABLE configuracion_empresa (
    id                       BIGINT        PRIMARY KEY,
    nombre_empresa           VARCHAR(150)  NOT NULL,
    logo_ruta                VARCHAR(255)  NULL,
    logo_nombre_original     VARCHAR(255)  NULL,
    updated_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    actualizado_por_admin_id BIGINT        NULL,

    CONSTRAINT fk_configuracion_empresa_admin FOREIGN KEY (actualizado_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT chk_configuracion_empresa_id_unico CHECK (id = 1)
);

-- Con INSERT inicial a proposito (mismo patron que configuracion_ubicacion, fase1-1-bbdd.md):
-- la fila siempre existe, con el nombre real del negocio ya sembrado; logo_ruta nace NULL
-- hasta que el Admin suba un logo desde el panel de Configuracion.
INSERT INTO configuracion_empresa (id, nombre_empresa) VALUES (1, 'Sidrería La Zapica');
