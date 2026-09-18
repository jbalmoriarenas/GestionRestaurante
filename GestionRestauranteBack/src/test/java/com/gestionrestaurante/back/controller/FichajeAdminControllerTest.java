package com.gestionrestaurante.back.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gestionrestaurante.back.dto.FichajeResponseDTO;
import com.gestionrestaurante.back.exception.FichajeNotFoundException;
import com.gestionrestaurante.back.exception.RangoFechasInvalidoException;
import com.gestionrestaurante.back.security.JwtAuthFilter;
import com.gestionrestaurante.back.service.FichajeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La cadena de seguridad real (JWT/CORS, y por tanto la comprobacion de @PreAuthorize) esta
 * fuera de alcance de este test, igual que en VacacionesControllerTest: se fija el
 * Authentication directamente como principal de la peticion, sin depender de ningun filtro
 * de seguridad, para poder ejercitar CurrentUser#empleadoId y el mapeo de excepciones de
 * negocio igual que en produccion.
 */
@WebMvcTest(
        controllers = FichajeAdminController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class FichajeAdminControllerTest {

    private static final Long ADMIN_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FichajeService fichajeService;

    private static Authentication autenticacionDeAdmin() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@test.com", null, List.of(new SimpleGrantedAuthority("EMPLEADOS:EDICION")));
        authentication.setDetails(ADMIN_ID);
        return authentication;
    }

    @Test
    void listarComoAdmin_devuelveLaPaginaDelServicio() throws Exception {
        FichajeResponseDTO fichaje = new FichajeResponseDTO(
                1L, LocalDate.of(2026, 1, 5), LocalDateTime.of(2026, 1, 5, 9, 0),
                LocalDateTime.of(2026, 1, 5, 17, 0), BigDecimal.valueOf(8), null);
        when(fichajeService.listarComoAdmin(eq(2L), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(fichaje)));

        mockMvc.perform(get("/api/fichajes/admin")
                        .principal(autenticacionDeAdmin())
                        .param("empleadoId", "2")
                        .param("desde", "2026-01-01")
                        .param("hasta", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    void corregir_horaSalidaAntesDeEntradaResponde400() throws Exception {
        when(fichajeService.corregir(eq(10L), eq(ADMIN_ID), any(), any(), any()))
                .thenThrow(new RangoFechasInvalidoException(
                        "La hora de salida debe ser igual o posterior a la hora de entrada"));

        mockMvc.perform(put("/api/fichajes/admin/10")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaEntrada\":\"2026-01-05T17:00:00\",\"horaSalida\":\"2026-01-05T09:00:00\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void corregir_fichajeInexistenteResponde404() throws Exception {
        Mockito.doThrow(new FichajeNotFoundException(10L))
                .when(fichajeService).corregir(eq(10L), eq(ADMIN_ID), any(), any(), any());

        mockMvc.perform(put("/api/fichajes/admin/10")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaEntrada\":\"2026-01-05T09:00:00\",\"horaSalida\":\"2026-01-05T17:00:00\"}"))
                .andExpect(status().isNotFound());
    }
}
