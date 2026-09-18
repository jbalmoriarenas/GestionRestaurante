package com.gestionrestaurante.back.dto;

import java.math.BigDecimal;

public record FichajeResumenMesDTO(
        int mes,
        int anio,
        BigDecimal horasCalculadas,
        boolean cumpleMinimo
) {
}
