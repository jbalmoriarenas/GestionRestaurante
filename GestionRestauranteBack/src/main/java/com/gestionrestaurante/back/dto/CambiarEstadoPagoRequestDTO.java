package com.gestionrestaurante.back.dto;

import com.gestionrestaurante.back.entity.EstadoPago;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoPagoRequestDTO(
        @NotNull EstadoPago estadoPago
) {
}
