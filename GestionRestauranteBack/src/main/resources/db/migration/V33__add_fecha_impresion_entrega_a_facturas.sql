-- El alta de facturas (fase2-3) distingue tres fechas que un ticket de venta al por mayor
-- (p. ej. Makro) puede traer por separado: venta/emision (ya cubierta por fecha_factura,
-- de la que se derivan mes/anio), impresion y entrega. Ambas nuevas columnas son opcionales:
-- solo se rellenan cuando la extraccion OCR o el Admin las identifican explicitamente.
ALTER TABLE facturas
    ADD COLUMN fecha_impresion DATE NULL AFTER fecha_factura,
    ADD COLUMN fecha_entrega   DATE NULL AFTER fecha_impresion;
