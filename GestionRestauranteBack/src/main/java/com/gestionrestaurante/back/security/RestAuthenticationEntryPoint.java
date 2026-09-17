package com.gestionrestaurante.back.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Se dispara antes de llegar a ningun controller (filtro de seguridad), por eso no lo
 * cubre el @RestControllerAdvice de GlobalExceptionHandler. Escribe el mismo formato ApiError
 * a mano para que el frontend tenga un unico shape de error en toda la app.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        writeError(response, request, HttpStatus.UNAUTHORIZED, "No autenticado o token invalido/expirado");
    }

    static void writeError(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        String json = """
                {"timestamp":"%s","status":%d,"error":"%s","message":"%s","path":"%s"}""".formatted(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        response.getWriter().write(json);
    }
}
