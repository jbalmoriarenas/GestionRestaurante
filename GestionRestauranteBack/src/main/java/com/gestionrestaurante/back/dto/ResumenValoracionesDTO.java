package com.gestionrestaurante.back.dto;

import java.math.BigDecimal;
import java.util.Map;

/** {@code media} es {@code null} si no hay ninguna reseña sincronizada todavia; {@code
 * distribucion} siempre trae las 5 claves (1..5), a 0 las que no tengan ninguna reseña -
 * back/fase4-2-back.md. */
public record ResumenValoracionesDTO(BigDecimal media, int total, Map<Integer, Integer> distribucion) {
}
