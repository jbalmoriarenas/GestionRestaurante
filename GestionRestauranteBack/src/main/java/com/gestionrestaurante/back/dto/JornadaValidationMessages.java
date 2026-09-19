package com.gestionrestaurante.back.dto;

/**
 * Rango y mensajes de validacion de horasJornadaDiaria, compartidos entre EmpleadoCreateDTO,
 * EmpleadoUpdateDTO y HorarioTeoricoUpdateDTO para que el rango [1, 12] no pueda desalinearse
 * entre los tres DTOs que lo validan por separado.
 */
final class JornadaValidationMessages {

    private JornadaValidationMessages() {
    }

    static final String JORNADA_MINIMA = "1";
    static final String JORNADA_MAXIMA = "12";
    static final String MENSAJE_JORNADA_MINIMA = "La jornada minima es de 1 hora";
    static final String MENSAJE_JORNADA_MAXIMA = "La jornada maxima es de 12 horas";
}
