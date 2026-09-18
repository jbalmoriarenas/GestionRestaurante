CREATE TABLE solicitudes_vacaciones (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    empleado_id            BIGINT        NOT NULL,
    fecha_inicio           DATE          NOT NULL,
    fecha_fin              DATE          NOT NULL,
    dias_solicitados       SMALLINT      NOT NULL,
    estado                 VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    fecha_solicitud        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_resolucion       TIMESTAMP     NULL,
    resuelta_por_admin_id  BIGINT        NULL,
    motivo_rechazo         VARCHAR(255)  NULL,
    created_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_solicitudes_vacaciones_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id),
    CONSTRAINT fk_solicitudes_vacaciones_admin FOREIGN KEY (resuelta_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT chk_solicitudes_vacaciones_estado CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    CONSTRAINT chk_solicitudes_vacaciones_fechas CHECK (fecha_fin >= fecha_inicio)
);

CREATE INDEX idx_solicitudes_vacaciones_empleado_estado ON solicitudes_vacaciones (empleado_id, estado);
