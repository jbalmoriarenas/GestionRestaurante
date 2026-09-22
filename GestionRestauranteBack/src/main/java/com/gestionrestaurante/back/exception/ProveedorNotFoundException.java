package com.gestionrestaurante.back.exception;

public class ProveedorNotFoundException extends RuntimeException {

    public ProveedorNotFoundException(Long id) {
        super("No existe ningun proveedor con id " + id);
    }
}
