package com.gestionrestaurante.back.service.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.gestionrestaurante.back.exception.ConexionGoogleNoDisponibleException;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient.PaginaResenas;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient.ResenaGoogle;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient.TokensGoogle;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient.UbicacionGoogle;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * A diferencia del resto de tests de Fase 4 (que mockean la interfaz
 * {@link GoogleBusinessProfileClient}), esta clase ejercita {@link GoogleBusinessProfileClientImpl}
 * de verdad con un {@link MockRestServiceServer}: es la unica que cubre el parseo JSON real, el
 * mapeo de {@code starRating} y el formateo de las plantillas de URL, sin llamar a la API real
 * de Google (back/fase4-1-back.md, mismo criterio que Tesseract en fase2-3-back.md).
 */
class GoogleBusinessProfileClientImplTest {

    private static final String REDIRECT_URI = "http://localhost:8080/api/valoraciones/conexion/callback";

    private MockRestServiceServer servidor;
    private GoogleBusinessProfileClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleBusinessProfileClientImpl(builder, "client-id-test", "client-secret-test", REDIRECT_URI);
    }

    @Test
    void generarUrlConsentimiento_incluyeAccessTypeOfflineYPromptConsent() {
        String url = client.generarUrlConsentimiento("estado-123");

        assertThat(url)
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth?client_id=client-id-test")
                .contains("access_type=offline")
                .contains("prompt=consent")
                .contains("state=estado-123");
    }

    @Test
    void intercambiarCodigoPorTokens_devuelveLosTokensDeLaRespuesta() {
        servidor.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"access-1\",\"refresh_token\":\"refresh-1\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        TokensGoogle tokens = client.intercambiarCodigoPorTokens("code-1");

        assertThat(tokens.accessToken()).isEqualTo("access-1");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-1");
        assertThat(tokens.expiraEnSegundos()).isEqualTo(3600L);
    }

    @Test
    void intercambiarCodigoPorTokens_sinRefreshTokenLanzaExcepcion() {
        servidor.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"access-1\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.intercambiarCodigoPorTokens("code-1"))
                .isInstanceOf(ConexionGoogleNoDisponibleException.class);
    }

    @Test
    void refrescarAccessToken_devuelveElAccessTokenDeLaRespuesta() {
        servidor.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"access-nuevo\"}", MediaType.APPLICATION_JSON));

        String accessToken = client.refrescarAccessToken("refresh-1");

        assertThat(accessToken).isEqualTo("access-nuevo");
    }

    @Test
    void refrescarAccessToken_conErrorDeGoogleLanzaConexionGoogleNoDisponibleException() {
        servidor.expect(requestTo("https://oauth2.googleapis.com/token")).andRespond(withServerError());

        assertThatThrownBy(() -> client.refrescarAccessToken("refresh-1"))
                .isInstanceOf(ConexionGoogleNoDisponibleException.class);
    }

    @Test
    void revocarToken_conErrorDeGoogleLanzaConexionGoogleNoDisponibleException() {
        servidor.expect(requestTo("https://oauth2.googleapis.com/revoke")).andRespond(withServerError());

        assertThatThrownBy(() -> client.revocarToken("refresh-1")).isInstanceOf(ConexionGoogleNoDisponibleException.class);
    }

    @Test
    void listarUbicaciones_descubreLaUbicacionDeLaCuentaConectada() {
        servidor.expect(requestTo("https://mybusinessaccountmanagement.googleapis.com/v1/accounts"))
                .andRespond(withSuccess(
                        "{\"accounts\":[{\"name\":\"accounts/1\",\"accountName\":\"Restaurante\"}]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(
                        "https://mybusinessbusinessinformation.googleapis.com/v1/accounts/1/locations?readMask=name,title"))
                .andRespond(withSuccess(
                        "{\"locations\":[{\"name\":\"locations/2\",\"title\":\"Sidreria La Zapica\"}]}",
                        MediaType.APPLICATION_JSON));

        List<UbicacionGoogle> ubicaciones = client.listarUbicaciones("access-1");

        assertThat(ubicaciones).hasSize(1);
        UbicacionGoogle ubicacion = ubicaciones.get(0);
        assertThat(ubicacion.locationResourceName()).isEqualTo("accounts/1/locations/2");
        assertThat(ubicacion.nombre()).isEqualTo("Sidreria La Zapica");
    }

    @Test
    void listarResenas_mapeaStarRatingYFechasCorrectamente() {
        servidor.expect(requestTo("https://mybusiness.googleapis.com/v4/accounts/1/locations/2/reviews?pageSize=50"))
                .andRespond(withSuccess(
                        "{\"reviews\":[{\"reviewId\":\"r1\",\"reviewer\":{\"displayName\":\"Cliente Feliz\"},"
                                + "\"starRating\":\"FIVE\",\"comment\":\"Genial\","
                                + "\"createTime\":\"2026-01-15T10:30:00Z\",\"updateTime\":null}],\"nextPageToken\":null}",
                        MediaType.APPLICATION_JSON));

        PaginaResenas pagina = client.listarResenas("access-1", "accounts/1/locations/2", null);

        assertThat(pagina.resenas()).hasSize(1);
        ResenaGoogle resena = pagina.resenas().get(0);
        assertThat(resena.googleReviewId()).isEqualTo("r1");
        assertThat(resena.autorNombre()).isEqualTo("Cliente Feliz");
        assertThat(resena.puntuacion()).isEqualTo(5);
        assertThat(resena.fechaPublicacion()).isEqualTo(LocalDateTime.of(2026, Month.JANUARY, 15, 10, 30, 0));
        assertThat(resena.fechaActualizacion()).isNull();
        assertThat(pagina.siguientePageToken()).isNull();
    }

    @Test
    void listarResenas_conStarRatingDesconocidoLoContabilizaComoUnaEstrellaSinFallar() {
        servidor.expect(requestTo("https://mybusiness.googleapis.com/v4/accounts/1/locations/2/reviews?pageSize=50"))
                .andRespond(withSuccess(
                        "{\"reviews\":[{\"reviewId\":\"r2\",\"reviewer\":null,\"starRating\":\"RATING_UNSPECIFIED\","
                                + "\"comment\":null,\"createTime\":\"2026-01-15T10:30:00Z\"}]}",
                        MediaType.APPLICATION_JSON));

        PaginaResenas pagina = client.listarResenas("access-1", "accounts/1/locations/2", null);

        ResenaGoogle resena = pagina.resenas().get(0);
        assertThat(resena.puntuacion()).isEqualTo(1);
        assertThat(resena.autorNombre()).isEqualTo("Anonimo");
    }
}
