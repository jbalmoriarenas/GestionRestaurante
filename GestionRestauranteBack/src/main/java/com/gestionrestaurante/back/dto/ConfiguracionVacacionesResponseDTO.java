package com.gestionrestaurante.back.dto;

import com.gestionrestaurante.back.entity.ConfiguracionVacaciones;
import java.time.LocalDateTime;

public record ConfiguracionVacacionesResponseDTO(
        Integer diasAnualesDefecto,
        LocalDateTime updatedAt
) {
    public static ConfiguracionVacacionesResponseDTO from(ConfiguracionVacaciones configuracion) {
        return new ConfiguracionVacacionesResponseDTO(
                configuracion.getDiasAnualesDefecto(),
                configuracion.getUpdatedAt());
    }
}
