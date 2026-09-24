ALTER TABLE facturas
    ADD COLUMN estado_extraccion    VARCHAR(20) NOT NULL DEFAULT 'MANUAL' AFTER estado_pago,
    ADD COLUMN datos_extraidos_raw  JSON        NULL     AFTER estado_extraccion;

ALTER TABLE facturas
    ADD CONSTRAINT chk_facturas_estado_extraccion
        CHECK (estado_extraccion IN ('MANUAL', 'PENDIENTE', 'PROCESADA', 'ERROR', 'REVISADA'));

CREATE INDEX idx_facturas_estado_extraccion ON facturas (estado_extraccion);
