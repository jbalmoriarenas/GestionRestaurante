package com.gestionrestaurante.back.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmpleadoResponseDTO(
        Long id,
        String nombre,
        String apellidos,
        String email,
        String dniNie,
        String telefono,
        String puesto,
        LocalDate fechaAlta,
        LocalDate fechaBaja,
        boolean activo,
        TipoUsuarioDTO tipoUsuario,
        BigDecimal horasJornadaDiaria,
        Integer diasVacacionesAnuales
) {
}
