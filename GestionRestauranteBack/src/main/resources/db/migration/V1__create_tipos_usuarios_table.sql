CREATE TABLE tipos_usuarios (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo       VARCHAR(30)   NOT NULL,
    nombre       VARCHAR(50)   NOT NULL,
    descripcion  VARCHAR(255)  NULL,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_tipos_usuarios_codigo UNIQUE (codigo)
);
