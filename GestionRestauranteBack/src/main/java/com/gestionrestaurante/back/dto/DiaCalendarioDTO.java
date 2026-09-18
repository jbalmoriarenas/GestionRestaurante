package com.gestionrestaurante.back.dto;

import java.time.LocalDate;

public record DiaCalendarioDTO(
        LocalDate fecha,
        TipoDiaCalendario tipo
) {
}
