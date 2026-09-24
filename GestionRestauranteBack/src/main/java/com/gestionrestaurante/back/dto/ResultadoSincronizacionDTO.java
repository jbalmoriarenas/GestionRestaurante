package com.gestionrestaurante.back.dto;

public record ResultadoSincronizacionDTO(int nuevas, int actualizadas, int total) {

    public static ResultadoSincronizacionDTO vacio() {
        return new ResultadoSincronizacionDTO(0, 0, 0);
    }
}
