package com.gestionrestaurante.back.exception;

/**
 * Google respondio con error al intercambiar/refrescar tokens o al llamar a la API de
 * reseñas/ubicaciones (back/fase4-1-back.md) - servicio externo fuera de control del
 * proyecto, nunca debe traducirse en un 500 crudo.
 */
public class ConexionGoogleNoDisponibleException extends RuntimeException {

    public ConexionGoogleNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    public ConexionGoogleNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
