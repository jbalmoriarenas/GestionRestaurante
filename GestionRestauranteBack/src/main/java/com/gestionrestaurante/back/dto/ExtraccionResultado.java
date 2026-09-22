package com.gestionrestaurante.back.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Resultado interno (servicio, no expuesto tal cual al front) de FacturaExtractionService -
 * back/fase2-3-back.md. FacturaService.extraerPreview lo convierte a FacturaExtraccionPreviewDTO.
 */
public record ExtraccionResultado(
        String textoOcrCrudo,
        Long proveedorSugeridoId,
        String nombreProveedorSugerido,
        LocalDate fechaFacturaSugerida,
        String numeroFacturaSugerido,
        BigDecimal importeBaseSugerido,
        BigDecimal ivaSugerido,
        BigDecimal importeTotalSugerido,
        Map<String, String> camposAdicionales
) {
}
