package com.gestionrestaurante.back.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gestionrestaurante.back.dto.SolicitudVacacionesAdminResponseDTO;
import com.gestionrestaurante.back.dto.SolicitudVacacionesResponseDTO;
import com.gestionrestaurante.back.entity.EstadoSolicitud;
import com.gestionrestaurante.back.exception.DiasVacacionesInsuficientesException;
import com.gestionrestaurante.back.exception.SolicitudVacacionesNoEditableException;
import com.gestionrestaurante.back.exception.SolicitudVacacionesNotFoundException;
import com.gestionrestaurante.back.security.CurrentUser;
import com.gestionrestaurante.back.security.JwtAuthFilter;
import com.gestionrestaurante.back.service.VacacionesService;
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
    private static final Long ADMIN_ID = 2L;

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

    private static Authentication autenticacionDeAdmin() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@test.com", null, List.of(new SimpleGrantedAuthority("EMPLEADOS:EDICION")));
        authentication.setDetails(ADMIN_ID);
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

    @Test
    void aprobar_devuelveLaSolicitudAprobada() throws Exception {
        when(vacacionesService.aprobar(eq(10L), eq(ADMIN_ID)))
                .thenReturn(solicitudResponse(10L, EstadoSolicitud.APROBADA));

        mockMvc.perform(patch("/api/vacaciones/admin/solicitudes/10/aprobar").principal(autenticacionDeAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"));
    }

    @Test
    void aprobar_solicitudInexistenteResponde404() throws Exception {
        when(vacacionesService.aprobar(eq(10L), eq(ADMIN_ID)))
                .thenThrow(new SolicitudVacacionesNotFoundException(10L));

        mockMvc.perform(patch("/api/vacaciones/admin/solicitudes/10/aprobar").principal(autenticacionDeAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rechazar_sinMotivoResponde400SinLlamarAlServicio() throws Exception {
        mockMvc.perform(patch("/api/vacaciones/admin/solicitudes/10/rechazar")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rechazar_conMotivoDevuelveLaSolicitudRechazada() throws Exception {
        when(vacacionesService.rechazar(eq(10L), eq(ADMIN_ID), eq("FALTA_DISPONIBILIDAD")))
                .thenReturn(solicitudResponse(10L, EstadoSolicitud.RECHAZADA));

        mockMvc.perform(patch("/api/vacaciones/admin/solicitudes/10/rechazar")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"FALTA_DISPONIBILIDAD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"));
    }

    @Test
    void rechazar_conMotivoNoPermitidoResponde400SinLlamarAlServicio() throws Exception {
        mockMvc.perform(patch("/api/vacaciones/admin/solicitudes/10/rechazar")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"no queda cupo\"}"))
                .andExpect(status().isBadRequest());

        verify(vacacionesService, never()).rechazar(any(), any(), any());
    }

    @Test
    void listarTodasPendientes_devuelveLaPaginaDelServicio() throws Exception {
        when(vacacionesService.listarTodasPendientes(any()))
                .thenReturn(new PageImpl<>(List.of(new SolicitudVacacionesAdminResponseDTO(
                        1L, 5L, "Ana Lopez", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9),
                        (short) 5, EstadoSolicitud.PENDIENTE, LocalDateTime.now(), null))));

        mockMvc.perform(get("/api/vacaciones/admin/solicitudes/pendientes").principal(autenticacionDeAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].empleadoNombre").value("Ana Lopez"));
    }

    @Test
    void asignarDeOficio_sinCupoSuficienteResponde409() throws Exception {
        when(vacacionesService.asignarDeOficio(eq(5L), any(LocalDate.class), any(LocalDate.class), eq(ADMIN_ID)))
                .thenThrow(new DiasVacacionesInsuficientesException());

        mockMvc.perform(post("/api/vacaciones/admin/solicitudes")
                        .principal(autenticacionDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"empleadoId\":5,\"fechaInicio\":\"2026-01-05\",\"fechaFin\":\"2026-01-12\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void obtenerCalendarioComoAdmin_devuelveElCalendarioDelEmpleadoIndicado() throws Exception {
        when(vacacionesService.obtenerCalendario(eq(5L), eq(2026))).thenReturn(List.of());

        mockMvc.perform(get("/api/vacaciones/admin/calendario")
                        .principal(autenticacionDeAdmin())
                        .param("empleadoId", "5")
                        .param("anio", "2026"))
                .andExpect(status().isOk());
    }

    private static SolicitudVacacionesResponseDTO solicitudResponse(Long id, EstadoSolicitud estado) {
        return new SolicitudVacacionesResponseDTO(
                id, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9), (short) 5, estado, LocalDateTime.now(),
                estado == EstadoSolicitud.RECHAZADA ? "no queda cupo" : null);
    }
}
