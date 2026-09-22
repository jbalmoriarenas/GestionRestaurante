package com.gestionrestaurante.back.exception;

public class FacturaNotFoundException extends RuntimeException {

    public FacturaNotFoundException(Long id) {
        super("No existe ninguna factura con id " + id);
    }
}
