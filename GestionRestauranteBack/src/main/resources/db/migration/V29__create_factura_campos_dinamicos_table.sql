CREATE TABLE factura_campos_dinamicos (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    factura_id      BIGINT        NOT NULL,
    nombre_campo    VARCHAR(100)  NOT NULL,
    valor           VARCHAR(500)  NULL,
    fecha_creacion  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_campos_dinamicos_factura FOREIGN KEY (factura_id) REFERENCES facturas (id) ON DELETE CASCADE,
    CONSTRAINT uk_campos_dinamicos_factura_nombre UNIQUE (factura_id, nombre_campo)
);

CREATE INDEX idx_campos_dinamicos_factura ON factura_campos_dinamicos (factura_id);
