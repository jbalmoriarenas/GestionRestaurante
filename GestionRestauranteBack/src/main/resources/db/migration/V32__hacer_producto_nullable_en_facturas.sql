-- El campo "producto" (texto libre) deja de pedirse en el alta manual/OCR: para facturas con
-- muchas lineas de articulos distintos (p.ej. tickets de distribuidores) forzar un unico valor
-- de texto no tiene sentido, y el detalle de lineas de producto queda fuera de alcance de esta
-- fase (ver nota en bbdd/fase2-3-bbdd.md sobre una futura vista de almacenes con su propio
-- modelo de lineas de factura). Se mantiene la columna, ahora opcional, por compatibilidad con
-- las filas ya existentes y por si el Admin quiere seguir anotando un concepto libre.
ALTER TABLE facturas MODIFY COLUMN producto VARCHAR(255) NULL;
