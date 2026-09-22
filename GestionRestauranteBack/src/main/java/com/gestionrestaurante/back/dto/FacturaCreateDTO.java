package com.gestionrestaurante.back.dto;

import com.gestionrestaurante.back.entity.EstadoPago;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Formulario de alta de factura (multipart/form-data, back/fase2-1-back.md), bindeado via
 * {@code @ModelAttribute}. {@code camposAdicionalesJson} (fase2-3-back.md) es el mapa de
 * campos dinamicos detectados por OCR, serializado como JSON por el front - se parsea en
 * FacturaService, no aqui, para no acoplar el DTO de entrada a Jackson directamente en el
 * binding de multipart.
 */
public record FacturaCreateDTO(
        @NotNull Long proveedorId,
        String numeroFactura,
        @NotBlank String producto,
        @NotNull LocalDate fechaFactura,
        LocalDate fechaVencimiento,
        BigDecimal importeBase,
        BigDecimal iva,
        @NotNull @Positive BigDecimal importeTotal,
        EstadoPago estadoPago,
        @NotNull MultipartFile archivo,
        Boolean origenOcr,
        String camposAdicionalesJson,
        String datosExtraidosRawJson
) {
}
