CREATE TABLE configuracion_vacaciones (
    id                        BIGINT PRIMARY KEY,
    dias_anuales_defecto      SMALLINT      NOT NULL DEFAULT 23,
    updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    actualizado_por_admin_id  BIGINT        NULL,

    CONSTRAINT fk_configuracion_vacaciones_admin FOREIGN KEY (actualizado_por_admin_id) REFERENCES empleados (id),
    CONSTRAINT chk_configuracion_vacaciones_id_unico CHECK (id = 1)
);

-- Fila unica, sembrada en 23 (el mismo valor que ya era la constante Java por defecto):
-- el despliegue de esta migracion no cambia el comportamiento observable del sistema.
INSERT INTO configuracion_vacaciones (id, dias_anuales_defecto) VALUES (1, 23);
