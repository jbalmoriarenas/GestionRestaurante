package com.gestionrestaurante.back.exception;

public class EmpleadoNotFoundException extends RuntimeException {

    public EmpleadoNotFoundException(Long id) {
        super("No existe ningun empleado con id " + id);
    }
}
