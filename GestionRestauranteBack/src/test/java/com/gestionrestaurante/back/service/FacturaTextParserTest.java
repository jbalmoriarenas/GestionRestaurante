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

    /** Factura de servicios (reparacion) con el desglose en una caja aparte: "IVA:" comparte
     * linea con la etiqueta "IBAN" (una linea con un unico importe, valida), mientras que la
     * fila de la tabla "Desglose IVA" repite base/iva/importe-a-pagar en la MISMA linea de
     * texto (tres importes) - antes de la correccion, sumarImportesEnLineasConPalabra sumaba
     * ciegamente todos los importes de cualquier linea con "iva", dando un IVA disparatado. */
    private static final String FACTURA_HOSTELOR = """
            MANTENIMIENTOS DEL ORIENTE 2001, S.L.
            FACTURA Número I//2342 Fecha 17/08/2026
            Desplazamiento 1,00 25,00 25,00
            Mano de Obra reparación 1,00 46,00 46,00
            Chiclé 2,00 13,10 26,20
            Cond. de pago: TRANSFERENCIA CUENTA Base / Total bruto: 97,20 Total bruto 97,20
            IBAN: ES48 2103 7039 4900 3001 7428 IVA: 20,41
            Total sin retención: 117,61 Total 117,61
            DESGLOSE IVA VENCIMIENTOS
            IVA 21 % 21 97,20 20,41 17/08/2026 117,61 Pendiente
            """;

    @Test
    void extraerImportes_conFilaDeTablaQueRepiteImportesEnLaMismaLineaDeIva_noSumaTodoElloComoIva() {
        var importes = FacturaTextParser.extraerImportes(FACTURA_HOSTELOR);

        assertThat(importes.total()).isEqualByComparingTo(new BigDecimal("117.61"));
        assertThat(importes.iva()).isEqualByComparingTo(new BigDecimal("20.41"));
        assertThat(importes.base()).isEqualByComparingTo(new BigDecimal("97.20"));
    }

    /** Factura de dos paginas (Coca-Cola): la pagina 1 solo trae detalle de lineas y dos
     * "TOTAL ..." que NO son el total de la factura (TOTAL PRODUCTOS, TOTAL PUNTO VERDE); el
     * resumen real (bases/impuestos/total) esta integro en la pagina 2. Antes de la correccion
     * solo se procesaba la pagina 1 del PDF, asi que este texto ni siquiera llegaba a existir. */
    private static final String FACTURA_COCA_COLA_DOS_PAGINAS = """
            Factura 2724572627 31.07.2026
            0000090343599 544 COCACOLA VR237 C24. 11,00 25,44 279,84
            0000090357732 554 COCACOLA ZER VR237 C24 11,00 25,44 279,84
            TOTAL PRODUCTOS 482,16
            TOTAL PUNTO VERDE 0,66
            Factura 2724572627 31.07.2026
            TIPO BASE IMPONIBLE %IMPUESTOS IMPORTE
            482,82 IVA 21% 101,39
            TOTAL BASES: 482,82 TOTAL IMPUESTOS: 101,39
            TOTAL: 584,21 EUROS
            """;

    @Test
    void extraerImportes_conElTotalEnLaSegundaPagina_loEncuentraYNoConfundeLosSubtotalesDeLaPrimera() {
        var importes = FacturaTextParser.extraerImportes(FACTURA_COCA_COLA_DOS_PAGINAS);

        assertThat(importes.total()).isEqualByComparingTo(new BigDecimal("584.21"));
    }

    /** Factura con el desglose base/IVA/total en una fila de tabla ("315,00 | 10,00 31,50 |
     * 346,50€") donde el 10,00 es en realidad el % de IVA formateado con dos decimales, no un
     * importe - una ambiguedad real del propio documento que esta heuristica basada en texto
     * plano no puede resolver de forma fiable. El total SI se identifica bien (es el importe
     * mayor de la factura); base/iva se quedan sin proponer a proposito, coherente con el
     * comportamiento documentado en extraerImportes() cuando no hay suficiente informacion. */
    private static final String FACTURA_GONZALEZ_VALDES = """
            GONZÁLEZ VALDÉS
            FACTURA A 81278 FECHA 30/06/26 RESUMEN MENSUAL. GIROS DIA 20
            354 ALBARÁN A-98897 FECHA 05/06/2026
            ANCHOAS DON BOCARTE (D. OCT.) A. V. 36,000 8,75 315,00
            BASE IMPONIBLE I.V.A.( %) R.E. TOTAL
            315,00 10,00 31,50 346,50€
            """;

    @Test
    void extraerImportes_conElPorcentajeDeIvaFormateadoComoImporte_soloProponeElTotal() {
        var importes = FacturaTextParser.extraerImportes(FACTURA_GONZALEZ_VALDES);

        assertThat(importes.total()).isEqualByComparingTo(new BigDecimal("346.50"));
        assertThat(importes.base()).isNull();
        assertThat(importes.iva()).isNull();
    }
}
