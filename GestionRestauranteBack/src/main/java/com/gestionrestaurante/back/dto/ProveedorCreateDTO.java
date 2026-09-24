package com.gestionrestaurante.back.dto;

import com.gestionrestaurante.back.entity.TipoProveedor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProveedorCreateDTO(
        @NotBlank String nombre,
        String cif,
        String emailContacto,
        String telefono,
        @NotNull TipoProveedor tipo
) {
}
