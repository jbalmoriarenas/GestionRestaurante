package com.gestionrestaurante.back.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Primera implementacion de FileStorageService (back/fase3-1-back.md): almacenamiento en
 * disco local del propio servidor. Si en el futuro se despliega en varias instancias detras
 * de un balanceador, cada instancia solo veria los archivos que ella misma escribio
 * (documentado como riesgo bajo, fuera de alcance mientras el despliegue sea de una sola
 * instancia).
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path basePath;

    public LocalFileStorageService(@Value("${app.storage.base-path}") String basePathConfigurado) {
        this.basePath = Path.of(basePathConfigurado).toAbsolutePath().normalize();
    }

    @Override
    public String guardar(MultipartFile archivo, String subcarpeta) {
        String nombreFisico = UUID.randomUUID() + extensionDe(archivo.getOriginalFilename());
        Path carpetaDestino = resolverDentroDeBase(subcarpeta);
        Path destino = carpetaDestino.resolve(nombreFisico);
        try {
            Files.createDirectories(carpetaDestino);
            archivo.transferTo(destino);
        } catch (IOException e) {
            throw new UncheckedIOException("No se ha podido guardar el archivo " + archivo.getOriginalFilename(), e);
        }
        return subcarpeta + "/" + nombreFisico;
    }

    @Override
    public Resource cargarComoResource(String rutaRelativa) {
        Path ruta = resolverDentroDeBase(rutaRelativa);
        try {
            Resource resource = new UrlResource(ruta.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new UncheckedIOException(new IOException("El archivo " + rutaRelativa + " no existe o no se puede leer"));
            }
            return resource;
        } catch (java.net.MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void eliminar(String rutaRelativa) {
        try {
            Files.deleteIfExists(resolverDentroDeBase(rutaRelativa));
        } catch (IOException e) {
            throw new UncheckedIOException("No se ha podido eliminar el archivo " + rutaRelativa, e);
        }
    }

    @Override
    public String mover(String rutaRelativaOrigen, String subcarpetaDestino) {
        Path origen = resolverDentroDeBase(rutaRelativaOrigen);
        String nombreFisico = origen.getFileName().toString();
        Path carpetaDestino = resolverDentroDeBase(subcarpetaDestino);
        Path destino = carpetaDestino.resolve(nombreFisico);
        try {
            Files.createDirectories(carpetaDestino);
            Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("No se ha podido mover el archivo " + rutaRelativaOrigen, e);
        }
        return subcarpetaDestino + "/" + nombreFisico;
    }

    /**
     * Resuelve {@code rutaRelativa} dentro de basePath y normaliza, para evitar que una ruta
     * con ".." se escape del directorio de almacenamiento (path traversal). El nombre fisico
     * de cada archivo ya es un UUID generado por esta misma clase, pero se protege igual la
     * lectura por si alguna ruta llegara corrupta desde la BBDD.
     */
    private Path resolverDentroDeBase(String rutaRelativa) {
        Path resuelto = basePath.resolve(rutaRelativa).normalize();
        if (!resuelto.startsWith(basePath)) {
            throw new IllegalArgumentException("Ruta de archivo invalida: " + rutaRelativa);
        }
        return resuelto;
    }

    private static String extensionDe(String nombreOriginal) {
        if (nombreOriginal == null) {
            return "";
        }
        int puntoIndex = nombreOriginal.lastIndexOf('.');
        return puntoIndex >= 0 ? nombreOriginal.substring(puntoIndex) : "";
    }
}
