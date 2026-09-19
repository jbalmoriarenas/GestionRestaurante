package com.gestionrestaurante.back.dto;

import jakarta.validation.constraints.Min;

/**
 * diasVacacionesAnuales a null significa "usa el valor global por defecto"
 * (configuracion_vacaciones), no "0 dias" - ver VacacionesService.obtenerDiasVacacionesAnuales.
 * El techo (no poder superar el valor global) es dinamico, no una constante, por lo que se
 * valida en EmpleadoService.actualizarDiasVacacionesAnuales, no aqui con un @Max.
 */
public record DiasVacacionesAnualesUpdateDTO(
        @Min(1) Integer diasVacacionesAnuales
) {
}
