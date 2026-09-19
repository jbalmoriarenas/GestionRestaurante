package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RechazarSolicitudRequestDTO(
        @NotBlank(message = "El motivo del rechazo es obligatorio")
        // El front ofrece estos tres motivos como opciones cerradas (selector, no texto libre);
        // se valida aqui tambien para no depender solo de la UI.
        @Pattern(
                regexp = "FALTA_DISPONIBILIDAD|YA_SELECCIONADAS_COMPANEROS|OTROS",
                message = "Motivo de rechazo no valido"
        )
        String motivo
) {
}
