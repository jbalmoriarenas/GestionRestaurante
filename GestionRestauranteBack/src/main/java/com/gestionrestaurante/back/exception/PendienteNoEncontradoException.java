package com.gestionrestaurante.back.exception;

public class PendienteNoEncontradoException extends RuntimeException {

    public PendienteNoEncontradoException(String tempId) {
        super("No existe ningun archivo pendiente de asignar con id " + tempId
                + " (puede que ya se haya asignado)");
    }
}
