package com.gestionrestaurante.back.dto;

import com.gestionrestaurante.back.entity.ConfiguracionUbicacion;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ConfiguracionUbicacionResponseDTO(
        BigDecimal lat,
        BigDecimal lng,
        Integer radioMetros,
        LocalDateTime updatedAt
) {
    public static ConfiguracionUbicacionResponseDTO from(ConfiguracionUbicacion configuracion) {
        return new ConfiguracionUbicacionResponseDTO(
                configuracion.getLat(),
                configuracion.getLng(),
                configuracion.getRadioMetros(),
                configuracion.getUpdatedAt());
    }
}
