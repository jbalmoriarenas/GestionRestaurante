package com.gestionrestaurante.back.dto;

import java.time.LocalDateTime;

public record FichajeActualDTO(
        boolean enCurso,
        LocalDateTime horaEntrada
) {
}
