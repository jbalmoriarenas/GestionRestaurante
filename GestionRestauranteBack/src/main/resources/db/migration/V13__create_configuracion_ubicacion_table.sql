CREATE TABLE configuracion_ubicacion (
    id                        BIGINT PRIMARY KEY,
    lat                       DECIMAL(9,6)  NOT NULL,
    lng                       DECIMAL(9,6)  NOT NULL,
    radio_metros              INT           NOT NULL DEFAULT 150,
    updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    actualizado_por_admin_id  BIGINT        NULL,

    CONSTRAINT fk_configuracion_ubicacion_admin FOREIGN KEY (actualizado_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT chk_configuracion_ubicacion_id_unico CHECK (id = 1)
);

-- Fila única, sembrada en (0, 0) a propósito: bloquea todos los fichajes por "fuera de
-- rango" hasta que el Admin la configure con la ubicación real del restaurante.
INSERT INTO configuracion_ubicacion (id, lat, lng, radio_metros) VALUES (1, 0, 0, 150);
