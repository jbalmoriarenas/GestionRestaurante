package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenEncryptionServiceTest {

    // Clave de prueba (32 bytes aleatorios fijos, no una clave real) - suficiente para probar
    // el algoritmo, nunca se usaria en produccion (back/fase4-1-back.md).
    private final TokenEncryptionService service =
            new TokenEncryptionService(Base64.getEncoder().encodeToString("clave-de-prueba-32-bytes-exactos".getBytes()));

    @Test
    void cifrarYDescifrar_devuelveElTextoOriginal() {
        String original = "1//refresh-token-de-ejemplo-de-google";

        String cifrado = service.cifrar(original);
        String descifrado = service.descifrar(cifrado);

        assertThat(descifrado).isEqualTo(original);
    }

    @Test
    void cifrar_elTextoResultanteNoContieneElTextoPlano() {
        String original = "1//refresh-token-de-ejemplo-de-google";

        String cifrado = service.cifrar(original);

        assertThat(cifrado).doesNotContain(original);
    }

    @Test
    void cifrar_dosLlamadasSobreElMismoTextoDanResultadosDistintos() {
        // El IV es aleatorio por cifrado (back/fase4-1-back.md): dos cifrados del mismo texto
        // no deben coincidir nunca.
        String original = "mismo-texto";

        assertThat(service.cifrar(original)).isNotEqualTo(service.cifrar(original));
    }
}
