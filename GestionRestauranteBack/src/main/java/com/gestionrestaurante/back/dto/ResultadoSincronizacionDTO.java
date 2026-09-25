package com.gestionrestaurante.back.dto;

/** {@code error} es {@code null} cuando la sincronizacion se ha completado sin problemas
 * (incluido el caso "sin conexion activa", que no es un error); si no es {@code null},
 * {@code nuevas}/{@code actualizadas}/{@code total} son siempre 0 y el front debe mostrar el
 * fallo en vez de un mensaje de exito - back/fase4-1-back.md. */
public record ResultadoSincronizacionDTO(int nuevas, int actualizadas, int total, String error) {

    public static ResultadoSincronizacionDTO vacio() {
        return new ResultadoSincronizacionDTO(0, 0, 0, null);
    }

    public static ResultadoSincronizacionDTO error(String mensaje) {
        return new ResultadoSincronizacionDTO(0, 0, 0, mensaje);
    }
}
