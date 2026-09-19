package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ConfiguracionVacacionesRequestDTO(
        @NotNull @Min(1) @Max(365) Integer diasAnualesDefecto
) {
}
