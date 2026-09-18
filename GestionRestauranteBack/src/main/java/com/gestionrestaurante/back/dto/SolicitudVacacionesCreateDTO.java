package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record SolicitudVacacionesCreateDTO(
        @NotNull LocalDate fechaInicio,
        @NotNull LocalDate fechaFin
) {
}
