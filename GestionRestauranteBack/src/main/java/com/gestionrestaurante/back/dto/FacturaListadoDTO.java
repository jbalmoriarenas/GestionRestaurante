package com.gestionrestaurante.back.dto;

import com.gestionrestaurante.back.entity.EstadoPago;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Exactamente las columnas que pide el usuario (Empresa/Producto/Cuantia/Fecha/Estado,
 * back/fase2-1-back.md) - el resto de campos de la factura solo se exponen en
 * {@link FacturaDetalleDTO} y en la exportacion (fase2-2-back.md), nunca en el listado.
 */
public record FacturaListadoDTO(
        Long id,
        String empresa,
        String producto,
        BigDecimal importeTotal,
        LocalDate fechaFactura,
        EstadoPago estadoPago
) {
}
