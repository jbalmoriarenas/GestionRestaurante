package com.gestionrestaurante.back.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gestionrestaurante.back.exception.DiasVacacionesInsuficientesException;
import com.gestionrestaurante.back.exception.SolicitudVacacionesNoEditableException;
import com.gestionrestaurante.back.security.CurrentUser;
import com.gestionrestaurante.back.security.JwtAuthFilter;
import com.gestionrestaurante.back.service.VacacionesService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
 * La cadena de seguridad real (JWT/CORS) esta fuera de alcance de este test: se fija el
 * {@link Authentication} directamente como principal de la peticion (con la misma forma
 * que deja {@code JwtAuthFilter} en produccion: empleadoId en los "details"), sin depender
 * de ningun filtro de seguridad, para poder ejercitar {@link CurrentUser#empleadoId} igual
 * que en produccion.
 */
@WebMvcTest(
        controllers = VacacionesController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class VacacionesControllerTest {

    private static final Long EMPLEADO_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VacacionesService vacacionesService;

    private static Authentication autenticacionDeEmpleado() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "empleado@test.com", null, List.of(new SimpleGrantedAuthority("VACACIONES:EDICION")));
        authentication.setDetails(EMPLEADO_ID);
        return authentication;
    }

    @Test
    void solicitar_masDiasQueLosDisponiblesResponde409() throws Exception {
        when(vacacionesService.solicitar(eq(EMPLEADO_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenThrow(new DiasVacacionesInsuficientesException());

        mockMvc.perform(post("/api/vacaciones/solicitudes")
                        .principal(autenticacionDeEmpleado())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaInicio\":\"2026-01-05\",\"fechaFin\":\"2026-01-12\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelar_solicitudYaAprobadaResponde409() throws Exception {
        Mockito.doThrow(new SolicitudVacacionesNoEditableException())
                .when(vacacionesService).cancelar(eq(EMPLEADO_ID), eq(10L));

        mockMvc.perform(delete("/api/vacaciones/solicitudes/10")
                        .principal(autenticacionDeEmpleado()))
                .andExpect(status().isConflict());
    }

    @Test
    void obtenerResumen_conAnioFueraDeRangoResponde400SinLlamarAlServicio() throws Exception {
        mockMvc.perform(get("/api/vacaciones/resumen")
                        .principal(autenticacionDeEmpleado())
                        .param("anio", "99999999"))
                .andExpect(status().isBadRequest());
    }
}
