package com.gestionrestaurante.back.exception;

public class LogoEmpresaNoConfiguradoException extends RuntimeException {

    public LogoEmpresaNoConfiguradoException() {
        super("La empresa todavía no tiene ningún logo configurado");
    }
}
