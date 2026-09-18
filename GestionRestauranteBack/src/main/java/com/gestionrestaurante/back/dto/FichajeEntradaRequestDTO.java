package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.NotNull;

public record FichajeEntradaRequestDTO(
        @NotNull Double lat,
        @NotNull Double lng
) {
}
