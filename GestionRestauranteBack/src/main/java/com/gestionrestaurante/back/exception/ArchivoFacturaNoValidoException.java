package com.gestionrestaurante.back.exception;

/**
 * Distinta de ArchivoNoValidoException (nominas, fase3-1-back.md): esa tiene el mensaje
 * hardcodeado a "debe ser un PDF valido", pero una factura admite ademas imagenes
 * (jpg/png/webp) - back/fase2-1-back.md.
 */
public class ArchivoFacturaNoValidoException extends RuntimeException {

    public ArchivoFacturaNoValidoException() {
        super("El archivo debe ser una imagen (JPEG/PNG/WEBP) o un PDF valido");
    }

    /** Conserva la causa real (p.ej. un IOException al convertir una foto a PDF) para que no
     * se pierda del log un fallo que no sea realmente "archivo invalido". */
    public ArchivoFacturaNoValidoException(Throwable causa) {
        super("El archivo debe ser una imagen (JPEG/PNG/WEBP) o un PDF valido", causa);
    }
}
