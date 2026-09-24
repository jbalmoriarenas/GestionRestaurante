package com.gestionrestaurante.back.exception;

/**
 * El {@code state} recibido en {@code /api/valoraciones/conexion/callback} no fue emitido por
 * {@code /conexion/url} o ya expiro (TTL 10 minutos, back/fase4-1-back.md) - protege el
 * callback (el unico endpoint del proyecto sin @PreAuthorize, ver ese documento) sin depender
 * de un JWT que Google no puede enviar en una redireccion de navegador.
 */
public class EstadoOAuthInvalidoException extends RuntimeException {

    public EstadoOAuthInvalidoException() {
        super("El estado de la conexion con Google no es valido o ha expirado. Vuelve a iniciar la conexion.");
    }
}
