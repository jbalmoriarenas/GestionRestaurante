package com.gestionrestaurante.back.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Resultado best-effort de /api/facturas/extraer (back/fase2-3-back.md): nunca se persiste,
 * solo se devuelve al front para prellenar el formulario de alta, que el Admin siempre revisa
 * y confirma antes de guardar. "producto" no aparece aqui a proposito: no hay heuristica
 * fiable para extraerlo, se deja siempre a entrada manual (ver Analisis y decisiones tecnicas
 * de back/fase2-3-back.md).
 */
public record FacturaExtraccionPreviewDTO(
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
