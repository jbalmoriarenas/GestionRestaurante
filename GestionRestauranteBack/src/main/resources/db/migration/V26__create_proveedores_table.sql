CREATE TABLE proveedores (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(150)  NOT NULL,
    cif             VARCHAR(15)   NULL,
    email_contacto  VARCHAR(150)  NULL,
    telefono        VARCHAR(20)   NULL,
    tipo            VARCHAR(20)   NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_proveedores_cif UNIQUE (cif),
    CONSTRAINT chk_proveedores_tipo CHECK (tipo IN ('PROVEEDOR', 'SUMINISTRO'))
);

CREATE INDEX idx_proveedores_tipo ON proveedores (tipo);
