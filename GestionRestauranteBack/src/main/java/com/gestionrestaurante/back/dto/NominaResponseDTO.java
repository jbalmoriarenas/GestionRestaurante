package com.gestionrestaurante.back.dto;

import com.gestionrestaurante.back.entity.EstadoNomina;
import java.time.LocalDateTime;

public record NominaResponseDTO(
        Long id,
        Long empleadoId,
        int periodoMes,
        int periodoAnio,
        EstadoNomina estado,
        String nombreArchivoOriginal,
        LocalDateTime fechaSubida,
        LocalDateTime fechaEnvio
) {
}
