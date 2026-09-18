package com.gestionrestaurante.back.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FichajeResumenSemanaDTO(
        LocalDate inicioSemana,
        LocalDate finSemana,
        BigDecimal horasCalculadas,
        boolean cumpleMinimo
) {
}
