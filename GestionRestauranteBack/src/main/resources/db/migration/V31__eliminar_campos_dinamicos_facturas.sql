-- Revierte el patron EAV de fase2-3 (V29): tras probar el flujo con facturas reales, guardar
-- una fila por cada etiqueta "Etiqueta: Valor" que el OCR reconoce en el texto genera ruido
-- (docenas de filas irrelevantes por factura en tickets con muchas lineas, p.ej. codigos de
-- barra o datos de cliente) en vez de datos utiles. El PDF/imagen original (facturas.ruta_archivo)
-- ya se conserva siempre y es la via para consultar cualquier dato que no sea proveedor, fecha
-- o importes; el texto OCR crudo (V28, datos_extraidos_raw) tampoco se usaba para nada mas que
-- auditoria y se elimina por el mismo motivo.
DROP TABLE factura_campos_dinamicos;

ALTER TABLE facturas DROP COLUMN datos_extraidos_raw;
