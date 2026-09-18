package com.gestionrestaurante.back.security;

import java.util.Objects;
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
        // No deberia poder faltar (JwtService siempre incluye el claim al generar el
        // token), pero si algun dia se acepta un token sin el, se prefiere un fallo
        // explicito aqui a un NPE/ClassCastException opaco varias capas mas abajo
        // (p.ej. dentro de EmpleadoRepository.getReferenceById(null)).
        return Objects.requireNonNull(
                (Long) authentication.getDetails(),
                "El token no contiene el empleadoId del usuario autenticado");
    }
}
