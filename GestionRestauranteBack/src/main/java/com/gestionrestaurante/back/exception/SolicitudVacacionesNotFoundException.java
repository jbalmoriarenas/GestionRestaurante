package com.gestionrestaurante.back.exception;

public class SolicitudVacacionesNotFoundException extends RuntimeException {

    public SolicitudVacacionesNotFoundException(Long id) {
        super("No existe ninguna solicitud de vacaciones con id " + id);
    }
}
