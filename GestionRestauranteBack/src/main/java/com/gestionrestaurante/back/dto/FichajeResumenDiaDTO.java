package com.gestionrestaurante.back.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FichajeResumenDiaDTO(
        LocalDate fecha,
        BigDecimal horasCalculadas,
        BigDecimal horasTeoricas,
        boolean cumpleMinimo
) {
}
