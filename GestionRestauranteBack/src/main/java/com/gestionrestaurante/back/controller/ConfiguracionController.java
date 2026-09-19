package com.gestionrestaurante.back.controller;

import com.gestionrestaurante.back.Wuris;
import com.gestionrestaurante.back.dto.ConfiguracionUbicacionRequestDTO;
import com.gestionrestaurante.back.dto.ConfiguracionUbicacionResponseDTO;
import com.gestionrestaurante.back.dto.ConfiguracionVacacionesRequestDTO;
import com.gestionrestaurante.back.dto.ConfiguracionVacacionesResponseDTO;
import com.gestionrestaurante.back.security.CurrentUser;
import com.gestionrestaurante.back.service.ConfiguracionUbicacionService;
import com.gestionrestaurante.back.service.ConfiguracionVacacionesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Editar la ubicacion/radio de la geovalla es una capacidad de Admin, no del modulo de
 * fichajes en si (mismo razonamiento de autorizacion que FichajeAdminController, ver
 * back/fase1-3-back.md).
 */
@RestController
@RequiredArgsConstructor
public class ConfiguracionController {

    private final ConfiguracionUbicacionService configuracionUbicacionService;
    private final ConfiguracionVacacionesService configuracionVacacionesService;

    @GetMapping(Wuris.Configuracion.UBICACION)
    @PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")
    public ConfiguracionUbicacionResponseDTO obtenerUbicacion() {
        return ConfiguracionUbicacionResponseDTO.from(configuracionUbicacionService.obtener());
    }

    @PutMapping(Wuris.Configuracion.UBICACION)
    @PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")
    public ConfiguracionUbicacionResponseDTO actualizarUbicacion(
            @Valid @RequestBody ConfiguracionUbicacionRequestDTO request, Authentication authentication) {
        return ConfiguracionUbicacionResponseDTO.from(configuracionUbicacionService.actualizar(
                request.lat(), request.lng(), request.radioMetros(), CurrentUser.empleadoId(authentication)));
    }

    @GetMapping(Wuris.Configuracion.VACACIONES)
    @PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")
    public ConfiguracionVacacionesResponseDTO obtenerVacaciones() {
        return ConfiguracionVacacionesResponseDTO.from(configuracionVacacionesService.obtener());
    }

    @PutMapping(Wuris.Configuracion.VACACIONES)
    @PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")
    public ConfiguracionVacacionesResponseDTO actualizarVacaciones(
            @Valid @RequestBody ConfiguracionVacacionesRequestDTO request, Authentication authentication) {
        return ConfiguracionVacacionesResponseDTO.from(configuracionVacacionesService.actualizar(
                request.diasAnualesDefecto(), CurrentUser.empleadoId(authentication)));
    }
}
