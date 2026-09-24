CREATE TABLE valoraciones (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    google_review_id       VARCHAR(150)  NOT NULL,
    autor_nombre            VARCHAR(150)  NOT NULL,
    autor_foto_url           VARCHAR(500)  NULL,
    puntuacion              INT           NOT NULL,
    comentario               TEXT          NULL,
    fecha_publicacion        TIMESTAMP     NOT NULL,
    fecha_actualizacion      TIMESTAMP     NULL,
    fecha_sincronizacion     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_valoraciones_google_review_id UNIQUE (google_review_id),
    CONSTRAINT chk_valoraciones_puntuacion CHECK (puntuacion BETWEEN 1 AND 5)
);

CREATE INDEX idx_valoraciones_puntuacion ON valoraciones (puntuacion);
CREATE INDEX idx_valoraciones_fecha_publicacion ON valoraciones (fecha_publicacion);
