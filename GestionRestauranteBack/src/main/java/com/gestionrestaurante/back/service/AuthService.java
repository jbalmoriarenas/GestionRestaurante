package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.dto.LoginRequestDTO;
import com.gestionrestaurante.back.dto.LoginResponseDTO;
import com.gestionrestaurante.back.dto.TipoUsuarioDTO;
import com.gestionrestaurante.back.entity.Empleado;
import com.gestionrestaurante.back.exception.CredencialesInvalidasException;
import com.gestionrestaurante.back.repository.EmpleadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final EmpleadoRepository empleadoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PermisoService permisoService;

    public LoginResponseDTO login(LoginRequestDTO request) {
        Empleado empleado = empleadoRepository.findByEmailWithTipoUsuario(request.email())
                .filter(Empleado::isActivo)
                .orElseThrow(CredencialesInvalidasException::new);

        if (!passwordEncoder.matches(request.password(), empleado.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }

        String tipoUsuarioCodigo = empleado.getTipoUsuario().getCodigo();
        String token = jwtService.generarToken(empleado);

        return new LoginResponseDTO(
                token,
                empleado.getId(),
                empleado.getNombre(),
                empleado.getApellidos(),
                TipoUsuarioDTO.from(empleado.getTipoUsuario()),
                permisoService.obtenerPermisos(tipoUsuarioCodigo));
    }
}
