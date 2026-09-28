package com.gestionrestaurante.back.dto;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

/**
 * Encapsula el recurso del logo ya resuelto (contenido + tipo de imagen), mismo patron que
 * FacturaDescargaDTO: el controller no debe conocer FileStorageService, solo pedirle este DTO
 * al servicio (back/fase5-1-back.md, revision de codigo).
 */
public record ConfiguracionEmpresaLogoDTO(
        Resource recurso,
        MediaType mediaType
) {
}
