CREATE TABLE permisos (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo_usuario_id  BIGINT       NOT NULL,
    vista_id         BIGINT       NOT NULL,
    nivel_acceso     VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_permisos_tipo_usuario FOREIGN KEY (tipo_usuario_id) REFERENCES tipos_usuarios (id),
    CONSTRAINT fk_permisos_vista FOREIGN KEY (vista_id) REFERENCES vistas (id),
    CONSTRAINT uk_permisos_tipo_vista UNIQUE (tipo_usuario_id, vista_id),
    CONSTRAINT chk_permisos_nivel_acceso CHECK (nivel_acceso IN ('EDICION', 'VISUALIZACION'))
);
