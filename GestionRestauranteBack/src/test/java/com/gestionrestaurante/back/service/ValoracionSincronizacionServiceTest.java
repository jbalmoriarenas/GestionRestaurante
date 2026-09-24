package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gestionrestaurante.back.dto.ResultadoSincronizacionDTO;
import com.gestionrestaurante.back.entity.ConexionGoogleBusiness;
import com.gestionrestaurante.back.entity.Valoracion;
import com.gestionrestaurante.back.repository.ConexionGoogleBusinessRepository;
import com.gestionrestaurante.back.repository.ValoracionRepository;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient.PaginaResenas;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient.ResenaGoogle;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Cubre el upsert por googleReviewId (nunca duplica) y el aislamiento de fallos (nunca
 * propaga, siempre registra ultimoError) - back/fase4-1-back.md. GoogleBusinessProfileClient
 * mockeado, sin llamar a la API real de Google.
 */
@ExtendWith(MockitoExtension.class)
class ValoracionSincronizacionServiceTest {

    @Mock
    private ConexionGoogleBusinessRepository conexionRepository;
    @Mock
    private ValoracionRepository valoracionRepository;
    @Mock
    private GoogleBusinessProfileClient googleClient;
    @Mock
    private TokenEncryptionService tokenEncryptionService;

    private ValoracionSincronizacionService service;
    private ConexionGoogleBusiness conexion;

    @BeforeEach
    void setUp() {
        service = new ValoracionSincronizacionService(conexionRepository, valoracionRepository, googleClient, tokenEncryptionService);
        conexion = new ConexionGoogleBusiness();
        conexion.setId(1L);
        conexion.setLocationResourceName("accounts/1/locations/2");
        conexion.setRefreshTokenCifrado("cifrado");
    }

    private static ResenaGoogle resena(String id) {
        return new ResenaGoogle(id, "Cliente", null, 5, "Muy bueno", LocalDateTime.now(), null);
    }

    @Test
    void sincronizar_sinConexionNoLlamaAGoogleYDevuelveVacio() {
        when(conexionRepository.findById(1L)).thenReturn(Optional.empty());

        ResultadoSincronizacionDTO resultado = service.sincronizar();

        assertThat(resultado.total()).isZero();
        verify(googleClient, never()).refrescarAccessToken(any());
    }

    @Test
    void sincronizar_procesarDosVecesLaMismaResenaNoLaDuplica() {
        when(conexionRepository.findById(1L)).thenReturn(Optional.of(conexion));
        when(tokenEncryptionService.descifrar("cifrado")).thenReturn("refresh-en-claro");
        when(googleClient.refrescarAccessToken("refresh-en-claro")).thenReturn("access-token");
        when(googleClient.listarResenas("access-token", "accounts/1/locations/2", null))
                .thenReturn(new PaginaResenas(List.of(resena("review-1")), null));
        when(valoracionRepository.findByGoogleReviewId("review-1")).thenReturn(Optional.empty());

        ResultadoSincronizacionDTO primera = service.sincronizar();

        // La segunda vuelta ya encuentra la fila creada en la primera.
        Valoracion existente = new Valoracion();
        existente.setGoogleReviewId("review-1");
        when(valoracionRepository.findByGoogleReviewId("review-1")).thenReturn(Optional.of(existente));

        ResultadoSincronizacionDTO segunda = service.sincronizar();

        assertThat(primera.nuevas()).isEqualTo(1);
        assertThat(segunda.nuevas()).isZero();
        assertThat(segunda.actualizadas()).isEqualTo(1);
    }

    @Test
    void sincronizar_actualizaFechaUltimaSincronizacionYLimpiaUltimoError() {
        conexion.setUltimoError("error anterior");
        when(conexionRepository.findById(1L)).thenReturn(Optional.of(conexion));
        when(tokenEncryptionService.descifrar("cifrado")).thenReturn("refresh-en-claro");
        when(googleClient.refrescarAccessToken(anyString())).thenReturn("access-token");
        when(googleClient.listarResenas(anyString(), anyString(), any())).thenReturn(new PaginaResenas(List.of(), null));

        service.sincronizar();

        assertThat(conexion.getUltimoError()).isNull();
        assertThat(conexion.getFechaUltimaSincronizacion()).isNotNull();
    }

    @Test
    void sincronizar_unFalloDeGoogleNoPropagaYRegistraUltimoError() {
        when(conexionRepository.findById(1L)).thenReturn(Optional.of(conexion));
        when(tokenEncryptionService.descifrar("cifrado")).thenReturn("refresh-en-claro");
        when(googleClient.refrescarAccessToken(anyString())).thenThrow(new RuntimeException("Google no responde"));

        ResultadoSincronizacionDTO resultado = service.sincronizar();

        assertThat(resultado.total()).isZero();
        assertThat(conexion.getUltimoError()).isEqualTo("Google no responde");
    }
}
