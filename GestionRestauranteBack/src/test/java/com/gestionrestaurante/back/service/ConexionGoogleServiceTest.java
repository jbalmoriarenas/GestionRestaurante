package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gestionrestaurante.back.dto.ConexionEstadoDTO;
import com.gestionrestaurante.back.entity.ConexionGoogleBusiness;
import com.gestionrestaurante.back.exception.EstadoOAuthInvalidoException;
import com.gestionrestaurante.back.repository.ConexionGoogleBusinessRepository;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient.TokensGoogle;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient.UbicacionGoogle;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Cubre completarConexion (cifrado del refresh token, descubrimiento de ubicacion,
 * sincronizacion inmediata) y la validacion de {@code state} (back/fase4-1-back.md) con
 * GoogleBusinessProfileClient mockeado - no se llama a la API real de Google en tests, mismo
 * criterio que TesseractFacturaExtractionService en fase2-3-back.md.
 */
@ExtendWith(MockitoExtension.class)
class ConexionGoogleServiceTest {

    @Mock
    private ConexionGoogleBusinessRepository conexionRepository;
    @Mock
    private GoogleBusinessProfileClient googleClient;
    @Mock
    private TokenEncryptionService tokenEncryptionService;
    @Mock
    private ValoracionSincronizacionService sincronizacionService;

    private ConexionGoogleService service;

    @BeforeEach
    void setUp() {
        service = new ConexionGoogleService(conexionRepository, googleClient, tokenEncryptionService, sincronizacionService);
    }

    private static String extraerState(String url) {
        Matcher matcher = Pattern.compile("state=([^&]+)").matcher(url);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void obtenerEstado_sinFilaDevuelveDesconectado() {
        when(conexionRepository.findById(1L)).thenReturn(Optional.empty());

        ConexionEstadoDTO estado = service.obtenerEstado();

        assertThat(estado.conectado()).isFalse();
        assertThat(estado.nombreUbicacion()).isNull();
    }

    @Test
    void completarConexion_conStateNoEmitidoLanzaExcepcion() {
        assertThatThrownBy(() -> service.completarConexion("code", "state-nunca-emitido"))
                .isInstanceOf(EstadoOAuthInvalidoException.class);
    }

    @Test
    void completarConexion_cifraElRefreshTokenAntesDeGuardarlo() {
        when(googleClient.generarUrlConsentimiento(anyString())).thenAnswer(inv -> "https://google/consent?state=" + inv.getArgument(0));
        String url = service.iniciarConexion(42L);
        String state = extraerState(url);

        when(googleClient.intercambiarCodigoPorTokens("code")).thenReturn(new TokensGoogle("access", "refresh-en-claro", 3600));
        when(googleClient.listarUbicaciones("access"))
                .thenReturn(List.of(new UbicacionGoogle("accounts/1/locations/2", "Sidreria La Zapica")));
        when(tokenEncryptionService.cifrar("refresh-en-claro")).thenReturn("refresh-cifrado");
        when(conexionRepository.findById(1L)).thenReturn(Optional.empty());

        service.completarConexion("code", state);

        ArgumentCaptor<ConexionGoogleBusiness> captor = ArgumentCaptor.forClass(ConexionGoogleBusiness.class);
        verify(conexionRepository).save(captor.capture());
        assertThat(captor.getValue().getRefreshTokenCifrado()).isEqualTo("refresh-cifrado");
        assertThat(captor.getValue().getLocationResourceName()).isEqualTo("accounts/1/locations/2");
        assertThat(captor.getValue().getConectadoPorAdminId()).isEqualTo(42L);
        verify(sincronizacionService).sincronizar();
    }

    @Test
    void completarConexion_conUnStateYaUsadoLanzaExcepcionLaSegundaVez() {
        when(googleClient.generarUrlConsentimiento(anyString())).thenAnswer(inv -> "https://google/consent?state=" + inv.getArgument(0));
        String url = service.iniciarConexion(1L);
        String state = extraerState(url);
        when(googleClient.intercambiarCodigoPorTokens(any())).thenReturn(new TokensGoogle("a", "r", 1));
        when(googleClient.listarUbicaciones(any())).thenReturn(List.of(new UbicacionGoogle("accounts/1/locations/2", "Test")));
        when(conexionRepository.findById(1L)).thenReturn(Optional.empty());

        service.completarConexion("code", state);

        assertThatThrownBy(() -> service.completarConexion("code", state))
                .isInstanceOf(EstadoOAuthInvalidoException.class);
    }

    @Test
    void desconectar_sinConexionNoLlamaAGoogleNiFalla() {
        when(conexionRepository.findById(1L)).thenReturn(Optional.empty());

        service.desconectar();

        verify(googleClient, never()).revocarToken(any());
    }

    @Test
    void desconectar_borraLaFilaAunqueLaRevocacionEnGoogleFalle() {
        ConexionGoogleBusiness conexion = new ConexionGoogleBusiness();
        conexion.setRefreshTokenCifrado("cifrado");
        when(conexionRepository.findById(1L)).thenReturn(Optional.of(conexion));
        when(tokenEncryptionService.descifrar("cifrado")).thenReturn("en-claro");
        org.mockito.Mockito.doThrow(new RuntimeException("Google no responde")).when(googleClient).revocarToken("en-claro");

        service.desconectar();

        verify(conexionRepository).delete(conexion);
    }
}
