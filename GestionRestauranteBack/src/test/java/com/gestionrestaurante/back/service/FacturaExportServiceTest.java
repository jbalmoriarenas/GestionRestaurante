package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** sanearNombreCarpeta es la unica parte de FacturaExportService que tiene sentido testear de
 * forma aislada (sin BBDD ni almacenamiento): el resto depende de FacturaRepository y
 * FileStorageService, cubiertos indirectamente via FacturaControllerTest. */
class FacturaExportServiceTest {

    @Test
    void sanearNombreCarpeta_quitaSecuenciasDeDoblePunto() {
        String resultado = FacturaExportService.sanearNombreCarpeta("../../etc/passwd");

        assertThat(resultado).doesNotContain("..");
    }

    @Test
    void sanearNombreCarpeta_quitaBarras() {
        assertThat(FacturaExportService.sanearNombreCarpeta("facturas/julio")).isEqualTo("facturas_julio");
        assertThat(FacturaExportService.sanearNombreCarpeta("facturas\\julio")).isEqualTo("facturas_julio");
    }

    @Test
    void sanearNombreCarpeta_dejaIntactoUnNombreYaLimpio() {
        assertThat(FacturaExportService.sanearNombreCarpeta("facturas_julio_2026")).isEqualTo("facturas_julio_2026");
    }

    @Test
    void sanearNombreCarpeta_nuloOVacioDevuelveNull() {
        assertThat(FacturaExportService.sanearNombreCarpeta(null)).isNull();
        assertThat(FacturaExportService.sanearNombreCarpeta("")).isNull();
        assertThat(FacturaExportService.sanearNombreCarpeta("   ")).isNull();
    }
}
