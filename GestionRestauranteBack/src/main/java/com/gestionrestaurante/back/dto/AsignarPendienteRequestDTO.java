package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AsignarPendienteRequestDTO(
        @NotNull(message = "El empleado es obligatorio") Long empleadoId,
        @NotNull(message = "El mes del periodo es obligatorio") @Min(1) @Max(12) Integer periodoMes,
        // Mismo rango 2000-2100 ya usado en NominaController/VacacionesController.
        @NotNull(message = "El año del periodo es obligatorio") @Min(2000) @Max(2100) Integer periodoAnio
) {
}
