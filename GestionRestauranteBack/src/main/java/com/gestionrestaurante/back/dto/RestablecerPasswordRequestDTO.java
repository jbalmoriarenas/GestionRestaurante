package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * {@code password} exige la misma politica fuerte que ya valida el frontend (minimo 8
 * caracteres, mayuscula, minuscula y digito) - revision de codigo, back/fase6-1-back.md: a
 * diferencia de EmpleadoCreateDTO.password (min 6, solo la crea un admin autenticado), este
 * endpoint es publico y autoservicio, asi que el backend debe imponer la politica fuerte como
 * ultima linea de defensa y no confiar solo en la validacion del frontend.
 */
public record RestablecerPasswordRequestDTO(
        @NotBlank String token,
        @NotBlank(message = "La contrasena es obligatoria")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "La contrasena debe tener minimo 8 caracteres, con al menos una mayuscula, una minuscula y un numero")
        String password
) {
}
