package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Formulario de actualizacion de los datos de empresa (multipart/form-data), bindeado via
 * {@code @ModelAttribute}. {@code logo} es opcional: si no se adjunta, se conserva el logo ya
 * configurado (back/fase5-1-back.md).
 */
public record ConfiguracionEmpresaRequestDTO(
        @NotBlank @Size(max = 150) String nombreEmpresa,
        MultipartFile logo
) {
}
