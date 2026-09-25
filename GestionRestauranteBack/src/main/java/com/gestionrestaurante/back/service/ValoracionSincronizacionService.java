package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.dto.ResultadoSincronizacionDTO;
import com.gestionrestaurante.back.entity.ConexionGoogleBusiness;
import com.gestionrestaurante.back.entity.Valoracion;
import com.gestionrestaurante.back.repository.ConexionGoogleBusinessRepository;
import com.gestionrestaurante.back.repository.ValoracionRepository;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient.PaginaResenas;
import com.gestionrestaurante.back.service.google.GoogleBusinessProfileClient.ResenaGoogle;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sincroniza las reseñas de Google con {@code valoraciones} (back/fase4-1-back.md). Reutilizada
 * tanto por {@link com.gestionrestaurante.back.scheduler.ValoracionSincronizacionScheduler}
 * (cada 6h por defecto) como por el endpoint de sincronizacion manual - un unico punto con la
 * logica real, ninguno de los dos duplica nada.
 *
 * <p>Sin conexion activa, {@link #sincronizar()} no hace nada (no es un error). Cualquier
 * fallo de red/API de Google se atrapa aqui: nunca debe tumbar el job programado ni dejar
 * {@code ultimoError} sin actualizar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValoracionSincronizacionService {

    private static final Long CONEXION_ID = 1L;

    private final ConexionGoogleBusinessRepository conexionRepository;
    private final ValoracionRepository valoracionRepository;
    private final GoogleBusinessProfileClient googleClient;
    private final TokenEncryptionService tokenEncryptionService;

    @Transactional
    public ResultadoSincronizacionDTO sincronizar() {
        ConexionGoogleBusiness conexion = conexionRepository.findById(CONEXION_ID).orElse(null);
        if (conexion == null) {
            return ResultadoSincronizacionDTO.vacio();
        }

        try {
            String refreshToken = tokenEncryptionService.descifrar(conexion.getRefreshTokenCifrado());
            String accessToken = googleClient.refrescarAccessToken(refreshToken);

            int nuevas = 0;
            int actualizadas = 0;
            String pageToken = null;
            do {
                PaginaResenas pagina = googleClient.listarResenas(accessToken, conexion.getLocationResourceName(), pageToken);
                for (ResenaGoogle resena : pagina.resenas()) {
                    if (upsert(resena)) {
                        nuevas++;
                    } else {
                        actualizadas++;
                    }
                }
                pageToken = pagina.siguientePageToken();
            } while (pageToken != null && !pageToken.isBlank());

            conexion.setFechaUltimaSincronizacion(LocalDateTime.now());
            conexion.setUltimoError(null);
            conexionRepository.save(conexion);

            return new ResultadoSincronizacionDTO(nuevas, actualizadas, nuevas + actualizadas, null);
        } catch (RuntimeException e) {
            log.warn("Fallo al sincronizar valoraciones desde Google", e);
            String mensaje = mensajeError(e);
            conexion.setFechaUltimaSincronizacion(LocalDateTime.now());
            conexion.setUltimoError(mensaje);
            conexionRepository.save(conexion);
            return ResultadoSincronizacionDTO.error(mensaje);
        }
    }

    /** @return {@code true} si la reseña era nueva, {@code false} si ya existia y se ha actualizado. */
    private boolean upsert(ResenaGoogle resena) {
        Valoracion valoracion = valoracionRepository.findByGoogleReviewId(resena.googleReviewId()).orElse(null);
        boolean esNueva = valoracion == null;
        if (esNueva) {
            valoracion = new Valoracion();
            valoracion.setGoogleReviewId(resena.googleReviewId());
        }
        valoracion.setAutorNombre(resena.autorNombre());
        valoracion.setAutorFotoUrl(resena.autorFotoUrl());
        valoracion.setPuntuacion(resena.puntuacion());
        valoracion.setComentario(resena.comentario());
        valoracion.setFechaPublicacion(resena.fechaPublicacion());
        valoracion.setFechaActualizacion(resena.fechaActualizacion());
        valoracion.setFechaSincronizacion(LocalDateTime.now());
        valoracionRepository.save(valoracion);
        return esNueva;
    }

    private static String mensajeError(RuntimeException e) {
        String mensaje = e.getMessage();
        return mensaje != null ? mensaje : Objects.requireNonNullElse(e.getCause(), e).getClass().getSimpleName();
    }
}
