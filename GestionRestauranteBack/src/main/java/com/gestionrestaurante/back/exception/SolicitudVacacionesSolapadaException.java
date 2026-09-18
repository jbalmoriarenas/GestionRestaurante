package com.gestionrestaurante.back.exception;

public class SolicitudVacacionesSolapadaException extends RuntimeException {

    public SolicitudVacacionesSolapadaException() {
        super("Ya tienes una solicitud de vacaciones pendiente o aprobada que se solapa con ese rango");
    }
}
