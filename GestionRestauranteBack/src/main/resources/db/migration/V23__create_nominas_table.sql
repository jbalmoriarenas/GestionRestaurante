CREATE TABLE nominas (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    empleado_id              BIGINT        NOT NULL,
    periodo_mes              TINYINT       NOT NULL,
    periodo_anio             SMALLINT      NOT NULL,
    ruta_archivo             VARCHAR(255)  NOT NULL,
    nombre_archivo_original  VARCHAR(255)  NOT NULL,
    estado                   VARCHAR(20)   NOT NULL DEFAULT 'ADJUNTADA',
    fecha_subida             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    subida_por_admin_id      BIGINT        NOT NULL,
    fecha_envio              TIMESTAMP     NULL,
    enviada_por_admin_id     BIGINT        NULL,

    CONSTRAINT fk_nominas_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id),
    CONSTRAINT fk_nominas_subida_admin FOREIGN KEY (subida_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT fk_nominas_enviada_admin FOREIGN KEY (enviada_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT chk_nominas_periodo_mes CHECK (periodo_mes BETWEEN 1 AND 12),
    CONSTRAINT chk_nominas_estado CHECK (estado IN ('ADJUNTADA', 'ENVIADA')),
    CONSTRAINT uk_nominas_empleado_periodo UNIQUE (empleado_id, periodo_mes, periodo_anio)
);

CREATE INDEX idx_nominas_periodo ON nominas (periodo_anio, periodo_mes);
