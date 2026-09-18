package com.gestionrestaurante.back.controller;

import com.gestionrestaurante.back.Wuris;
import com.gestionrestaurante.back.dto.FichajeActualDTO;
import com.gestionrestaurante.back.dto.FichajeEntradaRequestDTO;
import com.gestionrestaurante.back.dto.FichajeResponseDTO;
import com.gestionrestaurante.back.dto.FichajeResumenHoyDTO;
import com.gestionrestaurante.back.security.CurrentUser;
import com.gestionrestaurante.back.service.FichajeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FichajeController {

    private final FichajeService fichajeService;

    @PostMapping(Wuris.Fichajes.ENTRADA)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('FICHAJES:EDICION')")
    public FichajeResponseDTO ficharEntrada(
            @Valid @RequestBody FichajeEntradaRequestDTO request, Authentication authentication) {
        return fichajeService.iniciar(CurrentUser.empleadoId(authentication), request.lat(), request.lng());
    }

    @PostMapping(Wuris.Fichajes.SALIDA)
    @PreAuthorize("hasAuthority('FICHAJES:EDICION')")
    public FichajeResponseDTO ficharSalida(Authentication authentication) {
        return fichajeService.finalizar(CurrentUser.empleadoId(authentication));
    }

    @GetMapping(Wuris.Fichajes.ACTUAL)
    @PreAuthorize("hasAuthority('FICHAJES:EDICION')")
    public FichajeActualDTO obtenerActual(Authentication authentication) {
        return fichajeService.obtenerActual(CurrentUser.empleadoId(authentication));
    }

    @GetMapping(Wuris.Fichajes.MIOS)
    @PreAuthorize("hasAuthority('FICHAJES:EDICION')")
    public Page<FichajeResponseDTO> listarMios(
            Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        return fichajeService.listarMios(CurrentUser.empleadoId(authentication), pageable);
    }

    @GetMapping(Wuris.Fichajes.MIOS_HOY)
    @PreAuthorize("hasAuthority('FICHAJES:EDICION')")
    public FichajeResumenHoyDTO obtenerResumenHoy(Authentication authentication) {
        return fichajeService.obtenerResumenHoy(CurrentUser.empleadoId(authentication));
    }
}
