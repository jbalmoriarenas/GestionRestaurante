CREATE TABLE tokens_recuperacion_password (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    empleado_id       BIGINT       NOT NULL,
    token_hash        VARCHAR(64)  NOT NULL,
    fecha_creacion    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_expiracion  TIMESTAMP    NOT NULL,
    fecha_uso         TIMESTAMP    NULL,

    CONSTRAINT fk_tokens_recuperacion_password_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id),
    CONSTRAINT uk_tokens_recuperacion_password_hash UNIQUE (token_hash)
);

CREATE INDEX idx_tokens_recuperacion_password_empleado ON tokens_recuperacion_password (empleado_id);
