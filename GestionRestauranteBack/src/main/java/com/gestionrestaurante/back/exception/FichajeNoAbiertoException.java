package com.gestionrestaurante.back.exception;

public class FichajeNoAbiertoException extends RuntimeException {

    public FichajeNoAbiertoException() {
        super("No tienes ningun fichaje en curso");
    }
}
