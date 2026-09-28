package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.entity.ConfiguracionEmpresa;
import com.gestionrestaurante.back.entity.Empleado;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

/**
 * Primera vez que el proyecto envia email (back/fase3-3-back.md). Cualquier excepcion de
 * JavaMailSender se propaga tal cual; NominaService.enviar es quien decide como traducirla sin
 * dejar el estado de la nomina a medias.
 *
 * <p>El envio real se controla con {@code nominas.enabled} (false en produccion hasta que el
 * SMTP este listo, true en application-local.properties): en false no se llama a
 * JavaMailSender, solo se deja constancia en el log.
 */
@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final boolean nominasEnabled;
    private final Configuration freemarkerConfig;
    private final String frontBaseUrl;
    private final int recuperacionExpiracionMinutos;
    private final ConfiguracionEmpresaService configuracionEmpresaService;
    private final String mailUsername;

    public EmailServiceImpl(JavaMailSender mailSender,
            @Value("${nominas.enabled:false}") boolean nominasEnabled,
            Configuration freemarkerConfig,
            @Value("${app.front.base-url}") String frontBaseUrl,
            @Value("${app.recuperacion-password.expiracion-minutos}") int recuperacionExpiracionMinutos,
            ConfiguracionEmpresaService configuracionEmpresaService,
            @Value("${spring.mail.username:}") String mailUsername) {
        this.mailSender = mailSender;
        this.nominasEnabled = nominasEnabled;
        this.freemarkerConfig = freemarkerConfig;
        this.frontBaseUrl = frontBaseUrl;
        this.recuperacionExpiracionMinutos = recuperacionExpiracionMinutos;
        this.configuracionEmpresaService = configuracionEmpresaService;
        this.mailUsername = mailUsername;
    }

    @Override
    public void enviarNomina(Empleado destinatario, Resource archivo, String nombreArchivoParaAdjunto,
            int periodoMes, int periodoAnio) {
        if (!nominasEnabled) {
            log.info("nominas.enabled=false: se omite el envio real del correo de nomina a {} ({}/{})",
                    destinatario.getEmail(), periodoMes, periodoAnio);
            return;
        }
        try {
            String nombreMes = Month.of(periodoMes).getDisplayName(TextStyle.FULL, Locale.of("es", "ES"));

            freemarkerConfig.setClassForTemplateLoading(this.getClass(), "/email/");
            Template plantilla = freemarkerConfig.getTemplate("template_nomina.ftl");
            Map<String, Object> modelo = Map.of(
                    "nombre", destinatario.getNombre(),
                    "nombreMes", nombreMes,
                    "periodoAnio", periodoAnio);
            String html = FreeMarkerTemplateUtils.processTemplateIntoString(plantilla, modelo);

            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true);
            aplicarRemitente(helper);

            helper.setTo(destinatario.getEmail());
            helper.setSubject("Tu nomina de " + nombreMes + "/" + periodoAnio);
            helper.setText(html, true);
            helper.addAttachment(nombreArchivoParaAdjunto, archivo);

            mailSender.send(mensaje);
            log.info("Correo de nomina enviado a {} ({}/{})", destinatario.getEmail(), periodoMes, periodoAnio);
        } catch (MessagingException e) {
            throw new MailParseException(e);
        } catch (IOException | TemplateException e) {
            throw new IllegalStateException("No se ha podido renderizar la plantilla de nomina", e);
        }
    }

    /**
     * Primer uso de FreeMarker en el proyecto (back/fase6-1-back.md), mismo patron ya usado en
     * realdoohprojectapi: {@code setClassForTemplateLoading} apunta al directorio de plantillas
     * en tiempo de peticion (no en el constructor) para no acoplar la config del bean al primer
     * uso concreto. El token nunca se loguea ni viaja en claro salvo dentro de este correo.
     */
    @Override
    public void enviarRecuperacionPassword(Empleado destinatario, String tokenEnClaro) {
        try {
            freemarkerConfig.setClassForTemplateLoading(this.getClass(), "/email/");
            Template plantilla = freemarkerConfig.getTemplate("template_recuperacion_password.ftl");

            String enlace = frontBaseUrl + "/restablecer-contrasena?token="
                    + URLEncoder.encode(tokenEnClaro, StandardCharsets.UTF_8);
            Map<String, Object> modelo = Map.of(
                    "nombre", destinatario.getNombre(),
                    "enlace", enlace,
                    "expiracionMinutos", recuperacionExpiracionMinutos);
            String html = FreeMarkerTemplateUtils.processTemplateIntoString(plantilla, modelo);

            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true);
            aplicarRemitente(helper);
            helper.setTo(destinatario.getEmail());
            helper.setSubject("Recupera tu contraseña");
            helper.setText(html, true);

            mailSender.send(mensaje);
            log.info("Correo de recuperacion de contraseña enviado a {}", destinatario.getEmail());
        } catch (MessagingException e) {
            throw new MailParseException(e);
        } catch (IOException | TemplateException e) {
            throw new IllegalStateException("No se ha podido renderizar la plantilla de recuperacion de contraseña", e);
        }
    }

    /**
     * Remitente configurable desde /configuracion (ConfiguracionEmpresa.emailRemitente,
     * back/fase5-1-back.md); si no esta configurado, cae a spring.mail.username (la cuenta
     * SMTP autenticada). Con Gmail SMTP en concreto, la direccion debe coincidir con la cuenta
     * autenticada (o un alias "Enviar como" verificado en esa cuenta) o Gmail la rechaza/reescribe.
     */
    private void aplicarRemitente(MimeMessageHelper helper) throws MessagingException {
        ConfiguracionEmpresa configuracion = configuracionEmpresaService.obtener();
        String direccion = configuracion.getEmailRemitente();
        if (direccion == null || direccion.isBlank()) {
            direccion = mailUsername;
        }
        if (direccion == null || direccion.isBlank()) {
            log.warn("Sin remitente configurado (ni emailRemitente en /configuracion ni spring.mail.username): "
                    + "el correo se enviara sin cabecera From explicita");
            return;
        }
        try {
            helper.setFrom(direccion, configuracion.getNombreEmpresa());
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("Remitente con codificacion no soportada: " + direccion, e);
        }
    }
}
