package com.gestionrestaurante.back.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gestionrestaurante.back.entity.ConfiguracionUbicacion;
import com.gestionrestaurante.back.entity.ConfiguracionVacaciones;
import com.gestionrestaurante.back.security.JwtAuthFilter;
import com.gestionrestaurante.back.service.ConfiguracionUbicacionService;
import com.gestionrestaurante.back.service.ConfiguracionVacacionesService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 * Misma nota que en FichajeAdminControllerTest/VacacionesControllerTest: la cadena de
 * seguridad real (y por tanto @PreAuthorize) esta fuera de alcance de este test.
 */
@WebMvcTest(
        controllers = ConfiguracionController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class ConfiguracionControllerTest {

    private static final Long ADMIN_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfiguracionUbicacionService configuracionUbicacionService;
    @MockitoBean
    private ConfiguracionVacacionesService configuracionVacacionesService;

    private static Authentication autenticacionDeAdmin() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@test.com", null, List.of(new SimpleGrantedAuthority("EMPLEADOS:EDICION")));
        authentication.setDetails(ADMIN_ID);
        return authentication;
    }

    @Test
    void obtenerUbicacion_devuelveLaConfiguracionActual() throws Exception {
        ConfiguracionUbicacion configuracion = new ConfiguracionUbicacion();
        configuracion.setLat(BigDecimal.valueOf(43.506670));
        configuracion.setLng(BigDecimal.valueOf(-5.690421));
        configuracion.setRadioMetros(150);
        configuracion.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(configuracionUbicacionService.obtener()).thenReturn(configuracion);

        mockMvc.perform(get("/api/configuracion/ubicacion").principal(autenticacionDeAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.radioMetros").value(150));
    }

    @Test
    void actualizarUbicacion_conLatFueraDeRangoResponde400SinLlamarAlServicio() throws Exception {
        mockMvc.perform(put("/api/configuracion/ubicacion")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":200,\"lng\":0,\"radioMetros\":150}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void obtenerVacaciones_devuelveElValorGlobalActual() throws Exception {
        ConfiguracionVacaciones configuracion = new ConfiguracionVacaciones();
        configuracion.setDiasAnualesDefecto(23);
        configuracion.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(configuracionVacacionesService.obtener()).thenReturn(configuracion);

        mockMvc.perform(get("/api/configuracion/vacaciones").principal(autenticacionDeAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasAnualesDefecto").value(23));
    }

    @Test
    void actualizarVacaciones_conValorFueraDeRangoResponde400SinLlamarAlServicio() throws Exception {
        mockMvc.perform(put("/api/configuracion/vacaciones")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diasAnualesDefecto\":0}"))
                .andExpect(status().isBadRequest());
    }
}
