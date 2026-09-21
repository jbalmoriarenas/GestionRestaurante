package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.entity.Empleado;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Primera vez que el proyecto envia email (back/fase3-3-back.md): sin motor de plantillas,
 * un texto plano basta para el alcance de esta fase. Cualquier excepcion de JavaMailSender se
 * propaga tal cual; NominaService.enviar es quien decide como traducirla sin dejar el estado
 * de la nomina a medias.
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

    public EmailServiceImpl(JavaMailSender mailSender,
            @Value("${nominas.enabled:false}") boolean nominasEnabled) {
        this.mailSender = mailSender;
        this.nominasEnabled = nominasEnabled;
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
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true);
            String nombreMes = Month.of(periodoMes).getDisplayName(TextStyle.FULL, Locale.of("es", "ES"));

            helper.setTo(destinatario.getEmail());
            helper.setSubject("Tu nomina de " + nombreMes + "/" + periodoAnio);
            helper.setText("Hola " + destinatario.getNombre() + ",\n\n"
                    + "Adjuntamos tu nomina correspondiente a " + nombreMes + " de " + periodoAnio + ".\n\n"
                    + "Un saludo.");
            helper.addAttachment(nombreArchivoParaAdjunto, archivo);

            mailSender.send(mensaje);
        } catch (MessagingException e) {
            throw new MailParseException(e);
        }
    }
}
