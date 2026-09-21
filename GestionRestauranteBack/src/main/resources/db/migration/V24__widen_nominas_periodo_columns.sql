-- Hibernate valida periodoMes/periodoAnio como Integer (INT), no como TINYINT/SMALLINT
-- (ver mapeo de fase3-1-bbdd.md); se ensanchan las columnas para que coincidan con el tipo Java.
ALTER TABLE nominas MODIFY COLUMN periodo_mes  INT NOT NULL;
ALTER TABLE nominas MODIFY COLUMN periodo_anio INT NOT NULL;
