package com.gestionrestaurante.back.exception;

public class FichajeNotFoundException extends RuntimeException {

    public FichajeNotFoundException(Long id) {
        super("No existe ningun fichaje con id " + id);
    }
}
