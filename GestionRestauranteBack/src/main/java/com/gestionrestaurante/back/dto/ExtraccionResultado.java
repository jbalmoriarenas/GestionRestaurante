package com.gestionrestaurante.back.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resultado interno (servicio, no expuesto tal cual al front) de FacturaExtractionService -
 * back/fase2-3-back.md. FacturaService.extraerPreview lo convierte a FacturaExtraccionPreviewDTO.
 * Solo agrupa los campos fijos de Factura que la heuristica es capaz de proponer: fecha de
 * impresion/entrega, importe base/IVA/total. El proveedor siempre se selecciona (o se da de
 * alta) a mano, no hay heuristica de coincidencia sobre el texto OCR. El resto de la factura
 * solo esta disponible en el PDF/imagen original, que se conserva y se puede descargar
 * (Factura.rutaArchivo).
 */
public record ExtraccionResultado(
        LocalDate fechaImpresionSugerida,
        LocalDate fechaEntregaSugerida,
        BigDecimal importeBaseSugerido,
        BigDecimal ivaSugerido,
        BigDecimal importeTotalSugerido
) {
}
