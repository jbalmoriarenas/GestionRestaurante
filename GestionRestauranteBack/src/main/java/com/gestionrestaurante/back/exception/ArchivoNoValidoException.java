package com.gestionrestaurante.back.exception;

public class ArchivoNoValidoException extends RuntimeException {

    public ArchivoNoValidoException() {
        super("El archivo debe ser un PDF valido");
    }
}
