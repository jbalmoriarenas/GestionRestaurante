package com.gestionrestaurante.back.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

/**
 * Adapta el contenido en memoria de una entrada de ZIP (NominaService.procesarZip,
 * back/fase3-2-back.md) a MultipartFile, para poder reutilizar FileStorageService.guardar
 * (pensado para la subida individual, back/fase3-1-back.md) sin duplicar esa logica.
 */
final class ByteArrayMultipartFile implements MultipartFile {

    private final String nombreOriginal;
    private final String contentType;
    private final byte[] contenido;

    ByteArrayMultipartFile(String nombreOriginal, String contentType, byte[] contenido) {
        this.nombreOriginal = nombreOriginal;
        this.contentType = contentType;
        this.contenido = contenido;
    }

    @Override
    @NonNull
    public String getName() {
        return nombreOriginal;
    }

    @Override
    public String getOriginalFilename() {
        return nombreOriginal;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return contenido.length == 0;
    }

    @Override
    public long getSize() {
        return contenido.length;
    }

    @Override
    @NonNull
    public byte[] getBytes() {
        return contenido;
    }

    @Override
    @NonNull
    public InputStream getInputStream() {
        return new ByteArrayInputStream(contenido);
    }

    @Override
    public void transferTo(@NonNull File dest) throws IOException {
        try (FileOutputStream out = new FileOutputStream(dest)) {
            out.write(contenido);
        }
    }
}
