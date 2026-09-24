package com.gestionrestaurante.back.controller;

import com.gestionrestaurante.back.Wuris;
import com.gestionrestaurante.back.dto.ProveedorCreateDTO;
import com.gestionrestaurante.back.dto.ProveedorDTO;
import com.gestionrestaurante.back.entity.TipoProveedor;
import com.gestionrestaurante.back.service.ProveedorService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProveedorController {

    private final ProveedorService proveedorService;

    @GetMapping(Wuris.Proveedores.BASE)
    @PreAuthorize("hasAuthority('FACTURAS:EDICION')")
    public List<ProveedorDTO> listar(@RequestParam(required = false) TipoProveedor tipo) {
        return proveedorService.listar(tipo);
    }

    @PostMapping(Wuris.Proveedores.BASE)
    @PreAuthorize("hasAuthority('FACTURAS:EDICION')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProveedorDTO crear(@Valid @RequestBody ProveedorCreateDTO request) {
        return proveedorService.crear(request);
    }
}
