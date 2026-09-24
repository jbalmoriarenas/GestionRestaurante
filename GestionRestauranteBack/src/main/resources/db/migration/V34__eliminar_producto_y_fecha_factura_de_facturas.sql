-- "producto" y la fecha de venta/emision (fecha_factura) dejan de usarse (decision confirmada
-- con el usuario): el listado y el alta pasan a basarse en fecha de impresion, entrega y
-- vencimiento en su lugar. fecha_impresion pasa a ser la fecha obligatoria que alimenta
-- mes/anio (antes era responsabilidad de fecha_factura) - se rellena primero con el valor de
-- fecha_factura en las filas existentes que no la tuvieran, para no perder el dato de periodo.
UPDATE facturas SET fecha_impresion = fecha_factura WHERE fecha_impresion IS NULL;

ALTER TABLE facturas
    MODIFY COLUMN fecha_impresion DATE NOT NULL,
    DROP COLUMN producto,
    DROP COLUMN fecha_factura;
