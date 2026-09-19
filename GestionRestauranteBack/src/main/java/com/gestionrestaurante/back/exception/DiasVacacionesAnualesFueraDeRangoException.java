package com.gestionrestaurante.back.exception;

public class DiasVacacionesAnualesFueraDeRangoException extends RuntimeException {

    public DiasVacacionesAnualesFueraDeRangoException(int defecto) {
        super("Los dias anuales de un empleado no pueden superar el valor global configurado (" + defecto + ")");
    }
}
