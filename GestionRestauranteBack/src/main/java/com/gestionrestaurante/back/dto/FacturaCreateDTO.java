package com.gestionrestaurante.back.dto;

import com.gestionrestaurante.back.entity.EstadoPago;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Formulario de alta de factura (multipart/form-data, back/fase2-1-back.md), bindeado via
 * {@code @ModelAttribute}. {@code origenOcr} (fase2-3-back.md) marca si el alta parte de una
 * previsualizacion de /extraer (aunque el Admin haya editado los valores propuestos), y solo
 * determina {@code estadoExtraccion}; no se persiste ningun dato adicional de la extraccion -
 * el PDF/imagen original (@code archivo) es la unica fuente de verdad para consultar datos que
 * no esten entre los campos fijos.
 */
public record FacturaCreateDTO(
        @NotNull Long proveedorId,
        String numeroFactura,
        @NotNull LocalDate fechaImpresion,
        LocalDate fechaEntrega,
        BigDecimal importeBase,
        BigDecimal iva,
        @NotNull @Positive BigDecimal importeTotal,
        EstadoPago estadoPago,
        @NotNull MultipartFile archivo,
        Boolean origenOcr
) {
}
