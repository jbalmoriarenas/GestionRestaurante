package com.gestionrestaurante.back.exception;

public class NominaNotFoundException extends RuntimeException {

    public NominaNotFoundException(Long id) {
        super("No existe ninguna nomina con id " + id);
    }
}
