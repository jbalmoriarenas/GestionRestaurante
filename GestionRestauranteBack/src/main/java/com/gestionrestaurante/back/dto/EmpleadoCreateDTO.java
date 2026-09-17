package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record EmpleadoCreateDTO(
        @NotBlank(message = "El nombre es obligatorio") String nombre,
        @NotBlank(message = "Los apellidos son obligatorios") String apellidos,
        @NotBlank(message = "El email es obligatorio") @Email(message = "Email no valido") String email,
        @NotBlank(message = "El DNI/NIE es obligatorio")
        @Pattern(regexp = "^[0-9XYZxyz][0-9]{7}[A-Za-z]$", message = "DNI/NIE no valido")
        String dniNie,
        String telefono,
        String puesto,
        @NotNull(message = "La fecha de alta es obligatoria") LocalDate fechaAlta,
        @NotBlank(message = "El tipo de usuario es obligatorio") String tipoUsuarioCodigo,
        @NotBlank(message = "La contrasena es obligatoria")
        @Size(min = 6, message = "La contrasena debe tener al menos 6 caracteres")
        String password
) {
}
