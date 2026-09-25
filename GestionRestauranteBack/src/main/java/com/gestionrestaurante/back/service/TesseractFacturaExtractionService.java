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
import org.apache.pdfbox.text.PDFTextStripper;
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

    /** Por debajo de este numero de caracteres, un PDF se considera "sin capa de texto" (un
     * escaneado guardado como PDF, o una pagina en blanco) y se procesa por OCR en vez de
     * confiar en el texto extraido - un PDF nativo real siempre trae bastante mas que esto. */
    private static final int MIN_CARACTERES_TEXTO_NATIVO = 20;

    @Value("${app.ocr.tessdata-path:}")
    private String tessDataPath;

    @Value("${app.ocr.idioma:spa}")
    private String idiomaOcr;

    @Override
    public ExtraccionResultado extraer(InputStream documento, String contentType, TipoProveedor tipo) {
        String textoOcr = extraerTexto(documento, contentType);

        FacturaTextParser.ImportesDetectados importes = FacturaTextParser.extraerImportes(textoOcr);
        var fechaImpresionSugerida = FacturaTextParser.extraerFechaImpresion(textoOcr).orElse(null);
        var fechaEntregaSugerida = FacturaTextParser.extraerFechaEntrega(textoOcr).orElse(null);

        return new ExtraccionResultado(fechaImpresionSugerida, fechaEntregaSugerida,
                importes.base(), importes.iva(), importes.total());
    }

    private String extraerTexto(InputStream documento, String contentType) {
        try {
            if ("application/pdf".equals(contentType)) {
                return extraerTextoDePdf(documento.readAllBytes());
            }
            BufferedImage imagen = ImageIO.read(documento);
            if (imagen == null) {
                throw new OcrNoDisponibleException(new IOException("Formato de imagen no reconocido"));
            }
            return ejecutarOcr(preprocesar(imagen));
        } catch (IOException e) {
            throw new OcrNoDisponibleException(e);
        }
    }

    /**
     * La mayoria de facturas subidas son PDF generados digitalmente (no escaneados): tienen una
     * capa de texto exacta ya embebida, muy superior a cualquier resultado de OCR sobre una
     * imagen rasterizada de ese mismo PDF. Se usa esa capa de texto siempre que exista; solo se
     * rasteriza y se hace OCR (pagina a pagina - back/fase2-3-back.md, antes solo se procesaba
     * la primera pagina, lo que dejaba sin leer facturas con el resumen de totales en la
     * segunda hoja, p.ej. Coca-Cola) cuando el PDF no trae texto (un escaneado real).
     */
    private String extraerTextoDePdf(byte[] bytes) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            String textoNativo = new PDFTextStripper().getText(pdf);
            if (textoNativo.strip().length() >= MIN_CARACTERES_TEXTO_NATIVO) {
                return textoNativo;
            }

            PDFRenderer renderer = new PDFRenderer(pdf);
            StringBuilder textoOcr = new StringBuilder();
            for (int pagina = 0; pagina < pdf.getNumberOfPages(); pagina++) {
                BufferedImage imagen = renderer.renderImageWithDPI(pagina, DPI_RENDER_PDF, ImageType.RGB);
                textoOcr.append(ejecutarOcr(preprocesar(imagen))).append('\n');
            }
            return textoOcr.toString();
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
