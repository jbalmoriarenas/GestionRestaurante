package com.gestionrestaurante.back.dto;

import org.springframework.core.io.Resource;

public record FacturaDescargaDTO(
        Resource recurso,
        String nombreArchivoOriginal
) {
}
