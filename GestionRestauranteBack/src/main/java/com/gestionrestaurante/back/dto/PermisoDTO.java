package com.gestionrestaurante.back.dto;

import com.gestionrestaurante.back.entity.NivelAcceso;

public record PermisoDTO(
        String vista,
        NivelAcceso nivelAcceso
) {
}
