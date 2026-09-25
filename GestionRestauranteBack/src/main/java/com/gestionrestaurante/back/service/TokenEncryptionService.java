package com.gestionrestaurante.back.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Cifra/descifra el refresh token de Google antes de guardarlo (bbdd/fase4-1-bbdd.md,
 * back/fase4-1-back.md) - primer dato cifrado en reposo de todo el proyecto. Un refresh
 * token concede acceso de pie a los datos de negocio de la cuenta de Google conectada, mas
 * sensible que una contraseña de empleado (que se guarda con BCrypt, un hash irreversible):
 * este token si necesita poder recuperarse en claro para llamar a la API de Google en cada
 * sincronizacion, asi que se cifra (AES/GCM) en vez de hashearse.
 *
 * <p>La clave, a diferencia de {@code app.jwt.secret}, no tiene valor por defecto: el arranque
 * falla explicitamente si {@code app.google.token-encryption-key} no esta definida, para no
 * arrancar nunca con una clave de ejemplo conocida protegiendo un dato tan sensible.
 */
@Service
public class TokenEncryptionService {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANO_IV_BYTES = 12;
    private static final int TAMANO_TAG_BITS = 128;

    private final SecretKeySpec clave;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenEncryptionService(@Value("${app.google.token-encryption-key}") String claveBase64) {
        byte[] claveBytes = Base64.getDecoder().decode(claveBase64);
        this.clave = new SecretKeySpec(claveBytes, "AES");
    }

    /** Devuelve {@code Base64(iv) + ":" + Base64(textoCifrado+tag)}: el IV va delante en claro
     * (no es secreto, solo debe ser unico por cifrado) porque hace falta para descifrar. */
    public String cifrar(String textoPlano) {
        try {
            byte[] iv = new byte[TAMANO_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(TAMANO_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(cifrado);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se ha podido cifrar el token", e);
        }
    }

    public String descifrar(String textoCifrado) {
        try {
            String[] partes = textoCifrado.split(":", 2);
            byte[] iv = Base64.getDecoder().decode(partes[0]);
            byte[] cifrado = Base64.getDecoder().decode(partes[1]);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(TAMANO_TAG_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se ha podido descifrar el token", e);
        }
    }
}
