package com.gestionrestaurante.back.controller;

import com.gestionrestaurante.back.Wuris;
import com.gestionrestaurante.back.dto.ConfiguracionEmpresaLogoDTO;
import com.gestionrestaurante.back.dto.ConfiguracionEmpresaRequestDTO;
import com.gestionrestaurante.back.dto.ConfiguracionEmpresaResponseDTO;
import com.gestionrestaurante.back.dto.ConfiguracionUbicacionRequestDTO;
import com.gestionrestaurante.back.dto.ConfiguracionUbicacionResponseDTO;
import com.gestionrestaurante.back.dto.ConfiguracionVacacionesRequestDTO;
import com.gestionrestaurante.back.dto.ConfiguracionVacacionesResponseDTO;
import com.gestionrestaurante.back.entity.ConfiguracionEmpresa;
import com.gestionrestaurante.back.security.CurrentUser;
import com.gestionrestaurante.back.service.ConfiguracionEmpresaService;
import com.gestionrestaurante.back.service.ConfiguracionUbicacionService;
import com.gestionrestaurante.back.service.ConfiguracionVacacionesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Editar la ubicacion/radio de la geovalla es una capacidad de Admin, no del modulo de
 * fichajes en si (mismo razonamiento de autorizacion que FichajeAdminController, ver
 * back/fase1-3-back.md). La seccion de datos de empresa (back/fase5-1-back.md) es distinta:
 * su lectura (nombre/logo) esta abierta a cualquier usuario autenticado -sin @PreAuthorize-
 * porque se muestra en la cabecera de toda la app, no solo dentro de un panel de Admin; solo
 * la escritura sigue restringida a EMPLEADOS:EDICION, igual que el resto del controller.
 * No conoce FileStorageService directamente (revision de codigo): la resolucion del recurso
 * del logo vive en ConfiguracionEmpresaService, mismo patron que FacturaService.descargarArchivo.
 */
@RestController
@RequiredArgsConstructor
public class ConfiguracionController {

    private final ConfiguracionUbicacionService configuracionUbicacionService;
    private final ConfiguracionVacacionesService configuracionVacacionesService;
    private final ConfiguracionEmpresaService configuracionEmpresaService;

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

    @GetMapping(Wuris.Configuracion.EMPRESA)
    public ConfiguracionEmpresaResponseDTO obtenerEmpresa() {
        return ConfiguracionEmpresaResponseDTO.from(configuracionEmpresaService.obtener(), Wuris.Configuracion.EMPRESA_LOGO);
    }

    @PutMapping(value = Wuris.Configuracion.EMPRESA, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")
    public ConfiguracionEmpresaResponseDTO actualizarEmpresa(
            @Valid @ModelAttribute ConfiguracionEmpresaRequestDTO request, Authentication authentication) {
        ConfiguracionEmpresa configuracion = configuracionEmpresaService.actualizar(
                request.nombreEmpresa(), request.logo(), request.emailRemitente(), CurrentUser.empleadoId(authentication));
        return ConfiguracionEmpresaResponseDTO.from(configuracion, Wuris.Configuracion.EMPRESA_LOGO);
    }

    @GetMapping(Wuris.Configuracion.EMPRESA_LOGO)
    public ResponseEntity<Resource> obtenerLogoEmpresa() {
        ConfiguracionEmpresaLogoDTO logo = configuracionEmpresaService.obtenerLogo();
        return ResponseEntity.ok().contentType(logo.mediaType()).body(logo.recurso());
    }
}
