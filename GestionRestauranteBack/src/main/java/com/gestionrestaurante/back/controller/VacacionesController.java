package com.gestionrestaurante.back.controller;

import com.gestionrestaurante.back.Wuris;
import com.gestionrestaurante.back.dto.DiaCalendarioDTO;
import com.gestionrestaurante.back.dto.ResumenVacacionesDTO;
import com.gestionrestaurante.back.dto.SolicitudVacacionesCreateDTO;
import com.gestionrestaurante.back.dto.SolicitudVacacionesResponseDTO;
import com.gestionrestaurante.back.security.CurrentUser;
import com.gestionrestaurante.back.service.VacacionesService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VacacionesController {

    private static final int ANIO_MINIMO = 2000;
    private static final int ANIO_MAXIMO = 2100;

    private final VacacionesService vacacionesService;

    @GetMapping(Wuris.Vacaciones.RESUMEN)
    @PreAuthorize("hasAuthority('VACACIONES:EDICION')")
    public ResumenVacacionesDTO obtenerResumen(
            Authentication authentication, @RequestParam @Min(ANIO_MINIMO) @Max(ANIO_MAXIMO) int anio) {
        return vacacionesService.obtenerResumenAnual(CurrentUser.empleadoId(authentication), anio);
    }

    @GetMapping(Wuris.Vacaciones.CALENDARIO)
    @PreAuthorize("hasAuthority('VACACIONES:EDICION')")
    public List<DiaCalendarioDTO> obtenerCalendario(
            Authentication authentication, @RequestParam @Min(ANIO_MINIMO) @Max(ANIO_MAXIMO) int anio) {
        return vacacionesService.obtenerCalendario(CurrentUser.empleadoId(authentication), anio);
    }

    @PostMapping(Wuris.Vacaciones.SOLICITUDES)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VACACIONES:EDICION')")
    public SolicitudVacacionesResponseDTO solicitar(
            Authentication authentication, @Valid @RequestBody SolicitudVacacionesCreateDTO request) {
        return vacacionesService.solicitar(
                CurrentUser.empleadoId(authentication), request.fechaInicio(), request.fechaFin());
    }

    @GetMapping(Wuris.Vacaciones.SOLICITUDES)
    @PreAuthorize("hasAuthority('VACACIONES:EDICION')")
    public Page<SolicitudVacacionesResponseDTO> listarMisSolicitudes(
            Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        return vacacionesService.listarMisSolicitudes(CurrentUser.empleadoId(authentication), pageable);
    }

    @DeleteMapping(Wuris.Vacaciones.SOLICITUD_BY_ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('VACACIONES:EDICION')")
    public void cancelar(Authentication authentication, @PathVariable Long id) {
        vacacionesService.cancelar(CurrentUser.empleadoId(authentication), id);
    }
}
