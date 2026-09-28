package com.gestionrestaurante.back.dto;

import com.gestionrestaurante.back.entity.ConfiguracionEmpresa;

/**
 * {@code logoUrl} no es una URL directamente utilizable desde fuera de la app (p.ej. en un
 * {@code <img src>} nativo): el endpoint que representa exige un usuario autenticado, y solo
 * las peticiones via HttpClient (con el interceptor de JWT) llevan esa cabecera. El front la
 * trata unicamente como flag de presencia (null = sin logo) y pide el contenido real como blob
 * (back/fase5-1-front.md) - revision de codigo.
 */
public record ConfiguracionEmpresaResponseDTO(
        String nombreEmpresa,
        String logoUrl,
        String emailRemitente
) {
    public static ConfiguracionEmpresaResponseDTO from(ConfiguracionEmpresa configuracion, String rutaLogoEndpoint) {
        return new ConfiguracionEmpresaResponseDTO(
                configuracion.getNombreEmpresa(),
                configuracion.getLogoRuta() != null ? rutaLogoEndpoint : null,
                configuracion.getEmailRemitente());
    }
}
