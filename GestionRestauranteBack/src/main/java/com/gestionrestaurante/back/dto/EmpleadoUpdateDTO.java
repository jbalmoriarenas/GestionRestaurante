package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record EmpleadoUpdateDTO(
        @NotBlank(message = "El nombre es obligatorio") String nombre,
        @NotBlank(message = "Los apellidos son obligatorios") String apellidos,
        @NotBlank(message = "El email es obligatorio") @Email(message = "Email no valido") String email,
        @NotBlank(message = "El DNI/NIE es obligatorio")
        @Pattern(regexp = "^[0-9XYZxyz][0-9]{7}[A-Za-z]$", message = "DNI/NIE no valido")
        String dniNie,
        String telefono,
        String puesto,
        @NotBlank(message = "El tipo de usuario es obligatorio") String tipoUsuarioCodigo,
        @DecimalMin(value = JornadaValidationMessages.JORNADA_MINIMA, message = JornadaValidationMessages.MENSAJE_JORNADA_MINIMA)
        @DecimalMax(value = JornadaValidationMessages.JORNADA_MAXIMA, message = JornadaValidationMessages.MENSAJE_JORNADA_MAXIMA)
        BigDecimal horasJornadaDiaria
) {
}
