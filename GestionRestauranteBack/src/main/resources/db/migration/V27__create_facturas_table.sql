CREATE TABLE facturas (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    proveedor_id             BIGINT         NOT NULL,
    numero_factura           VARCHAR(50)    NULL,
    producto                 VARCHAR(255)   NOT NULL,
    fecha_factura             DATE           NOT NULL,
    fecha_vencimiento        DATE           NULL,
    importe_base             DECIMAL(10,2)  NULL,
    iva                      DECIMAL(10,2)  NULL,
    importe_total            DECIMAL(10,2)  NOT NULL,
    mes                      TINYINT        NOT NULL,
    anio                     SMALLINT       NOT NULL,
    estado_pago              VARCHAR(20)    NOT NULL DEFAULT 'PENDIENTE',
    ruta_archivo              VARCHAR(255)   NOT NULL,
    nombre_archivo_original  VARCHAR(255)   NOT NULL,
    subida_por_admin_id      BIGINT         NOT NULL,
    fecha_subida             TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_facturas_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores (id),
    CONSTRAINT fk_facturas_subida_admin FOREIGN KEY (subida_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT chk_facturas_mes CHECK (mes BETWEEN 1 AND 12),
    CONSTRAINT chk_facturas_estado_pago CHECK (estado_pago IN ('PENDIENTE', 'PAGADA'))
);

CREATE INDEX idx_facturas_proveedor ON facturas (proveedor_id);
CREATE INDEX idx_facturas_periodo ON facturas (anio, mes);
CREATE INDEX idx_facturas_estado_pago ON facturas (estado_pago);
