package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmpleadoUpdateDTO(
        @NotBlank(message = "El nombre es obligatorio") String nombre,
        @NotBlank(message = "Los apellidos son obligatorios") String apellidos,
        @NotBlank(message = "El email es obligatorio") @Email(message = "Email no valido") String email,
        @NotBlank(message = "El DNI/NIE es obligatorio") String dniNie,
        String telefono,
        String puesto,
        @NotBlank(message = "El tipo de usuario es obligatorio") String tipoUsuarioCodigo
) {
}
