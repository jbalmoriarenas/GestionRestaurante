package com.gestionrestaurante.back.exception;

public class SolicitudVacacionesNoPropiaException extends RuntimeException {

    public SolicitudVacacionesNoPropiaException() {
        super("La solicitud no existe o no es tuya");
    }
}
