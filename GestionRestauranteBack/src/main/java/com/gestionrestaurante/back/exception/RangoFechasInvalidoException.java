package com.gestionrestaurante.back.exception;

public class RangoFechasInvalidoException extends RuntimeException {

    public RangoFechasInvalidoException() {
        super("La fecha de fin debe ser igual o posterior a la fecha de inicio");
    }

    public RangoFechasInvalidoException(String message) {
        super(message);
    }
}
