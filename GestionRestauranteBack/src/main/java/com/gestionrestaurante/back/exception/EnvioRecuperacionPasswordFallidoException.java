package com.gestionrestaurante.back.exception;

/**
 * Mismo criterio que EnvioNominaFallidoException: traduce un fallo de JavaMailSender (SMTP
 * caido, credenciales invalidas) en un 502 con mensaje claro, en vez de dejarlo caer en el
 * 500 generico de GlobalExceptionHandler#handleGeneric (revision de codigo, back/fase6-1-back.md).
 */
public class EnvioRecuperacionPasswordFallidoException extends RuntimeException {

    public EnvioRecuperacionPasswordFallidoException(Throwable causa) {
        super("No se ha podido enviar el email de recuperación de contraseña: " + causa.getMessage(), causa);
    }
}
