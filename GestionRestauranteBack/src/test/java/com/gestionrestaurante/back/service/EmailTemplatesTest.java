package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;

import freemarker.template.Configuration;
import freemarker.template.Template;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

/**
 * Verifica que las plantillas de email (EmailServiceImpl) renderizan sin lanzar excepcion con
 * el modelo exacto que les pasa el servicio - revision de codigo, back/fase6-1-back.md: sin
 * esto, un typo en una clave del modelo (o en un {@code ${...}} de la plantilla) solo se
 * detectaria en produccion, en el primer envio real.
 */
class EmailTemplatesTest {

    private static Configuration cargarConfiguracion() {
        // Version fija (no Configuration.getVersion()): FreeMarker desaconseja usar la version
        // de la libreria como incompatibleImprovements, ver warning en su propia documentacion.
        Configuration configuracion = new Configuration(Configuration.VERSION_2_3_32);
        configuracion.setClassForTemplateLoading(EmailServiceImpl.class, "/email/");
        return configuracion;
    }

    @Test
    void plantillaNomina_renderizaConElModeloDeEmailServiceImpl() throws Exception {
        Template plantilla = cargarConfiguracion().getTemplate("template_nomina.ftl");
        Map<String, Object> modelo = Map.of(
                "nombre", "Ana",
                "nombreMes", "septiembre",
                "periodoAnio", 2026);

        String html = FreeMarkerTemplateUtils.processTemplateIntoString(plantilla, modelo);

        assertThat(html).contains("Ana", "septiembre", "2026");
    }

    @Test
    void plantillaRecuperacionPassword_renderizaConElModeloDeEmailServiceImpl() throws Exception {
        Template plantilla = cargarConfiguracion().getTemplate("template_recuperacion_password.ftl");
        Map<String, Object> modelo = Map.of(
                "nombre", "Ana",
                "enlace", "http://localhost:4200/restablecer-contrasena?token=abc",
                "expiracionMinutos", 30);

        String html = FreeMarkerTemplateUtils.processTemplateIntoString(plantilla, modelo);

        assertThat(html).contains("Ana", "http://localhost:4200/restablecer-contrasena?token=abc", "30");
    }
}
