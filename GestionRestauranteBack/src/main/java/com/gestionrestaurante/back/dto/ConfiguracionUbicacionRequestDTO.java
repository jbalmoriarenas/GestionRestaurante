package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ConfiguracionUbicacionRequestDTO(
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal lng,
        @NotNull @Min(10) @Max(2000) Integer radioMetros
) {
}
