package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.dto.ProveedorCreateDTO;
import com.gestionrestaurante.back.dto.ProveedorDTO;
import com.gestionrestaurante.back.entity.Proveedor;
import com.gestionrestaurante.back.entity.TipoProveedor;
import com.gestionrestaurante.back.exception.ProveedorNotFoundException;
import com.gestionrestaurante.back.repository.ProveedorRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fase 2.1 (Facturas): CRUD minimo de proveedores/suministradores. La "gestion de
 * proveedores" es una capacidad de soporte del alta de facturas (alta rapida embebida en el
 * front), no una pantalla CRUD independiente - back/fase2-1-back.md.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;

    public List<ProveedorDTO> listar(TipoProveedor tipoOpcional) {
        List<Proveedor> proveedores = tipoOpcional != null
                ? proveedorRepository.findByTipoOrderByNombreAsc(tipoOpcional)
                : proveedorRepository.findAllByOrderByNombreAsc();
        return proveedores.stream().map(ProveedorService::toDTO).toList();
    }

    @Transactional
    public ProveedorDTO crear(ProveedorCreateDTO request) {
        Proveedor proveedor = new Proveedor();
        proveedor.setNombre(request.nombre());
        proveedor.setCif(request.cif());
        proveedor.setEmailContacto(request.emailContacto());
        proveedor.setTelefono(request.telefono());
        proveedor.setTipo(request.tipo());
        LocalDateTime ahora = LocalDateTime.now();
        proveedor.setCreatedAt(ahora);
        proveedor.setUpdatedAt(ahora);
        return toDTO(proveedorRepository.save(proveedor));
    }

    Proveedor obtenerOrThrow(Long proveedorId) {
        return proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new ProveedorNotFoundException(proveedorId));
    }

    private static ProveedorDTO toDTO(Proveedor proveedor) {
        return new ProveedorDTO(proveedor.getId(), proveedor.getNombre(), proveedor.getCif(),
                proveedor.getEmailContacto(), proveedor.getTelefono(), proveedor.getTipo());
    }
}
