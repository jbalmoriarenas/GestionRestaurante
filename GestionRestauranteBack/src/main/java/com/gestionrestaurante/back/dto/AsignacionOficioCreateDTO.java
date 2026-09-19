package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record AsignacionOficioCreateDTO(
        @NotNull(message = "El empleado es obligatorio") Long empleadoId,
        @NotNull(message = "La fecha de inicio es obligatoria") LocalDate fechaInicio,
        @NotNull(message = "La fecha de fin es obligatoria") LocalDate fechaFin
) {
}
