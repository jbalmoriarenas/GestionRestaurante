package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

class LocalFileStorageServiceTest {

    @TempDir
    Path directorioTemporal;

    @Test
    void guardarYCargar_devuelveElMismoContenido() throws IOException {
        LocalFileStorageService service = new LocalFileStorageService(directorioTemporal.toString());
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "nomina-enero.pdf", "application/pdf", "contenido-de-prueba".getBytes(StandardCharsets.UTF_8));

        String rutaGuardada = service.guardar(archivo, "nominas");

        assertThat(rutaGuardada).startsWith("nominas/").endsWith(".pdf");
        assertThat(rutaGuardada).doesNotContain("nomina-enero");

        Resource recurso = service.cargarComoResource(rutaGuardada);
        assertThat(recurso.getContentAsByteArray())
                .isEqualTo("contenido-de-prueba".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void eliminar_borraElArchivoDelDisco() {
        LocalFileStorageService service = new LocalFileStorageService(directorioTemporal.toString());
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "nomina.pdf", "application/pdf", "x".getBytes(StandardCharsets.UTF_8));
        String rutaGuardada = service.guardar(archivo, "nominas");

        service.eliminar(rutaGuardada);

        assertThat(Files.exists(directorioTemporal.resolve(rutaGuardada))).isFalse();
    }

    @Test
    void mover_conservaElContenidoYCambiaDeCarpeta() {
        LocalFileStorageService service = new LocalFileStorageService(directorioTemporal.toString());
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "nomina.pdf", "application/pdf", "y".getBytes(StandardCharsets.UTF_8));
        String rutaOrigen = service.guardar(archivo, "nominas/pendientes");

        String rutaDestino = service.mover(rutaOrigen, "nominas");

        assertThat(rutaDestino).startsWith("nominas/").doesNotContain("pendientes");
        assertThat(Files.exists(directorioTemporal.resolve(rutaOrigen))).isFalse();
        assertThat(Files.exists(directorioTemporal.resolve(rutaDestino))).isTrue();
    }
}
