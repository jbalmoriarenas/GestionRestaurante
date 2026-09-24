-- Mismo problema ya corregido en nominas (V24__widen_nominas_periodo_columns.sql): Hibernate
-- valida mes/anio como Integer (INT), no como TINYINT/SMALLINT (ver mapeo de fase2-1-bbdd.md).
ALTER TABLE facturas MODIFY COLUMN mes  INT NOT NULL;
ALTER TABLE facturas MODIFY COLUMN anio INT NOT NULL;
