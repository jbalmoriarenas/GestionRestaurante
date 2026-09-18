CREATE TABLE dias_festivos (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha        DATE          NOT NULL,
    descripcion  VARCHAR(150)  NOT NULL,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_dias_festivos_fecha UNIQUE (fecha)
);
