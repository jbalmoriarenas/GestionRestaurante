package com.gestionrestaurante.back.dto;

public record ResumenVacacionesDTO(
        int total,
        int aprobados,
        int pendientes,
        int disponibles
) {
}
