package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.dto.ExtraccionResultado;
import com.gestionrestaurante.back.entity.TipoProveedor;
import com.gestionrestaurante.back.exception.OcrNoDisponibleException;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Implementacion gratuita/self-hosted de FacturaExtractionService (back/fase2-3-back.md):
 * Tesseract via Tess4J + PDFBox para rasterizar PDF. Tesseract se instancia de forma
 * perezosa dentro de {@link #extraer}, nunca en el arranque de la aplicacion - si el binario
 * nativo no esta instalado en este entorno, el fallo solo aparece al usar
 * POST /api/facturas/extraer, no al levantar el backend.
 */
@Service
@RequiredArgsConstructor
public class TesseractFacturaExtractionService implements FacturaExtractionService {

    private static final int DPI_RENDER_PDF = 300;

    @Value("${app.ocr.tessdata-path:}")
    private String tessDataPath;

    @Value("${app.ocr.idioma:spa}")
    private String idiomaOcr;

    @Override
    public ExtraccionResultado extraer(InputStream documento, String contentType, TipoProveedor tipo) {
        BufferedImage imagen = cargarComoImagen(documento, contentType);
        String textoOcr = ejecutarOcr(preprocesar(imagen));

        FacturaTextParser.ImportesDetectados importes = FacturaTextParser.extraerImportes(textoOcr);
        var fechaImpresionSugerida = FacturaTextParser.extraerFechaImpresion(textoOcr).orElse(null);
        var fechaEntregaSugerida = FacturaTextParser.extraerFechaEntrega(textoOcr).orElse(null);

        return new ExtraccionResultado(fechaImpresionSugerida, fechaEntregaSugerida,
                importes.base(), importes.iva(), importes.total());
    }

    private BufferedImage cargarComoImagen(InputStream documento, String contentType) {
        try {
            if ("application/pdf".equals(contentType)) {
                byte[] bytes = documento.readAllBytes();
                try (PDDocument pdf = Loader.loadPDF(bytes)) {
                    PDFRenderer renderer = new PDFRenderer(pdf);
                    return renderer.renderImageWithDPI(0, DPI_RENDER_PDF, ImageType.RGB);
                }
            }
            BufferedImage imagen = ImageIO.read(documento);
            if (imagen == null) {
                throw new OcrNoDisponibleException(new IOException("Formato de imagen no reconocido"));
            }
            return imagen;
        } catch (IOException e) {
            throw new OcrNoDisponibleException(e);
        }
    }

    /** Escala de grises + estiramiento de contraste ligero (sin dependencia de OpenCV, ver
     * back/fase2-3-back.md: primer sitio a mejorar si la precision resulta insuficiente). */
    private BufferedImage preprocesar(BufferedImage original) {
        BufferedImage gris = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        gris.getGraphics().drawImage(original, 0, 0, null);
        RescaleOp contraste = new RescaleOp(1.15f, -10f, null);
        return contraste.filter(gris, null);
    }

    private String ejecutarOcr(BufferedImage imagen) {
        try {
            Tesseract tesseract = new Tesseract();
            if (tessDataPath != null && !tessDataPath.isBlank()) {
                tesseract.setDatapath(tessDataPath);
            }
            tesseract.setLanguage(idiomaOcr);
            return tesseract.doOCR(imagen);
        } catch (TesseractException | LinkageError e) {
            throw new OcrNoDisponibleException(e);
        } catch (Error e) {
            // JNA lanza java.lang.Error (p.ej. "Invalid memory access") cuando el motor nativo
            // se inicializa con datos de idioma ausentes o corruptos (tessdata) - no es un
            // LinkageError (eso es solo "no se encuentra la libreria"), pero es el mismo tipo
            // de fallo de entorno, no un bug de la aplicacion.
            throw new OcrNoDisponibleException(e);
        }
    }
}
