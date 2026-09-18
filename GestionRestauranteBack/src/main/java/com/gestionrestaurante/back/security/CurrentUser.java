package com.gestionrestaurante.back.security;

import org.springframework.security.core.Authentication;

/**
 * Extrae el empleadoId del usuario autenticado. JwtAuthFilter guarda el empleadoId del
 * claim del JWT en los "details" de la Authentication (el principal se deja como el email,
 * para no romper nada que ya dependa de Authentication#getName()).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long empleadoId(Authentication authentication) {
        return (Long) authentication.getDetails();
    }
}
