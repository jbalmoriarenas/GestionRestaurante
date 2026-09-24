package com.gestionrestaurante.back.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resultado best-effort de /api/facturas/extraer (back/fase2-3-back.md): nunca se persiste,
 * solo se devuelve al front para prellenar el formulario de alta, que el Admin siempre revisa
 * y confirma antes de guardar. Solo cubre los campos fijos de Factura (fecha de
 * impresion/entrega, importe base/IVA/total) - "producto" no aparece aqui a proposito: no hay
 * heuristica fiable para extraerlo, se deja siempre a entrada manual. El proveedor tampoco:
 * siempre se selecciona (o se da de alta) a mano. Cualquier otro dato de la factura que el
 * Admin necesite consultar esta en el PDF/imagen original, siempre descargable.
 */
public record FacturaExtraccionPreviewDTO(
        LocalDate fechaImpresionSugerida,
        LocalDate fechaEntregaSugerida,
        BigDecimal importeBaseSugerido,
        BigDecimal ivaSugerido,
        BigDecimal importeTotalSugerido
) {
}
