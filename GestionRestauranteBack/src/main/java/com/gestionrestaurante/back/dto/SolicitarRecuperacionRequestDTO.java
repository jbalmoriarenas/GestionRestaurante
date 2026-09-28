package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SolicitarRecuperacionRequestDTO(
        @NotBlank @Email String email
) {
}
