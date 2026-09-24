package com.gestionrestaurante.back.entity;

/**
 * Estado del pipeline de OCR de una factura (back/fase2-3-back.md), distinto de
 * {@link EstadoPago} (si la factura esta pagada o no) - no confundir ambos.
 * MANUAL cubre tanto las facturas dadas de alta antes de que existiera OCR (fase2-1, ver
 * V28__add_ocr_columns_a_facturas.sql, DEFAULT 'MANUAL') como las que el Admin decide rellenar
 * a mano sin pasar por /extraer.
 */
public enum EstadoExtraccion {
    MANUAL,
    PENDIENTE,
    PROCESADA,
    ERROR,
    REVISADA
}
