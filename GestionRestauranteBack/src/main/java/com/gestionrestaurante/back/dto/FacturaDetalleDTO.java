package com.gestionrestaurante.back.dto;

import com.gestionrestaurante.back.entity.EstadoExtraccion;
import com.gestionrestaurante.back.entity.EstadoPago;
import com.gestionrestaurante.back.entity.TipoProveedor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FacturaDetalleDTO(
        Long id,
        Long proveedorId,
        String empresa,
        TipoProveedor tipo,
        String numeroFactura,
        LocalDate fechaImpresion,
        LocalDate fechaEntrega,
        BigDecimal importeBase,
        BigDecimal iva,
        BigDecimal importeTotal,
        EstadoPago estadoPago,
        EstadoExtraccion estadoExtraccion,
        String nombreArchivoOriginal,
        LocalDateTime fechaSubida
) {
}
