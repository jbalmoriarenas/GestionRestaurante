package com.gestionrestaurante.back.dto;

import java.time.LocalDateTime;

public record NominaListadoDTO(
        Long empleadoId,
        String nombreEmpleado,
        String email,
        int periodoMes,
        int periodoAnio,
        String estado,
        Long nominaId,
        String nombreArchivoOriginal,
        LocalDateTime fechaSubida,
        LocalDateTime fechaEnvio
) {
}
