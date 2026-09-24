package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Fixtures fijas simulando texto OCR real (back/fase2-3-back.md) - sin Spring ni BBDD. */
class FacturaTextParserTest {

    private static final String FACTURA_LUZ = """
            IBERDROLA CLIENTES S.A.U.
            Factura: 2026-000123
            Fecha de factura: 15/03/2026
            CUPS: ES0021000000000001JN0F
            Periodo de facturacion: 01/02/2026 - 28/02/2026
            Base imponible: 100,00 €
            IVA (21%): 21,00 €
            Total factura: 121,00 €
            """;

    /** Ticket de venta al por mayor con varios tipos de IVA en la misma factura (formato
     * habitual de distribuidores tipo Makro): la fecha de impresion aparece antes que la de
     * venta, y el IVA se agrega en una tabla-resumen por tramo, no con la etiqueta "IVA". */
    private static final String TICKET_MULTI_IVA = """
            Makro Distribucion Mayorista, S.A.
            Fecha de venta: 28/07/2026 11:51
            Fecha impresión: 28/07/2026 11:55
            Fecha de entrega: 29/07/2026 09:00
            Número de bultos: 48 Peso Total: 3,590 KG Envases: 0 Importe 428,16
            Mercancía % IMP Total IMP
            251,50 1=10,00% 25,15
            170,37 2=21,00% 35,78
            6,29 5= 4,00% 0,25
            428,16 61,18
            Total a pagar 489,34
            Valor total descuentos 4,11-
            """;

    @Test
    void extraerFechaImpresion_devuelveLaFechaDeLaLineaDeImpresion() {
        var fecha = FacturaTextParser.extraerFechaImpresion(TICKET_MULTI_IVA);

        assertThat(fecha).contains(LocalDate.of(2026, 7, 28));
    }

    @Test
    void extraerFechaEntrega_devuelveLaFechaDeLaLineaDeEntrega() {
        var fecha = FacturaTextParser.extraerFechaEntrega(TICKET_MULTI_IVA);

        assertThat(fecha).contains(LocalDate.of(2026, 7, 29));
    }

    @Test
    void extraerFechaImpresion_sinLineaReconocibleDevuelveVacio() {
        var fecha = FacturaTextParser.extraerFechaImpresion(FACTURA_LUZ);

        assertThat(fecha).isEmpty();
    }

    @Test
    void extraerImportes_detectaBaseIvaYTotalPorRatio21Porciento() {
        var importes = FacturaTextParser.extraerImportes(FACTURA_LUZ);

        assertThat(importes.total()).isEqualByComparingTo(new BigDecimal("121.00"));
        assertThat(importes.base()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(importes.iva()).isEqualByComparingTo(new BigDecimal("21.00"));
    }

    @Test
    void extraerImportes_sinRatioReconocibleSoloProponeElTotal() {
        var importes = FacturaTextParser.extraerImportes("Importe 1: 45,00 € Importe 2: 200,00 €");

        assertThat(importes.total()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(importes.base()).isNull();
        assertThat(importes.iva()).isNull();
    }

    @Test
    void extraerImportes_sumaLosTramosDeIvaDeLaTablaResumen() {
        var importes = FacturaTextParser.extraerImportes(TICKET_MULTI_IVA);

        assertThat(importes.total()).isEqualByComparingTo(new BigDecimal("489.34"));
        assertThat(importes.base()).isEqualByComparingTo(new BigDecimal("428.16"));
        assertThat(importes.iva()).isEqualByComparingTo(new BigDecimal("61.18"));
    }
}
