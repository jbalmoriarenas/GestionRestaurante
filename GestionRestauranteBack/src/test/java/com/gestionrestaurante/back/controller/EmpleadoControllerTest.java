package com.gestionrestaurante.back.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gestionrestaurante.back.dto.EmpleadoResponseDTO;
import com.gestionrestaurante.back.dto.TipoUsuarioDTO;
import com.gestionrestaurante.back.exception.DiasVacacionesAnualesFueraDeRangoException;
import com.gestionrestaurante.back.exception.EmpleadoNotFoundException;
import com.gestionrestaurante.back.security.JwtAuthFilter;
import com.gestionrestaurante.back.service.EmpleadoService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Misma nota que en FichajeAdminControllerTest/ConfiguracionControllerTest: la cadena de
 * seguridad real (y por tanto @PreAuthorize) esta fuera de alcance de este test. Cubre solo
 * el endpoint de horario teorico (back/fase1-3-back.md); el resto de EmpleadoController
 * (alta, edicion completa, baja, password) no tiene tests todavia.
 */
@WebMvcTest(
        controllers = EmpleadoController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class EmpleadoControllerTest {

    private static final Long ADMIN_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmpleadoService empleadoService;

    private static Authentication autenticacionDeAdmin() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@test.com", null, List.of(new SimpleGrantedAuthority("EMPLEADOS:EDICION")));
        authentication.setDetails(ADMIN_ID);
        return authentication;
    }

    @Test
    void actualizarHorarioTeorico_conValorValidoDevuelveElEmpleadoActualizado() throws Exception {
        when(empleadoService.actualizarHorarioTeorico(eq(2L), eq(BigDecimal.valueOf(6.5))))
                .thenReturn(empleadoResponseConHoras(BigDecimal.valueOf(6.5)));

        mockMvc.perform(patch("/api/empleados/2/horario-teorico")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasJornadaDiaria\":6.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horasJornadaDiaria").value(6.5));
    }

    @Test
    void actualizarHorarioTeorico_conValorPorEncimaDe12Responde400SinLlamarAlServicio() throws Exception {
        mockMvc.perform(patch("/api/empleados/2/horario-teorico")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasJornadaDiaria\":15}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(empleadoService);
    }

    @Test
    void actualizarHorarioTeorico_conValorPorDebajoDe1Responde400() throws Exception {
        mockMvc.perform(patch("/api/empleados/2/horario-teorico")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasJornadaDiaria\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actualizarHorarioTeorico_empleadoInexistenteResponde404() throws Exception {
        when(empleadoService.actualizarHorarioTeorico(eq(99L), eq(BigDecimal.valueOf(6))))
                .thenThrow(new EmpleadoNotFoundException(99L));

        mockMvc.perform(patch("/api/empleados/99/horario-teorico")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasJornadaDiaria\":6}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void actualizarDiasVacacionesAnuales_conValorValidoDevuelveElEmpleadoActualizado() throws Exception {
        when(empleadoService.actualizarDiasVacacionesAnuales(eq(2L), eq(15)))
                .thenReturn(empleadoResponseConDias(15));

        mockMvc.perform(patch("/api/empleados/2/dias-vacaciones-anuales")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diasVacacionesAnuales\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasVacacionesAnuales").value(15));
    }

    @Test
    void actualizarDiasVacacionesAnuales_porEncimaDelGlobalResponde400() throws Exception {
        when(empleadoService.actualizarDiasVacacionesAnuales(eq(2L), eq(30)))
                .thenThrow(new DiasVacacionesAnualesFueraDeRangoException(23));

        mockMvc.perform(patch("/api/empleados/2/dias-vacaciones-anuales")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diasVacacionesAnuales\":30}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actualizarDiasVacacionesAnuales_conValorPorDebajoDe1Responde400SinLlamarAlServicio() throws Exception {
        mockMvc.perform(patch("/api/empleados/2/dias-vacaciones-anuales")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diasVacacionesAnuales\":0}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(empleadoService);
    }

    @Test
    void actualizarDiasVacacionesAnuales_conNullVuelveAUsarElValorGlobal() throws Exception {
        when(empleadoService.actualizarDiasVacacionesAnuales(eq(2L), eq(null)))
                .thenReturn(empleadoResponseConDias(null));

        mockMvc.perform(patch("/api/empleados/2/dias-vacaciones-anuales")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private static EmpleadoResponseDTO empleadoResponseConHoras(BigDecimal horas) {
        return new EmpleadoResponseDTO(
                2L, "Ana", "Lopez", "ana@test.com", "12345678A", null, null,
                LocalDate.of(2026, 1, 1), null, true,
                new TipoUsuarioDTO("EMPLEADO", "Empleado"), horas, null);
    }

    private static EmpleadoResponseDTO empleadoResponseConDias(Integer dias) {
        return new EmpleadoResponseDTO(
                2L, "Ana", "Lopez", "ana@test.com", "12345678A", null, null,
                LocalDate.of(2026, 1, 1), null, true,
                new TipoUsuarioDTO("EMPLEADO", "Empleado"), null, dias);
    }
}
