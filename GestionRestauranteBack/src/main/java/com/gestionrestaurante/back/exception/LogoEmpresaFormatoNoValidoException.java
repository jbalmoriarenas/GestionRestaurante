package com.gestionrestaurante.back.exception;

public class LogoEmpresaFormatoNoValidoException extends RuntimeException {

    public LogoEmpresaFormatoNoValidoException() {
        super("El logo debe ser una imagen (JPEG/PNG/WEBP)");
    }
}
