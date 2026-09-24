package com.gestionrestaurante.back.exception;

/**
 * Tesseract (motor nativo, no solo la dependencia JVM Tess4J) no esta instalado o no se
 * puede inicializar en este entorno - back/fase2-3-back.md, riesgo medio "dependencia
 * nativa". No debe impedir el arranque de la aplicacion; solo aparece al intentar usar
 * /api/facturas/extraer.
 */
public class OcrNoDisponibleException extends RuntimeException {

    public OcrNoDisponibleException(Throwable causa) {
        super("El motor de extraccion (Tesseract OCR) no esta disponible en este entorno. "
                + "Verifica que este instalado y que app.ocr.tessdata-path apunte a los datos de idioma.", causa);
    }
}
