package com.gestionrestaurante.back.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gestionrestaurante.back.dto.FacturaListadoDTO;
import com.gestionrestaurante.back.entity.EstadoPago;
import com.gestionrestaurante.back.exception.FacturaNotFoundException;
import com.gestionrestaurante.back.security.JwtAuthFilter;
import com.gestionrestaurante.back.service.FacturaExportService;
import com.gestionrestaurante.back.service.FacturaExtractionService;
import com.gestionrestaurante.back.service.FacturaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Mismo patron que NominaControllerTest (back/fase3-1-back.md). */
@WebMvcTest(
        controllers = FacturaController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class FacturaControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacturaService facturaService;

    @MockitoBean
    private FacturaExtractionService facturaExtractionService;

    @MockitoBean
    private FacturaExportService facturaExportService;

    @AfterEach
    void limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext();
    }

    private static Authentication autenticarComoAdmin() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@test.com", null, List.of(new SimpleGrantedAuthority("FACTURAS:EDICION")));
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
    void listar_conTokenDeEmpleadoResponde403() throws Exception {
        autenticarComoEmpleado();

        mockMvc.perform(get("/api/facturas").param("tipo", "PROVEEDOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listar_devuelveLaPaginaDelServicio() throws Exception {
        autenticarComoAdmin();
        when(facturaService.listar(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(new FacturaListadoDTO(
                        1L, "Iberdrola", LocalDate.of(2026, 3, 1), null,
                        null, null, new BigDecimal("120.50"), EstadoPago.PENDIENTE))));

        mockMvc.perform(get("/api/facturas").param("tipo", "SUMINISTRO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].empresa").value("Iberdrola"));
    }

    @Test
    void crear_conTokenDeEmpleadoResponde403() throws Exception {
        autenticarComoEmpleado();
        MockMultipartFile archivo = new MockMultipartFile("archivo", "factura.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/facturas")
                        .file(archivo)
                        .param("proveedorId", "1")
                        .param("fechaImpresion", "2026-03-01")
                        .param("importeTotal", "120.50"))
                .andExpect(status().isForbidden());
    }

    @Test
    void obtenerDetalle_facturaInexistenteResponde404() throws Exception {
        autenticarComoAdmin();
        when(facturaService.obtenerDetalle(99L)).thenThrow(new FacturaNotFoundException(99L));

        mockMvc.perform(get("/api/facturas/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cambiarEstadoPago_conTokenDeEmpleadoResponde403() throws Exception {
        autenticarComoEmpleado();

        mockMvc.perform(patch("/api/facturas/1/estado-pago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estadoPago\":\"PAGADA\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void extraer_conTokenDeEmpleadoResponde403() throws Exception {
        autenticarComoEmpleado();
        MockMultipartFile archivo = new MockMultipartFile("archivo", "factura.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/facturas/extraer")
                        .file(archivo)
                        .param("tipo", "PROVEEDOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportar_conTokenDeEmpleadoResponde403() throws Exception {
        autenticarComoEmpleado();

        mockMvc.perform(get("/api/facturas/exportar").param("tipo", "PROVEEDOR"))
                .andExpect(status().isForbidden());
    }
}
