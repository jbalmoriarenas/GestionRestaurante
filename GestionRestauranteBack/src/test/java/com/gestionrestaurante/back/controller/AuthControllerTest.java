package com.gestionrestaurante.back.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gestionrestaurante.back.security.JwtAuthFilter;
import com.gestionrestaurante.back.service.AuthService;
import com.gestionrestaurante.back.service.RecuperacionPasswordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ninguno de los dos endpoints de recuperacion lleva @PreAuthorize (son publicos por
 * definicion, back/fase6-1-back.md) - a diferencia de ConfiguracionControllerTest, no hace
 * falta simular ningun principal.
 */
@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private RecuperacionPasswordService recuperacionPasswordService;

    @Test
    void solicitarRecuperacion_conEmailConFormatoInvalidoResponde400SinLlamarAlServicio() throws Exception {
        mockMvc.perform(post("/api/auth/recuperacion/solicitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-un-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void solicitarRecuperacion_conEmailValidoResponde204YLlamaAlServicio() throws Exception {
        mockMvc.perform(post("/api/auth/recuperacion/solicitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@test.com\"}"))
                .andExpect(status().isNoContent());

        verify(recuperacionPasswordService).solicitar("ana@test.com");
    }

    @Test
    void restablecerPassword_conPasswordCortaResponde400SinLlamarAlServicio() throws Exception {
        mockMvc.perform(post("/api/auth/recuperacion/restablecer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restablecerPassword_conPasswordSinMayusculaNiDigitoResponde400SinLlamarAlServicio() throws Exception {
        mockMvc.perform(post("/api/auth/recuperacion/restablecer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"password\":\"todaminuscula\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restablecerPassword_conDatosValidosResponde204YLlamaAlServicio() throws Exception {
        mockMvc.perform(post("/api/auth/recuperacion/restablecer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"password\":\"nuevaPassword1\"}"))
                .andExpect(status().isNoContent());

        verify(recuperacionPasswordService).restablecer("abc", "nuevaPassword1");
    }
}
