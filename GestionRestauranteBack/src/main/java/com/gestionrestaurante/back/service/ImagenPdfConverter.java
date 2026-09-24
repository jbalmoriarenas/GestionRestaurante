package com.gestionrestaurante.back.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Convierte una foto de factura (JPEG/PNG/WEBP) a un PDF de una sola pagina antes de guardarla
 * (back/fase2-1-back.md, confirmado con el usuario): asi el archivo almacenado queda siempre en
 * el mismo formato con independencia de como la haya subido el Admin. La imagen se centra y
 * escala (conservando proporcion) sobre una pagina A4, en orientacion horizontal si la foto es
 * mas ancha que alta.
 */
public final class ImagenPdfConverter {

    private ImagenPdfConverter() {
    }

    public static byte[] convertirAPdf(byte[] imagenBytes) throws IOException {
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(imagenBytes));
        if (imagen == null) {
            throw new IOException("Formato de imagen no reconocido");
        }

        boolean horizontal = imagen.getWidth() > imagen.getHeight();
        PDRectangle tamanoPagina = horizontal
                ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
                : PDRectangle.A4;

        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage(tamanoPagina);
            documento.addPage(pagina);
            PDImageXObject imagenPdf = LosslessFactory.createFromImage(documento, imagen);

            float escala = Math.min(
                    tamanoPagina.getWidth() / imagen.getWidth(),
                    tamanoPagina.getHeight() / imagen.getHeight());
            float anchoDibujo = imagen.getWidth() * escala;
            float altoDibujo = imagen.getHeight() * escala;
            float x = (tamanoPagina.getWidth() - anchoDibujo) / 2;
            float y = (tamanoPagina.getHeight() - altoDibujo) / 2;

            try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                contenido.drawImage(imagenPdf, x, y, anchoDibujo, altoDibujo);
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    /** Sustituye la extension de {@code nombreOriginal} por ".pdf" (p.ej. "foto.jpg" ->
     * "foto.pdf"), o la añade si no tenia ninguna. */
    public static String nombrePdfDesde(String nombreOriginal) {
        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            return "factura.pdf";
        }
        int puntoIndex = nombreOriginal.lastIndexOf('.');
        String base = puntoIndex >= 0 ? nombreOriginal.substring(0, puntoIndex) : nombreOriginal;
        return base + ".pdf";
    }
}
