package com.gestionrestaurante.back.security;

import com.gestionrestaurante.back.service.JwtService;
import com.gestionrestaurante.back.service.PermisoService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final PermisoService permisoService;

    /**
     * Los endpoints que devuelven StreamingResponseBody (p.ej. GET /api/facturas/exportar)
     * fuerzan un redespacho ASYNC de Servlet: Spring Security vuelve a pasar por
     * AuthorizationFilter en ese redespacho, pero OncePerRequestFilter se salta el ASYNC por
     * defecto - sin este override, el SecurityContext llega vacio a esa segunda pasada y
     * Authorization deniega el acceso *despues* de que la respuesta ya empezo a enviarse
     * (200 chunked), lo que el navegador ve como ERR_INCOMPLETE_CHUNKED_ENCODING.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parseClaims(token);
                String email = claims.getSubject();
                String tipoUsuarioCodigo = claims.get("tipoUsuarioCodigo", String.class);
                Long empleadoId = claims.get("empleadoId", Long.class);

                var authorities = permisoService.obtenerAutoridades(tipoUsuarioCodigo);
                var authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);
                // El empleadoId va en los "details" (no en el principal) para que
                // Authentication#getName() siga devolviendo el email sin cambios; los
                // controllers que necesitan "solo mis propios datos" (fichajes, vacaciones)
                // lo leen via CurrentUser.empleadoId(authentication).
                authentication.setDetails(empleadoId);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
