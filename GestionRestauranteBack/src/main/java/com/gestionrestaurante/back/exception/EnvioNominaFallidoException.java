package com.gestionrestaurante.back.exception;

public class EnvioNominaFallidoException extends RuntimeException {

    public EnvioNominaFallidoException(Throwable causa) {
        super("No se ha podido enviar el email con la nomina: " + causa.getMessage(), causa);
    }
}
