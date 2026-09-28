package com.gestionrestaurante.back.controller;

import com.gestionrestaurante.back.Wuris;
import com.gestionrestaurante.back.dto.LoginRequestDTO;
import com.gestionrestaurante.back.dto.LoginResponseDTO;
import com.gestionrestaurante.back.dto.RestablecerPasswordRequestDTO;
import com.gestionrestaurante.back.dto.SolicitarRecuperacionRequestDTO;
import com.gestionrestaurante.back.service.AuthService;
import com.gestionrestaurante.back.service.RecuperacionPasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Todos los endpoints bajo /api/auth son publicos (Wuris.Auth.PUBLIC_PATTERN, SecurityConfig) -
 * ninguno lleva @PreAuthorize, ni el login ni los de recuperacion de contraseña
 * (back/fase6-1-back.md).
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RecuperacionPasswordService recuperacionPasswordService;

    @PostMapping(Wuris.Auth.LOGIN)
    public LoginResponseDTO login(@Valid @RequestBody LoginRequestDTO request) {
        return authService.login(request);
    }

    @PostMapping(Wuris.Auth.RECUPERACION_SOLICITAR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void solicitarRecuperacion(@Valid @RequestBody SolicitarRecuperacionRequestDTO request) {
        recuperacionPasswordService.solicitar(request.email());
    }

    @PostMapping(Wuris.Auth.RECUPERACION_RESTABLECER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecerPassword(@Valid @RequestBody RestablecerPasswordRequestDTO request) {
        recuperacionPasswordService.restablecer(request.token(), request.password());
    }
}
