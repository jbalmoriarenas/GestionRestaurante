package com.gestionrestaurante.back.dto;

import com.gestionrestaurante.back.entity.TipoUsuario;

public record TipoUsuarioDTO(
        String codigo,
        String nombre
) {
    public static TipoUsuarioDTO from(TipoUsuario tipoUsuario) {
        return new TipoUsuarioDTO(tipoUsuario.getCodigo(), tipoUsuario.getNombre());
    }
}
