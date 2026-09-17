package com.gestionrestaurante.back.mapper;

import com.gestionrestaurante.back.dto.EmpleadoCreateDTO;
import com.gestionrestaurante.back.dto.EmpleadoResponseDTO;
import com.gestionrestaurante.back.dto.EmpleadoUpdateDTO;
import com.gestionrestaurante.back.dto.TipoUsuarioDTO;
import com.gestionrestaurante.back.entity.Empleado;
import com.gestionrestaurante.back.entity.TipoUsuario;

public final class EmpleadoMapper {

    private EmpleadoMapper() {
    }

    public static EmpleadoResponseDTO toResponse(Empleado empleado) {
        return new EmpleadoResponseDTO(
                empleado.getId(),
                empleado.getNombre(),
                empleado.getApellidos(),
                empleado.getEmail(),
                empleado.getDniNie(),
                empleado.getTelefono(),
                empleado.getPuesto(),
                empleado.getFechaAlta(),
                empleado.getFechaBaja(),
                empleado.isActivo(),
                TipoUsuarioDTO.from(empleado.getTipoUsuario()));
    }

    public static Empleado toEntity(EmpleadoCreateDTO dto, TipoUsuario tipoUsuario, String passwordHash) {
        Empleado empleado = new Empleado();
        empleado.setNombre(dto.nombre());
        empleado.setApellidos(dto.apellidos());
        empleado.setEmail(dto.email());
        empleado.setDniNie(dto.dniNie());
        empleado.setTelefono(dto.telefono());
        empleado.setPuesto(dto.puesto());
        empleado.setFechaAlta(dto.fechaAlta());
        empleado.setActivo(true);
        empleado.setTipoUsuario(tipoUsuario);
        empleado.setPasswordHash(passwordHash);
        return empleado;
    }

    public static void actualizarEntidad(Empleado empleado, EmpleadoUpdateDTO dto, TipoUsuario tipoUsuario) {
        empleado.setNombre(dto.nombre());
        empleado.setApellidos(dto.apellidos());
        empleado.setEmail(dto.email());
        empleado.setDniNie(dto.dniNie());
        empleado.setTelefono(dto.telefono());
        empleado.setPuesto(dto.puesto());
        empleado.setTipoUsuario(tipoUsuario);
    }
}
