package com.gestionrestaurante.back.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gestionrestaurante.back.dto.ConexionEstadoDTO;
import com.gestionrestaurante.back.security.JwtAuthFilter;
import com.gestionrestaurante.back.service.ConexionGoogleService;
import com.gestionrestaurante.back.service.ValoracionService;
import com.gestionrestaurante.back.service.ValoracionSincronizacionService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Mismo patron que FacturaControllerTest (back/fase4-1-back.md, back/fase4-2-back.md). */
@WebMvcTest(
        controllers = ValoracionController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class ValoracionControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConexionGoogleService conexionGoogleService;

    @MockitoBean
    private ValoracionSincronizacionService sincronizacionService;

    @MockitoBean
    private ValoracionService valoracionService;

    @AfterEach
    void limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext();
    }

    private static Authentication autenticarComoAdmin() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@test.com", null, List.of(new SimpleGrantedAuthority("VALORACIONES:EDICION")));
        authentication.setDetails(1L);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    private static Authentication autenticarComoEmpleado() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "empleado@test.com", null, List.of(new SimpleGrantedAuthority("FICHAJES:EDICION")));
        authentication.setDetails(2L);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    @Test
    void obtenerEstadoConexion_conTokenDeEmpleadoResponde403() throws Exception {
        autenticarComoEmpleado();

        mockMvc.perform(get("/api/valoraciones/conexion")).andExpect(status().isForbidden());
    }

    @Test
    void obtenerEstadoConexion_sinConexionDevuelveDesconectado() throws Exception {
        autenticarComoAdmin();
        when(conexionGoogleService.obtenerEstado()).thenReturn(ConexionEstadoDTO.desconectado());

        mockMvc.perform(get("/api/valoraciones/conexion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conectado").value(false))
                .andExpect(jsonPath("$.nombreUbicacion").doesNotExist());
    }

    @Test
    void obtenerUrlConexion_conTokenDeEmpleadoResponde403() throws Exception {
        autenticarComoEmpleado();

        mockMvc.perform(get("/api/valoraciones/conexion/url")).andExpect(status().isForbidden());
    }

    @Test
    void desconectar_conTokenDeEmpleadoResponde403() throws Exception {
        autenticarComoEmpleado();

        mockMvc.perform(delete("/api/valoraciones/conexion")).andExpect(status().isForbidden());
    }

    @Test
    void sincronizar_conTokenDeEmpleadoResponde403() throws Exception {
        autenticarComoEmpleado();

        mockMvc.perform(post("/api/valoraciones/sincronizar")).andExpect(status().isForbidden());
    }

    @Test
    void listar_conTokenDeEmpleadoResponde403() throws Exception {
        autenticarComoEmpleado();

        mockMvc.perform(get("/api/valoraciones")).andExpect(status().isForbidden());
    }

    @Test
    void listar_llamaAlServicioConLaPuntuacionFiltrada() throws Exception {
        autenticarComoAdmin();
        when(valoracionService.listar(any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/api/valoraciones").param("puntuacion", "5"))
                .andExpect(status().isOk());
    }

    @Test
    void resumen_conTokenDeEmpleadoResponde403() throws Exception {
        autenticarComoEmpleado();

        mockMvc.perform(get("/api/valoraciones/resumen")).andExpect(status().isForbidden());
    }

    @Test
    void callback_esAccesibleSinAutenticacion() throws Exception {
        // Sin autenticarComoAdmin()/Empleado(): Google redirige aqui sin ningun JWT
        // (back/fase4-1-back.md, "Decisiones tecnicas") - no debe responder 401/403.
        mockMvc.perform(get("/api/valoraciones/conexion/callback").param("code", "abc").param("state", "xyz"))
                .andExpect(status().isFound());
    }

    @Test
    void callback_conStateInvalidoRedirigeConCodigoDeErrorEstable() throws Exception {
        org.mockito.Mockito.doThrow(new com.gestionrestaurante.back.exception.EstadoOAuthInvalidoException())
                .when(conexionGoogleService).completarConexion(any(), any());

        mockMvc.perform(get("/api/valoraciones/conexion/callback").param("code", "abc").param("state", "no-existe"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error=estado_invalido")));
    }

    @Test
    void callback_conConsentimientoCanceladoPorElAdminRedirigeSinLlamarAlServicio() throws Exception {
        // Google redirige asi (sin "code") cuando el Admin pulsa "Cancelar" en la pantalla de
        // consentimiento - no debe ser un binding fallido de Spring (400 en crudo).
        mockMvc.perform(get("/api/valoraciones/conexion/callback").param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error=access_denied")));

        org.mockito.Mockito.verify(conexionGoogleService, org.mockito.Mockito.never()).completarConexion(any(), any());
    }

    @Test
    void callback_sinCodeNiStateNiErrorRedirigeConSolicitudIncompleta() throws Exception {
        mockMvc.perform(get("/api/valoraciones/conexion/callback"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error=solicitud_incompleta")));
    }
}
