package com.gestionrestaurante.back.exception;

public class EmailYaExisteException extends RuntimeException {

    public EmailYaExisteException(String email) {
        super("Ya existe un empleado con el email " + email);
    }
}
