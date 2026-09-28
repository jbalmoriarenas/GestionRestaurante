package com.gestionrestaurante.back.exception;

/**
 * El token de recuperacion de contraseña recibido no existe, ya se uso, o ha caducado
 * (back/fase6-1-back.md) - mismo tipo de caso que EstadoOAuthInvalidoException (un
 * token/estado de un flujo que ya no es valido), agrupados en el mismo handler de
 * GlobalExceptionHandler.
 */
public class TokenRecuperacionInvalidoException extends RuntimeException {

    public TokenRecuperacionInvalidoException() {
        super("El enlace de recuperación no es válido o ha caducado. Solicita uno nuevo.");
    }
}
