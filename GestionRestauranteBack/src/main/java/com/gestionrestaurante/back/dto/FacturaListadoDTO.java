package com.gestionrestaurante.back.dto;

import com.gestionrestaurante.back.entity.EstadoPago;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Exactamente las columnas que pide el usuario (Empresa/Fecha de impresion/Fecha de
 * entrega/Base imponible/IVA/Cuantia/Estado) - el resto de campos de la factura (numero, tipo,
 * origen de la extraccion, archivo...) solo se exponen en {@link FacturaDetalleDTO} y en la
 * exportacion (fase2-2-back.md), nunca en el listado.
 */
public record FacturaListadoDTO(
        Long id,
        String empresa,
        LocalDate fechaImpresion,
        LocalDate fechaEntrega,
        BigDecimal importeBase,
        BigDecimal iva,
        BigDecimal importeTotal,
        EstadoPago estadoPago
) {
}
