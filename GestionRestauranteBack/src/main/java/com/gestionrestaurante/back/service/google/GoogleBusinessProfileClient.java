package com.gestionrestaurante.back.service.google;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Abstraccion sobre las llamadas HTTP a Google (OAuth + Business Profile / "Google My
 * Business API v4"), back/fase4-1-back.md - misma filosofia que {@code FacturaExtractionService}/
 * {@code FileStorageService}: aisla el detalle de integracion externa detras de una interfaz,
 * testeable con un mock (no se llama a la API real de Google en tests).
 */
public interface GoogleBusinessProfileClient {

    record TokensGoogle(String accessToken, String refreshToken, long expiraEnSegundos) {
    }

    record UbicacionGoogle(String locationResourceName, String nombre) {
    }

    record ResenaGoogle(
            String googleReviewId,
            String autorNombre,
            String autorFotoUrl,
            int puntuacion,
            String comentario,
            LocalDateTime fechaPublicacion,
            LocalDateTime fechaActualizacion) {
    }

    record PaginaResenas(List<ResenaGoogle> resenas, String siguientePageToken) {
    }

    /** URL de {@code accounts.google.com/o/oauth2/v2/auth} con {@code access_type=offline}
     * (imprescindible para recibir un refresh_token) y {@code prompt=consent} (fuerza a Google
     * a reemitirlo aunque el Admin ya hubiera autorizado antes). */
    String generarUrlConsentimiento(String state);

    /** {@code POST oauth2.googleapis.com/token}, {@code grant_type=authorization_code}. */
    TokensGoogle intercambiarCodigoPorTokens(String code);

    /** Mismo endpoint que {@link #intercambiarCodigoPorTokens}, {@code grant_type=refresh_token}.
     * El access_token nunca se persiste (vive ~1h): se pide uno nuevo antes de cada operacion. */
    String refrescarAccessToken(String refreshToken);

    /** Descubre que ubicacion(es) gestiona la cuenta que acaba de autorizar (API de gestion de
     * cuentas de Google Business Profile). */
    List<UbicacionGoogle> listarUbicaciones(String accessToken);

    /** {@code GET accounts/{accountId}/locations/{locationId}/reviews}, paginado. */
    PaginaResenas listarResenas(String accessToken, String locationResourceName, String pageToken);

    /** {@code POST oauth2.googleapis.com/revoke}. Llamado siempre en modo best-effort por el
     * servicio (back/fase4-1-back.md): un fallo aqui nunca debe impedir desconectar la fila
     * local. */
    void revocarToken(String refreshToken);
}
