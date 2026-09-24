package com.gestionrestaurante.back.dto;

import java.time.LocalDateTime;

public record ConexionEstadoDTO(
        boolean conectado,
        String nombreUbicacion,
        LocalDateTime fechaConexion,
        LocalDateTime fechaUltimaSincronizacion,
        String ultimoError
) {

    public static ConexionEstadoDTO desconectado() {
        return new ConexionEstadoDTO(false, null, null, null, null);
    }
}
