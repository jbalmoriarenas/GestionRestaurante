package com.gestionrestaurante.back.exception;

public class ZipInvalidoException extends RuntimeException {

    public ZipInvalidoException() {
        super("El archivo debe ser un ZIP valido y no estar vacio");
    }
}
