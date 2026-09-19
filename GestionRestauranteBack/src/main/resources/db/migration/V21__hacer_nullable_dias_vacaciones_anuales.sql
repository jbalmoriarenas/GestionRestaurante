ALTER TABLE empleados
    MODIFY COLUMN dias_vacaciones_anuales SMALLINT NULL;

-- Todas las filas existentes tienen 23 unicamente porque era el DEFAULT de V14
-- (fase1-2-bbdd.md); nadie lo ha personalizado todavia, no existia como hacerlo. Se limpian
-- a NULL para que, a partir de ahora, sigan dinamicamente el valor global de
-- configuracion_vacaciones en vez de quedar clavadas en el 23 que tenian por accidente de
-- la migracion original.
UPDATE empleados SET dias_vacaciones_anuales = NULL;
