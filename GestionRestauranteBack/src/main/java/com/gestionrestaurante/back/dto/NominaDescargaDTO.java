package com.gestionrestaurante.back.dto;

import org.springframework.core.io.Resource;

public record NominaDescargaDTO(
        Resource recurso,
        String nombreArchivoOriginal
) {
}
