package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/**
 * horasJornadaDiaria a null significa "usa el valor global por defecto"
 * (app.fichajes.horas-jornada-default), no "0 horas" - ver FichajeService.obtenerUmbralDiario.
 */
public record HorarioTeoricoUpdateDTO(
        @DecimalMin(value = JornadaValidationMessages.JORNADA_MINIMA, message = JornadaValidationMessages.MENSAJE_JORNADA_MINIMA)
        @DecimalMax(value = JornadaValidationMessages.JORNADA_MAXIMA, message = JornadaValidationMessages.MENSAJE_JORNADA_MAXIMA)
        BigDecimal horasJornadaDiaria
) {
}
