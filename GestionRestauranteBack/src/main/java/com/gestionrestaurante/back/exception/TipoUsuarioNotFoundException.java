package com.gestionrestaurante.back.exception;

public class TipoUsuarioNotFoundException extends RuntimeException {

    public TipoUsuarioNotFoundException(String codigo) {
        super("No existe ningun tipo de usuario con codigo " + codigo);
    }
}
