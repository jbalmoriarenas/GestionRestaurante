package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.dto.ConexionEstadoDTO;
import com.gestionrestaurante.back.entity.ConexionGoogleBusiness;
import com.gestionrestaurante.back.exception.ConexionGoogleNoDisponibleException;
import com.gestionrestaurante.back.exception.EstadoOAuthInvalidoException;
import com.gestionrestaurante.back.repository.ConexionGoogleBusinessRepository;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient.TokensGoogle;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient.UbicacionGoogle;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flujo OAuth 2.0 completo contra Google (back/fase4-1-back.md): iniciar el consentimiento,
 * completarlo tras el callback, y desconectar. La conexion en si (fila {@code id = 1} de
 * {@code conexion_google_business}) es responsabilidad de este servicio; la sincronizacion de
 * reseñas es {@link ValoracionSincronizacionService}, al que se delega tras completar la
 * conexion (primera sincronizacion inmediata, ver "Decisiones tecnicas" del documento).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConexionGoogleService {

    /** Fila unica de conexion, mismo patron que {@code configuracion_ubicacion}. */
    private static final Long CONEXION_ID = 1L;
    private static final long STATE_TTL_MINUTOS = 10;

    private final ConexionGoogleBusinessRepository conexionRepository;
    private final GoogleBusinessProfileClient googleClient;
    private final TokenEncryptionService tokenEncryptionService;
    private final ValoracionSincronizacionService sincronizacionService;

    /** Estado efimero del flujo OAuth en memoria (TTL 10 min), no en BBDD: dato puramente
     * transitorio, coherente con el supuesto de despliegue de una sola instancia ya
     * documentado en plan-implementacion-general.md - back/fase4-1-back.md, "Decisiones
     * tecnicas". */
    private final Map<String, PendingState> estadosPendientes = new ConcurrentHashMap<>();

    private record PendingState(Long adminId, Instant expiraEn) {
    }

    @Transactional(readOnly = true)
    public ConexionEstadoDTO obtenerEstado() {
        return conexionRepository.findById(CONEXION_ID)
                .map(c -> new ConexionEstadoDTO(true, c.getNombreUbicacion(), c.getFechaConexion(),
                        c.getFechaUltimaSincronizacion(), c.getUltimoError()))
                .orElseGet(ConexionEstadoDTO::desconectado);
    }

    public String iniciarConexion(Long adminId) {
        limpiarEstadosExpirados();
        String state = UUID.randomUUID().toString();
        estadosPendientes.put(state, new PendingState(adminId, Instant.now().plus(STATE_TTL_MINUTOS, ChronoUnit.MINUTES)));
        return googleClient.generarUrlConsentimiento(state);
    }

    @Transactional
    public void completarConexion(String code, String state) {
        PendingState pendiente = estadosPendientes.remove(state);
        if (pendiente == null || pendiente.expiraEn().isBefore(Instant.now())) {
            throw new EstadoOAuthInvalidoException();
        }

        TokensGoogle tokens = googleClient.intercambiarCodigoPorTokens(code);

        // Se asume una unica ubicacion por cuenta conectada (back/fase4-1-back.md, "Analisis y
        // supuestos"): si hubiera mas de una, se conecta la primera.
        List<UbicacionGoogle> ubicaciones = googleClient.listarUbicaciones(tokens.accessToken());
        if (ubicaciones.isEmpty()) {
            throw new ConexionGoogleNoDisponibleException(
                    "La cuenta de Google autorizada no gestiona ninguna ubicacion de Google Business Profile");
        }
        UbicacionGoogle ubicacion = ubicaciones.get(0);

        ConexionGoogleBusiness conexion = conexionRepository.findById(CONEXION_ID).orElseGet(ConexionGoogleBusiness::new);
        conexion.setId(CONEXION_ID);
        conexion.setLocationResourceName(ubicacion.locationResourceName());
        conexion.setNombreUbicacion(ubicacion.nombre());
        conexion.setRefreshTokenCifrado(tokenEncryptionService.cifrar(tokens.refreshToken()));
        conexion.setFechaConexion(LocalDateTime.now());
        conexion.setConectadoPorAdminId(pendiente.adminId());
        conexion.setFechaUltimaSincronizacion(null);
        conexion.setUltimoError(null);
        conexionRepository.save(conexion);

        // Primera sincronizacion inmediata (back/fase4-1-back.md, "Decisiones tecnicas"): el
        // Admin espera ver resultado nada mas conectar, no hasta el siguiente ciclo del job.
        // Un fallo aqui no debe tumbar la conexion recien creada.
        try {
            sincronizacionService.sincronizar();
        } catch (RuntimeException e) {
            log.warn("La sincronizacion inmediata tras conectar con Google ha fallado", e);
        }
    }

    @Transactional
    public void desconectar() {
        conexionRepository.findById(CONEXION_ID).ifPresent(conexion -> {
            try {
                String refreshToken = tokenEncryptionService.descifrar(conexion.getRefreshTokenCifrado());
                googleClient.revocarToken(refreshToken);
            } catch (RuntimeException e) {
                // Best-effort (back/fase4-1-back.md): se borra la fila local igualmente aunque
                // Google no confirme la revocacion.
                log.warn("No se ha podido revocar el token de Google al desconectar", e);
            }
            conexionRepository.delete(conexion);
        });
    }

    private void limpiarEstadosExpirados() {
        Instant ahora = Instant.now();
        estadosPendientes.entrySet().removeIf(entry -> entry.getValue().expiraEn().isBefore(ahora));
    }
}
