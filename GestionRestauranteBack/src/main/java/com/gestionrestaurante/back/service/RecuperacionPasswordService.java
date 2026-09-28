package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.entity.TokenRecuperacionPassword;
import com.gestionrestaurante.back.exception.EnvioRecuperacionPasswordFallidoException;
import com.gestionrestaurante.back.exception.SolicitudRecuperacionDemasiadoFrecuenteException;
import com.gestionrestaurante.back.exception.TokenRecuperacionInvalidoException;
import com.gestionrestaurante.back.repository.EmpleadoRepository;
import com.gestionrestaurante.back.repository.TokenRecuperacionPasswordRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flujo de "olvide mi contraseña" (back/fase6-1-back.md): un token de un solo uso, guardado
 * como hash SHA-256 (nunca en claro), con expiracion corta. Reutiliza EmpleadoService para fijar
 * la contraseña nueva - la misma operacion que ya hace un Admin al resetear la de un empleado,
 * solo cambia quien la autoriza.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecuperacionPasswordService {

    private static final int TOKEN_BYTES = 32;

    /**
     * Cooldown minimo entre solicitudes para un mismo email (revision de codigo, back/fase6-1-back.md):
     * el endpoint es publico y sin autenticacion, asi que sin esto cualquiera podria spamear de
     * correos a un empleado concreto o agotar la cuota/reputacion de la cuenta SMTP. En memoria
     * (no en BBDD) porque es un dato puramente transitorio - mismo supuesto de una sola instancia
     * ya documentado para ConexionGoogleService.estadosPendientes.
     */
    private static final Duration COOLDOWN_SOLICITUD = Duration.ofSeconds(60);
    private final Map<String, Instant> ultimaSolicitudPorEmail = new ConcurrentHashMap<>();

    private final EmpleadoRepository empleadoRepository;
    private final TokenRecuperacionPasswordRepository tokenRepository;
    private final EmpleadoService empleadoService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.recuperacion-password.expiracion-minutos}")
    private int expiracionMinutos;

    /**
     * Responde siempre igual, exista o no el email (revision de codigo, back/fase6-1-back.md):
     * antes se distinguia con un 404 especifico, pero eso permite enumerar que emails estan
     * dados de alta como empleados activos contra un endpoint publico sin autenticacion -
     * decision revertida para seguir el estandar (OWASP ASVS) de no filtrar esa informacion.
     */
    @Transactional
    public void solicitar(String email) {
        aplicarCooldown(email);

        empleadoRepository.findByEmailAndActivoTrue(email).ifPresent(empleado -> {
            // Invalida (borra) los tokens anteriores no usados: un enlace mas antiguo, todavia
            // dentro de su ventana de expiracion, deja de servir en cuanto se pide uno nuevo.
            tokenRepository.deleteAll(tokenRepository.findByEmpleado_IdAndFechaUsoIsNull(empleado.getId()));

            String tokenEnClaro = generarToken();
            TokenRecuperacionPassword token = new TokenRecuperacionPassword();
            token.setEmpleado(empleado);
            token.setTokenHash(hashear(tokenEnClaro));
            token.setFechaCreacion(LocalDateTime.now());
            token.setFechaExpiracion(LocalDateTime.now().plusMinutes(expiracionMinutos));
            tokenRepository.save(token);

            try {
                emailService.enviarRecuperacionPassword(empleado, tokenEnClaro);
            } catch (MailException e) {
                throw new EnvioRecuperacionPasswordFallidoException(e);
            }
        });
    }

    private void aplicarCooldown(String email) {
        Instant ahora = Instant.now();
        Instant ultima = ultimaSolicitudPorEmail.put(email, ahora);
        if (ultima != null && ultima.plus(COOLDOWN_SOLICITUD).isAfter(ahora)) {
            throw new SolicitudRecuperacionDemasiadoFrecuenteException();
        }
    }

    @Transactional
    public void restablecer(String tokenEnClaro, String nuevaPassword) {
        TokenRecuperacionPassword token = tokenRepository.findByTokenHash(hashear(tokenEnClaro))
                .orElseThrow(TokenRecuperacionInvalidoException::new);

        if (token.getFechaUso() != null || token.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            throw new TokenRecuperacionInvalidoException();
        }

        empleadoService.resetearPassword(token.getEmpleado().getId(), nuevaPassword);
        token.setFechaUso(LocalDateTime.now());
    }

    private String generarToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashear(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es un algoritmo estandar garantizado por la JVM (JLS), nunca deberia faltar.
            throw new IllegalStateException(e);
        }
    }
}
