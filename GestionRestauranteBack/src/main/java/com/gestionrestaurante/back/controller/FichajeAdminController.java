package com.gestionrestaurante.back.controller;

import com.gestionrestaurante.back.Wuris;
import com.gestionrestaurante.back.dto.FichajeCorreccionRequestDTO;
import com.gestionrestaurante.back.dto.FichajeResponseDTO;
import com.gestionrestaurante.back.dto.FichajeResumenDiaDTO;
import com.gestionrestaurante.back.dto.FichajeResumenMesDTO;
import com.gestionrestaurante.back.dto.FichajeResumenSemanaDTO;
import com.gestionrestaurante.back.security.CurrentUser;
import com.gestionrestaurante.back.service.FichajeService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Clase separada de FichajeController (fase1-1-back.md) a proposito: estos endpoints se
 * protegen con EMPLEADOS:EDICION (ver "Analisis y decision de autorizacion" en
 * back/fase1-3-back.md), no con FICHAJES:EDICION que tambien tiene un EMPLEADO normal.
 * Mezclar ambas anotaciones en un mismo controller invitaria a copiar la equivocada.
 */
@RestController
@RequiredArgsConstructor
public class FichajeAdminController {

    private final FichajeService fichajeService;

    @GetMapping(Wuris.Fichajes.ADMIN)
    @PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")
    public Page<FichajeResponseDTO> listarComoAdmin(
            @RequestParam(required = false) Long empleadoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(size = 20) Pageable pageable) {
        return fichajeService.listarComoAdmin(empleadoId, desde, hasta, pageable);
    }

    @GetMapping(Wuris.Fichajes.ADMIN_RESUMEN_DIARIO)
    @PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")
    public List<FichajeResumenDiaDTO> obtenerResumenDiario(
            @RequestParam Long empleadoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return fichajeService.obtenerResumenDiario(empleadoId, desde, hasta);
    }

    @GetMapping(Wuris.Fichajes.ADMIN_RESUMEN_SEMANAL)
    @PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")
    public List<FichajeResumenSemanaDTO> obtenerResumenSemanal(
            @RequestParam Long empleadoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return fichajeService.obtenerResumenSemanal(empleadoId, desde, hasta);
    }

    @GetMapping(Wuris.Fichajes.ADMIN_RESUMEN_MENSUAL)
    @PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")
    public List<FichajeResumenMesDTO> obtenerResumenMensual(
            @RequestParam Long empleadoId, @RequestParam int anio) {
        return fichajeService.obtenerResumenMensual(empleadoId, anio);
    }

    @PutMapping(Wuris.Fichajes.ADMIN_BY_ID)
    @PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")
    public FichajeResponseDTO corregir(
            @PathVariable Long id, @Valid @RequestBody FichajeCorreccionRequestDTO request, Authentication authentication) {
        return fichajeService.corregir(
                id, CurrentUser.empleadoId(authentication), request.horaEntrada(), request.horaSalida(), request.observaciones());
    }
}
