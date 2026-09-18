CREATE TABLE fichajes (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    empleado_id             BIGINT        NOT NULL,
    fecha                   DATE          NOT NULL,
    hora_entrada            DATETIME      NOT NULL,
    hora_salida             DATETIME      NULL,
    horas_calculadas        DECIMAL(5,2)  NULL,
    lat_entrada             DECIMAL(9,6)  NULL,
    lng_entrada             DECIMAL(9,6)  NULL,
    observaciones           VARCHAR(255)  NULL,
    corregido_por_admin     TINYINT(1)    NOT NULL DEFAULT 0,
    corregido_por_admin_id  BIGINT        NULL,
    fecha_correccion        TIMESTAMP     NULL,
    created_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_fichajes_empleado FOREIGN KEY (empleado_id) REFERENCES empleados (id),
    CONSTRAINT fk_fichajes_corregido_por FOREIGN KEY (corregido_por_admin_id) REFERENCES empleados (id)
);

CREATE INDEX idx_fichajes_empleado_fecha ON fichajes (empleado_id, fecha);

-- Columna generada + índice único: solo puede existir UN fichaje con hora_salida NULL
-- por empleado. Si hora_salida no es NULL, la columna generada vale NULL (MySQL permite
-- múltiples NULL en un índice único), así que los fichajes ya cerrados nunca chocan entre
-- sí; solo choca un segundo INTENTO de fichaje abierto del mismo empleado. Esto convierte
-- la regla de negocio "no se puede fichar entrada dos veces sin fichar salida" en una
-- garantía de integridad de datos, no solo en una validación en el servicio.
ALTER TABLE fichajes
    ADD COLUMN marca_abierto BIGINT
    GENERATED ALWAYS AS (CASE WHEN hora_salida IS NULL THEN empleado_id END) VIRTUAL;

CREATE UNIQUE INDEX uk_fichajes_abierto ON fichajes (marca_abierto);
