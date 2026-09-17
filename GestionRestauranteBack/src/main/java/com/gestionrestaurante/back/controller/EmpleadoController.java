package com.gestionrestaurante.back.controller;

import com.gestionrestaurante.back.Wuris;
import com.gestionrestaurante.back.dto.EmpleadoCreateDTO;
import com.gestionrestaurante.back.dto.EmpleadoResponseDTO;
import com.gestionrestaurante.back.dto.EmpleadoUpdateDTO;
import com.gestionrestaurante.back.dto.PasswordResetDTO;
import com.gestionrestaurante.back.service.EmpleadoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EmpleadoController {

    private final EmpleadoService empleadoService;

    @GetMapping(Wuris.Empleados.BASE)
    @PreAuthorize("hasAnyAuthority('EMPLEADOS:EDICION', 'EMPLEADOS:VISUALIZACION')")
    public Page<EmpleadoResponseDTO> listar(
            @RequestParam(required = false) Boolean activo,
            @PageableDefault(size = 20, sort = "apellidos") Pageable pageable) {
        return empleadoService.listar(activo, pageable);
    }

    @GetMapping(Wuris.Empleados.BY_ID)
    @PreAuthorize("hasAnyAuthority('EMPLEADOS:EDICION', 'EMPLEADOS:VISUALIZACION')")
    public EmpleadoResponseDTO obtener(@PathVariable Long id) {
        return empleadoService.obtener(id);
    }

    @PostMapping(Wuris.Empleados.BASE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")
    public EmpleadoResponseDTO crear(@Valid @RequestBody EmpleadoCreateDTO request) {
        return empleadoService.crear(request);
    }

    @PutMapping(Wuris.Empleados.BY_ID)
    @PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")
    public EmpleadoResponseDTO actualizar(@PathVariable Long id, @Valid @RequestBody EmpleadoUpdateDTO request) {
        return empleadoService.actualizar(id, request);
    }

    @PatchMapping(Wuris.Empleados.BAJA)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")
    public void darDeBaja(@PathVariable Long id) {
        empleadoService.darDeBaja(id);
    }

    @PatchMapping(Wuris.Empleados.PASSWORD)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")
    public void resetearPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetDTO request) {
        empleadoService.resetearPassword(id, request.password());
    }
}
