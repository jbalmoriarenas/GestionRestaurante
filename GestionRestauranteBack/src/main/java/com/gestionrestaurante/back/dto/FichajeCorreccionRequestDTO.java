package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record FichajeCorreccionRequestDTO(
        @NotNull(message = "La hora de entrada es obligatoria") LocalDateTime horaEntrada,
        @NotNull(message = "La hora de salida es obligatoria") LocalDateTime horaSalida,
        String observaciones
) {
}
