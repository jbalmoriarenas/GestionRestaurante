package com.gestionrestaurante.back.dto;

import com.gestionrestaurante.back.entity.EstadoSolicitud;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SolicitudVacacionesAdminResponseDTO(
        Long id,
        Long empleadoId,
        String empleadoNombre,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        short diasSolicitados,
        EstadoSolicitud estado,
        LocalDateTime fechaSolicitud,
        String motivoRechazo
) {
}
