package com.gestionrestaurante.back.exception;

/**
 * Cooldown entre solicitudes de recuperacion de contraseña para el mismo email (revision de
 * codigo, back/fase6-1-back.md): sin esto, el endpoint publico permitiria spamear de correos a
 * un empleado concreto o agotar la cuota/reputacion de la cuenta SMTP.
 */
public class SolicitudRecuperacionDemasiadoFrecuenteException extends RuntimeException {

    public SolicitudRecuperacionDemasiadoFrecuenteException() {
        super("Ya se ha solicitado un enlace de recuperación recientemente. Inténtalo de nuevo en unos minutos.");
    }
}
