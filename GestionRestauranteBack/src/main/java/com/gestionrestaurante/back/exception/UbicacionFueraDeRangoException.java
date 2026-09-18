package com.gestionrestaurante.back.exception;

public class UbicacionFueraDeRangoException extends RuntimeException {

    public UbicacionFueraDeRangoException() {
        super("Debes estar en el restaurante para fichar");
    }
}
