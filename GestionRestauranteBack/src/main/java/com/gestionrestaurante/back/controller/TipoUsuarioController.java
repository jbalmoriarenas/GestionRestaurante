package com.gestionrestaurante.back.controller;

import com.gestionrestaurante.back.Wuris;
import com.gestionrestaurante.back.dto.TipoUsuarioDTO;
import com.gestionrestaurante.back.repository.TipoUsuarioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TipoUsuarioController {

    private final TipoUsuarioRepository tipoUsuarioRepository;

    @GetMapping(Wuris.TiposUsuarios.BASE)
    @PreAuthorize("hasAuthority('EMPLEADOS:EDICION')")
    public List<TipoUsuarioDTO> listar() {
        return tipoUsuarioRepository.findAllByOrderByNombreAsc().stream()
                .map(TipoUsuarioDTO::from)
                .toList();
    }
}
