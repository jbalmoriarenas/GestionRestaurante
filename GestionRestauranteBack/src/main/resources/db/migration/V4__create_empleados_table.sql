CREATE TABLE empleados (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre           VARCHAR(100)  NOT NULL,
    apellidos        VARCHAR(150)  NOT NULL,
    email            VARCHAR(150)  NOT NULL,
    dni_nie          VARCHAR(15)   NOT NULL,
    telefono         VARCHAR(20)   NULL,
    puesto           VARCHAR(100)  NULL,
    fecha_alta       DATE          NOT NULL,
    fecha_baja       DATE          NULL,
    activo           TINYINT(1)    NOT NULL DEFAULT 1,
    tipo_usuario_id  BIGINT        NOT NULL,
    password_hash    VARCHAR(60)   NOT NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_empleados_email UNIQUE (email),
    CONSTRAINT uk_empleados_dni_nie UNIQUE (dni_nie),
    CONSTRAINT fk_empleados_tipo_usuario FOREIGN KEY (tipo_usuario_id) REFERENCES tipos_usuarios (id)
);

CREATE INDEX idx_empleados_activo ON empleados (activo);
CREATE INDEX idx_empleados_tipo_usuario ON empleados (tipo_usuario_id);
