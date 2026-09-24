package com.gestionrestaurante.back.dto;

import java.time.LocalDateTime;

/** Sin {@code googleReviewId} ni {@code fechaSincronizacion} (detalles internos de
 * sincronizacion, sin valor para el Admin) - back/fase4-2-back.md. */
public record ValoracionDTO(
        Long id,
        String autorNombre,
        String autorFotoUrl,
        Integer puntuacion,
        String comentario,
        LocalDateTime fechaPublicacion,
        LocalDateTime fechaActualizacion
) {
}
