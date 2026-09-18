package com.gestionrestaurante.back.exception;

public class DiasVacacionesInsuficientesException extends RuntimeException {

    public DiasVacacionesInsuficientesException() {
        super("No tienes suficientes dias de vacaciones disponibles para ese rango");
    }
}
