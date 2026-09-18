package com.gestionrestaurante.back.dto;

import java.math.BigDecimal;
import java.util.List;

public record FichajeResumenHoyDTO(
        List<FichajeResponseDTO> fichajes,
        BigDecimal horasImputadas,
        BigDecimal horasTeoricas
) {
}
