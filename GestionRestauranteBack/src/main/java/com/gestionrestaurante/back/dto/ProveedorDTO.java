package com.gestionrestaurante.back.dto;

import com.gestionrestaurante.back.entity.TipoProveedor;

public record ProveedorDTO(
        Long id,
        String nombre,
        String cif,
        String emailContacto,
        String telefono,
        TipoProveedor tipo
) {
}
