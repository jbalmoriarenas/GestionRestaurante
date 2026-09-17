package com.gestionrestaurante.back.exception;

public class DniYaExisteException extends RuntimeException {

    public DniYaExisteException(String dniNie) {
        super("Ya existe un empleado con el DNI/NIE " + dniNie);
    }
}
