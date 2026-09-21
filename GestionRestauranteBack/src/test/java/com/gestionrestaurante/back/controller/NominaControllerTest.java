package com.gestionrestaurante.back.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gestionrestaurante.back.dto.NominaListadoDTO;
import com.gestionrestaurante.back.dto.NominaResponseDTO;
import com.gestionrestaurante.back.entity.EstadoNomina;
import com.gestionrestaurante.back.exception.ArchivoNoValidoException;
import com.gestionrestaurante.back.exception.NominaNotFoundException;
import com.gestionrestaurante.back.security.JwtAuthFilter;
import com.gestionrestaurante.back.service.NominaService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Mismo patron que VacacionesControllerTest para excluir la cadena real de seguridad
 * (JwtAuthFilter), con una diferencia: aqui SI se comprueba el 403 de @PreAuthorize, asi que
 * hace falta activar @EnableMethodSecurity explicitamente (un @WebMvcTest no carga
 * SecurityConfig -que trae JwtAuthFilter/JwtService/etc. como dependencias- asi que se declara
 * aqui, en un @TestConfiguration aislado). El interceptor de @PreAuthorize se ejecuta como AOP
 * alrededor del metodo del controller, no como filtro HTTP, asi que basta con rellenar
 * SecurityContextHolder directamente antes de cada peticion (un simple .principal(...) en el
 * MockHttpServletRequest NO sirve: @PreAuthorize lee del SecurityContext, no del Principal de
 * la request) - y limpiarlo despues, porque JUnit reutiliza el mismo hilo entre tests.
 */
@WebMvcTest(
        controllers = NominaController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class NominaControllerTest {

    private static final Long ADMIN_ID = 1L;

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NominaService nominaService;

    @AfterEach
    void limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Rellena SecurityContextHolder (lo que lee @PreAuthorize, via AOP alrededor del metodo)
     * y devuelve la Authentication para encadenarla con .principal(...) en la request (lo que
     * resuelve el parametro "Authentication authentication" del propio metodo del controller,
     * via request.getUserPrincipal() - dos mecanismos distintos, ninguno sustituye al otro).
     */
    private static Authentication autenticarComoAdmin() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@test.com", null, List.of(new SimpleGrantedAuthority("NOMINAS:EDICION")));
        authentication.setDetails(ADMIN_ID);
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
    void listar_conTokenDeEmpleadoResponde403() throws Exception {
        autenticarComoEmpleado();

        mockMvc.perform(get("/api/nominas"))
                .andExpect(status().isForbidden());
    }

    @Test
    void subir_conTokenDeEmpleadoResponde403() throws Exception {
        autenticarComoEmpleado();
        MockMultipartFile archivo = new MockMultipartFile("archivo", "nomina.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/nominas")
                        .file(archivo)
                        .param("empleadoId", "5")
                        .param("periodoMes", "1")
                        .param("periodoAnio", "2026"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listar_devuelveLaPaginaDelServicio() throws Exception {
        autenticarComoAdmin();
        when(nominaService.listar(any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(new NominaListadoDTO(
                        5L, "Ana Lopez", "ana@test.com", 1, 2026, "PENDIENTE", null, null, null, null))));

        mockMvc.perform(get("/api/nominas")
                        .param("periodoMes", "1")
                        .param("periodoAnio", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].estado").value("PENDIENTE"));
    }

    @Test
    void subir_archivoNoValidoResponde400() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "nomina.txt", "text/plain", new byte[]{1, 2, 3});
        when(nominaService.subir(eq(5L), eq(1), eq(2026), any(), eq(ADMIN_ID)))
                .thenThrow(new ArchivoNoValidoException());

        mockMvc.perform(multipart("/api/nominas")
                        .file(archivo)
                        .param("empleadoId", "5")
                        .param("periodoMes", "1")
                        .param("periodoAnio", "2026")
                        .principal(autenticarComoAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void subir_archivoValidoDevuelveLaNominaAdjuntada() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "nomina.pdf", "application/pdf", new byte[]{1, 2, 3});
        when(nominaService.subir(eq(5L), eq(1), eq(2026), any(), eq(ADMIN_ID)))
                .thenReturn(new NominaResponseDTO(
                        10L, 5L, 1, 2026, EstadoNomina.ADJUNTADA, "nomina.pdf", LocalDateTime.now(), null));

        mockMvc.perform(multipart("/api/nominas")
                        .file(archivo)
                        .param("empleadoId", "5")
                        .param("periodoMes", "1")
                        .param("periodoAnio", "2026")
                        .principal(autenticarComoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ADJUNTADA"));
    }

    @Test
    void descargar_nominaInexistenteResponde404() throws Exception {
        autenticarComoAdmin();
        when(nominaService.descargar(99L)).thenThrow(new NominaNotFoundException(99L));

        mockMvc.perform(get("/api/nominas/99/descargar"))
                .andExpect(status().isNotFound());
    }

    @Test
    void enviar_conTokenDeEmpleadoResponde403() throws Exception {
        autenticarComoEmpleado();

        mockMvc.perform(post("/api/nominas/10/enviar"))
                .andExpect(status().isForbidden());
    }
}
