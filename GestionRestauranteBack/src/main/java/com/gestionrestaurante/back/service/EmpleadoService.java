package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.dto.EmpleadoCreateDTO;
import com.gestionrestaurante.back.dto.EmpleadoResponseDTO;
import com.gestionrestaurante.back.dto.EmpleadoUpdateDTO;
import com.gestionrestaurante.back.entity.Empleado;
import com.gestionrestaurante.back.entity.TipoUsuario;
import com.gestionrestaurante.back.exception.DniYaExisteException;
import com.gestionrestaurante.back.exception.EmailYaExisteException;
import com.gestionrestaurante.back.exception.EmpleadoNotFoundException;
import com.gestionrestaurante.back.exception.TipoUsuarioNotFoundException;
import com.gestionrestaurante.back.mapper.EmpleadoMapper;
import com.gestionrestaurante.back.repository.EmpleadoRepository;
import com.gestionrestaurante.back.repository.TipoUsuarioRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmpleadoService {

    private final EmpleadoRepository empleadoRepository;
    private final TipoUsuarioRepository tipoUsuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public Page<EmpleadoResponseDTO> listar(Boolean activo, Pageable pageable) {
        Page<Empleado> pagina = (activo != null)
                ? empleadoRepository.findByActivo(activo, pageable)
                : empleadoRepository.findAll(pageable);
        return pagina.map(EmpleadoMapper::toResponse);
    }

    public EmpleadoResponseDTO obtener(Long id) {
        return EmpleadoMapper.toResponse(buscarOrThrow(id));
    }

    @Transactional
    public EmpleadoResponseDTO crear(EmpleadoCreateDTO request) {
        if (empleadoRepository.existsByEmail(request.email())) {
            throw new EmailYaExisteException(request.email());
        }
        if (empleadoRepository.existsByDniNie(request.dniNie())) {
            throw new DniYaExisteException(request.dniNie());
        }
        TipoUsuario tipoUsuario = buscarTipoUsuarioOrThrow(request.tipoUsuarioCodigo());
        Empleado empleado = EmpleadoMapper.toEntity(request, tipoUsuario, passwordEncoder.encode(request.password()));
        return EmpleadoMapper.toResponse(empleadoRepository.save(empleado));
    }

    @Transactional
    public EmpleadoResponseDTO actualizar(Long id, EmpleadoUpdateDTO request) {
        Empleado empleado = buscarOrThrow(id);

        if (!empleado.getEmail().equalsIgnoreCase(request.email()) && empleadoRepository.existsByEmail(request.email())) {
            throw new EmailYaExisteException(request.email());
        }
        if (!empleado.getDniNie().equalsIgnoreCase(request.dniNie()) && empleadoRepository.existsByDniNie(request.dniNie())) {
            throw new DniYaExisteException(request.dniNie());
        }

        TipoUsuario tipoUsuario = buscarTipoUsuarioOrThrow(request.tipoUsuarioCodigo());
        EmpleadoMapper.actualizarEntidad(empleado, request, tipoUsuario);
        return EmpleadoMapper.toResponse(empleadoRepository.save(empleado));
    }

    @Transactional
    public void darDeBaja(Long id) {
        Empleado empleado = buscarOrThrow(id);
        if (!empleado.isActivo()) {
            // Idempotente: si ya estaba de baja, no se toca de nuevo fechaBaja para no
            // perder la fecha original por una segunda llamada (p.ej. un doble click o un
            // reintento de red).
            return;
        }
        empleado.setActivo(false);
        empleado.setFechaBaja(LocalDate.now());
        empleadoRepository.save(empleado);
    }

    @Transactional
    public void resetearPassword(Long id, String nuevaPassword) {
        Empleado empleado = buscarOrThrow(id);
        empleado.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        empleadoRepository.save(empleado);
    }

    private Empleado buscarOrThrow(Long id) {
        return empleadoRepository.findById(id).orElseThrow(() -> new EmpleadoNotFoundException(id));
    }

    private TipoUsuario buscarTipoUsuarioOrThrow(String codigo) {
        return tipoUsuarioRepository.findByCodigo(codigo).orElseThrow(() -> new TipoUsuarioNotFoundException(codigo));
    }
}
