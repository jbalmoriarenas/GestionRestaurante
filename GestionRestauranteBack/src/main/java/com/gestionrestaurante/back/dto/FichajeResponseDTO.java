package com.gestionrestaurante.back.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FichajeResponseDTO(
        Long id,
        LocalDate fecha,
        LocalDateTime horaEntrada,
        LocalDateTime horaSalida,
        BigDecimal horasCalculadas,
        String observaciones
) {
}
