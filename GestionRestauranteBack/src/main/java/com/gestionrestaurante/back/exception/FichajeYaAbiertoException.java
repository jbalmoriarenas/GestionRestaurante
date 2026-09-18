package com.gestionrestaurante.back.exception;

public class FichajeYaAbiertoException extends RuntimeException {

    public FichajeYaAbiertoException() {
        super("Ya tienes un fichaje en curso");
    }
}
