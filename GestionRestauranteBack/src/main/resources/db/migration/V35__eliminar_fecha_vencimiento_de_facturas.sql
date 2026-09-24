-- "fecha_vencimiento" nunca llego a rellenarse desde ningun formulario (ni el alta manual ni
-- la extraccion OCR la piden): siempre quedaba NULL. Se elimina por no usarse (decision
-- confirmada con el usuario).
ALTER TABLE facturas
    DROP COLUMN fecha_vencimiento;
