package com.gestionrestaurante.back.dto;

import java.util.List;

public record LoginResponseDTO(
        String token,
        Long empleadoId,
        String nombre,
        String apellidos,
        TipoUsuarioDTO tipoUsuario,
        List<PermisoDTO> permisos
) {
}
