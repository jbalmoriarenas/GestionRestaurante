package com.gestionrestaurante.back.service.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gestionrestaurante.back.exception.ConexionGoogleNoDisponibleException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Llamadas HTTP directas a Google via RestClient de Spring (back/fase4-1-back.md,
 * "Decisiones tecnicas") - sin el SDK oficial de Google, la superficie de API que se necesita
 * es pequeña. Usa tres APIs distintas de Google, cada una con su propio host:
 * <ul>
 *   <li>{@code oauth2.googleapis.com} - intercambio/refresco/revocacion de tokens (estandar OAuth 2.0).</li>
 *   <li>{@code mybusinessaccountmanagement.googleapis.com} / {@code mybusinessbusinessinformation.googleapis.com}
 *       - descubrimiento de la cuenta/ubicacion al conectar (solo se usa una vez, en {@link #listarUbicaciones}).</li>
 *   <li>{@code mybusiness.googleapis.com/v4} (API legada, unica superficie de Google Business
 *       Profile que expone reseñas) - {@link #listarResenas}.</li>
 * </ul>
 */
@Component
@Slf4j
public class GoogleBusinessProfileClientImpl implements GoogleBusinessProfileClient {

    private static final String SCOPE = "https://www.googleapis.com/auth/business.manage";
    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    private static final String ACCOUNTS_URL = "https://mybusinessaccountmanagement.googleapis.com/v1/accounts";
    private static final String LOCATIONS_URL_TEMPLATE =
            "https://mybusinessbusinessinformation.googleapis.com/v1/%s/locations?readMask=name,title";
    private static final String REVIEWS_URL_TEMPLATE = "https://mybusiness.googleapis.com/v4/%s/reviews";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    /** {@code RestClient.Builder} inyectado (en vez de {@code RestClient.create()} directo) para
     * poder atar un {@code MockRestServiceServer} en tests sin llamar a la API real de Google -
     * ver {@code GoogleBusinessProfileClientImplTest}. */
    public GoogleBusinessProfileClientImpl(
            RestClient.Builder restClientBuilder,
            @Value("${app.google.client-id}") String clientId,
            @Value("${app.google.client-secret}") String clientSecret,
            @Value("${app.google.redirect-uri}") String redirectUri) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public String generarUrlConsentimiento(String state) {
        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String encodedScope = URLEncoder.encode(SCOPE, StandardCharsets.UTF_8);
        String encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8);
        return AUTH_URL
                + "?client_id=" + clientId
                + "&redirect_uri=" + encodedRedirect
                + "&response_type=code"
                + "&scope=" + encodedScope
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + encodedState;
    }

    @Override
    public TokensGoogle intercambiarCodigoPorTokens(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");
        TokenResponse respuesta = postToken(body);
        if (respuesta.refreshToken() == null) {
            throw new ConexionGoogleNoDisponibleException(
                    "Google no ha devuelto un refresh_token. Vuelve a intentar la conexion "
                            + "revocando primero el acceso desde la cuenta de Google si ya se habia autorizado antes.");
        }
        return new TokensGoogle(respuesta.accessToken(), respuesta.refreshToken(), respuesta.expiresIn());
    }

    @Override
    public String refrescarAccessToken(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("refresh_token", refreshToken);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", "refresh_token");
        return postToken(body).accessToken();
    }

    @Override
    public void revocarToken(String refreshToken) {
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", refreshToken);
            restClient.post()
                    .uri(REVOKE_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // Best-effort a proposito (back/fase4-1-back.md, "Desconexion con revocacion
            // best-effort"): el llamante decide que hacer con el fallo, este metodo no lanza.
            throw new ConexionGoogleNoDisponibleException("No se ha podido revocar el token en Google", e);
        }
    }

    /**
     * Solo se lee la primera pagina de cuentas y, por cada cuenta, la primera pagina de
     * ubicaciones ({@code nextPageToken} de {@link AccountsResponse}/{@link LocationsResponse}
     * se deserializa pero no se sigue) - coherente con el supuesto de "una unica ubicacion por
     * cuenta" (back/fase4-1-back.md, "Analisis y supuestos"): si la cuenta conectada tuviera
     * mas cuentas/ubicaciones de las que caben en una pagina, las siguientes no se descubririan.
     */
    @Override
    public List<UbicacionGoogle> listarUbicaciones(String accessToken) {
        List<UbicacionGoogle> resultado = new ArrayList<>();
        try {
            AccountsResponse cuentas = restClient.get()
                    .uri(ACCOUNTS_URL)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .body(AccountsResponse.class);
            if (cuentas == null || cuentas.accounts() == null) {
                return resultado;
            }
            for (AccountEntry cuenta : cuentas.accounts()) {
                String urlUbicaciones = LOCATIONS_URL_TEMPLATE.formatted(cuenta.name());
                LocationsResponse ubicaciones = restClient.get()
                        .uri(urlUbicaciones)
                        .headers(h -> h.setBearerAuth(accessToken))
                        .retrieve()
                        .body(LocationsResponse.class);
                if (ubicaciones == null || ubicaciones.locations() == null) {
                    continue;
                }
                for (LocationEntry ubicacion : ubicaciones.locations()) {
                    String locationResourceName = cuenta.name() + "/" + ubicacion.name();
                    resultado.add(new UbicacionGoogle(locationResourceName, ubicacion.title()));
                }
            }
            return resultado;
        } catch (RestClientException e) {
            throw new ConexionGoogleNoDisponibleException("No se han podido obtener las ubicaciones de Google Business Profile", e);
        }
    }

    @Override
    public PaginaResenas listarResenas(String accessToken, String locationResourceName, String pageToken) {
        try {
            StringBuilder uri = new StringBuilder(REVIEWS_URL_TEMPLATE.formatted(locationResourceName));
            uri.append("?pageSize=50");
            if (pageToken != null && !pageToken.isBlank()) {
                uri.append("&pageToken=").append(URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
            }
            ReviewsResponse respuesta = restClient.get()
                    .uri(uri.toString())
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .body(ReviewsResponse.class);
            if (respuesta == null || respuesta.reviews() == null) {
                return new PaginaResenas(List.of(), null);
            }
            List<ResenaGoogle> resenas = respuesta.reviews().stream().map(GoogleBusinessProfileClientImpl::toResena).toList();
            return new PaginaResenas(resenas, respuesta.nextPageToken());
        } catch (RestClientException e) {
            throw new ConexionGoogleNoDisponibleException("No se han podido obtener las reseñas de Google", e);
        }
    }

    private TokenResponse postToken(MultiValueMap<String, String> body) {
        try {
            return Objects.requireNonNull(
                    restClient.post()
                            .uri(TOKEN_URL)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(body)
                            .retrieve()
                            .body(TokenResponse.class));
        } catch (RestClientException e) {
            throw new ConexionGoogleNoDisponibleException("Google ha rechazado la peticion de token", e);
        }
    }

    private static ResenaGoogle toResena(ReviewEntry review) {
        return new ResenaGoogle(
                review.reviewId(),
                review.reviewer() != null ? review.reviewer().displayName() : "Anonimo",
                review.reviewer() != null ? review.reviewer().profilePhotoUrl() : null,
                puntuacionDesdeStarRating(review.starRating()),
                review.comment(),
                toLocalDateTime(review.createTime()),
                toLocalDateTime(review.updateTime()));
    }

    private static int puntuacionDesdeStarRating(String starRating) {
        return switch (starRating) {
            case "ONE" -> 1;
            case "TWO" -> 2;
            case "THREE" -> 3;
            case "FOUR" -> 4;
            case "FIVE" -> 5;
            case null -> {
                log.warn("Reseña de Google sin starRating, se contabiliza como 1 estrella por defecto");
                yield 1;
            }
            default -> {
                log.warn("starRating de Google desconocido '{}', se contabiliza como 1 estrella por defecto", starRating);
                yield 1;
            }
        };
    }

    private static LocalDateTime toLocalDateTime(String rfc3339) {
        return rfc3339 == null ? null : OffsetDateTime.parse(rfc3339).toLocalDateTime();
    }

    // --- DTOs internos de mapeo JSON (Jackson), no expuestos fuera de este cliente. ---

    // El endpoint de token es OAuth 2.0 estandar (RFC 6749): JSON en snake_case, a diferencia
    // del resto de APIs de Google usadas aqui (camelCase) - de ahi los @JsonProperty explicitos.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("token_type") String tokenType,
            String scope) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AccountsResponse(List<AccountEntry> accounts, String nextPageToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AccountEntry(String name, String accountName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LocationsResponse(List<LocationEntry> locations, String nextPageToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LocationEntry(String name, String title) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReviewsResponse(List<ReviewEntry> reviews, String nextPageToken, Double averageRating, Integer totalReviewCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReviewEntry(
            String reviewId, ReviewerEntry reviewer, String starRating, String comment, String createTime, String updateTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReviewerEntry(String displayName, String profilePhotoUrl, Boolean isAnonymous) {
    }
}
