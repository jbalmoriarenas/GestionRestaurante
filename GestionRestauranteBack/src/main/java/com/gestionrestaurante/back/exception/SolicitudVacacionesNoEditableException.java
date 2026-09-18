package com.gestionrestaurante.back.exception;

public class SolicitudVacacionesNoEditableException extends RuntimeException {

    public SolicitudVacacionesNoEditableException() {
        super("La solicitud no existe, no es tuya o ya no esta pendiente");
    }
}
