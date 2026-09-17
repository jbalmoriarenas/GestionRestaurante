package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.dto.PermisoDTO;
import com.gestionrestaurante.back.repository.PermisoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Resuelve dinamicamente, a partir de la tabla `permisos`, tanto las authorities de
 * Spring Security ("VISTA:NIVEL") como el listado de permisos que se devuelve al front en el login.
 * Un tipo de usuario nuevo funciona automaticamente en cuanto tiene filas en `permisos`,
 * sin tocar ninguna anotacion @PreAuthorize.
 */
@Service
@RequiredArgsConstructor
public class PermisoService {

    private final PermisoRepository permisoRepository;

    public List<GrantedAuthority> obtenerAutoridades(String tipoUsuarioCodigo) {
        return permisoRepository.findByTipoUsuarioCodigo(tipoUsuarioCodigo).stream()
                .map(permiso -> (GrantedAuthority) new SimpleGrantedAuthority(
                        permiso.getVista().getCodigo() + ":" + permiso.getNivelAcceso().name()))
                .toList();
    }

    public List<PermisoDTO> obtenerPermisos(String tipoUsuarioCodigo) {
        return permisoRepository.findByTipoUsuarioCodigo(tipoUsuarioCodigo).stream()
                .map(permiso -> new PermisoDTO(permiso.getVista().getCodigo(), permiso.getNivelAcceso()))
                .toList();
    }
}
