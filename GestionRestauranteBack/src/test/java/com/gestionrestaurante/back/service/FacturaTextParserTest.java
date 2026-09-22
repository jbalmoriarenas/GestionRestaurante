package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
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

    @Test
    void extraerFecha_devuelveLaFechaNumericaDeLaFactura() {
        var fecha = FacturaTextParser.extraerFecha(FACTURA_LUZ);

        assertThat(fecha).contains(LocalDate.of(2026, 3, 15));
    }

    @Test
    void extraerFecha_textoSinFechaDevuelveVacio() {
        var fecha = FacturaTextParser.extraerFecha("Sin ninguna fecha reconocible aqui");

        assertThat(fecha).isEmpty();
    }

    @Test
    void extraerFecha_reconoceFormatoTextualEnEspanol() {
        var fecha = FacturaTextParser.extraerFecha("Emitida el 5 de marzo de 2026 en Madrid");

        assertThat(fecha).contains(LocalDate.of(2026, 3, 5));
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
    void extraerCamposAdicionales_incluyeEtiquetaNoConsumida() {
        var campos = FacturaTextParser.extraerCamposAdicionales(FACTURA_LUZ, Set.of("Total factura"));

        assertThat(campos).containsEntry("CUPS", "ES0021000000000001JN0F");
    }

    @Test
    void extraerCamposAdicionales_ignoraEtiquetaYaConsumida() {
        var campos = FacturaTextParser.extraerCamposAdicionales(FACTURA_LUZ, Set.of("CUPS"));

        assertThat(campos).doesNotContainKey("CUPS");
    }
}
