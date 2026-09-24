package com.gestionrestaurante.back.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraccion sobre el almacenamiento fisico de ficheros (back/fase3-1-back.md): permite
 * cambiar a un almacenamiento externo (S3, Blob Storage) mas adelante sin tocar
 * NominaService, que es el unico consumidor de esta interfaz hasta ahora.
 */
public interface FileStorageService {

    /**
     * Guarda {@code archivo} bajo {@code subcarpeta} con un nombre fisico unico generado por
     * la implementacion (nunca el nombre original del usuario, para evitar colisiones y
     * ataques de path traversal). Devuelve la ruta relativa a persistir.
     */
    String guardar(MultipartFile archivo, String subcarpeta);

    /**
     * Igual que {@link #guardar(MultipartFile, String)} pero a partir de bytes ya en memoria
     * (p.ej. el PDF generado por {@link ImagenPdfConverter} a partir de una foto subida, que no
     * llega como MultipartFile porque no viene directamente de la petición HTTP). {@code
     * nombreOriginal} solo se usa para derivar la extension del nombre fisico.
     */
    String guardar(byte[] contenido, String nombreOriginal, String subcarpeta);

    /** Carga como {@link Resource} el archivo ubicado en {@code rutaRelativa}, para servir su descarga. */
    Resource cargarComoResource(String rutaRelativa);

    /** Borra el archivo ubicado en {@code rutaRelativa}, si existe. */
    void eliminar(String rutaRelativa);

    /**
     * Mueve el archivo ya guardado en {@code rutaRelativaOrigen} a {@code subcarpetaDestino},
     * conservando su nombre fisico. Usado por NominaService.asignarPendiente
     * (back/fase3-2-back.md) para llevar un PDF de "pendientes/" a su ubicacion definitiva
     * sin volver a leer el MultipartFile original (ya esta en disco). Devuelve la nueva ruta
     * relativa a persistir.
     */
    String mover(String rutaRelativaOrigen, String subcarpetaDestino);
}
