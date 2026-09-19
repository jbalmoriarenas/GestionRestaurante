-- V20/V21 usaron SMALLINT por costumbre (igual que dias_vacaciones_anuales original), pero
-- las entidades JPA correspondientes (ConfiguracionVacaciones.diasAnualesDefecto,
-- Empleado.diasVacacionesAnuales) se declararon como Integer, que Hibernate mapea a INT, no
-- SMALLINT -- con ddl-auto=validate esto rompe el arranque ("wrong column type encountered").
-- Se corrige aqui en vez de editar V20/V21 (ya aplicadas, no se tocan migraciones existentes).
ALTER TABLE configuracion_vacaciones
    MODIFY COLUMN dias_anuales_defecto INT NOT NULL DEFAULT 23;

ALTER TABLE empleados
    MODIFY COLUMN dias_vacaciones_anuales INT NULL;
