package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.dto.ExtraccionResultado;
import com.gestionrestaurante.back.entity.TipoProveedor;
import java.io.InputStream;

/**
 * Abstraccion sobre el motor de extraccion (OCR) de facturas (back/fase2-3-back.md): permite
 * sustituir la implementacion gratuita (Tesseract) por un motor de pago (p.ej. un LLM
 * multimodal) mas adelante sin tocar FacturaService ni el flujo de confirmacion, si la
 * precision resulta insuficiente en la practica.
 */
public interface FacturaExtractionService {

    /**
     * Extrae texto y propone valores estructurados a partir de {@code documento}. Nunca
     * persiste nada - el resultado es siempre una previsualizacion que el Admin revisa y
     * confirma explicitamente antes de guardar.
     */
    ExtraccionResultado extraer(InputStream documento, String contentType, TipoProveedor tipo);
}
