package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.entity.Empleado;
import org.springframework.core.io.Resource;

/**
 * Punto unico para el envio de correo de toda la aplicacion. Cualquier vista/modulo nuevo que
 * necesite enviar un email (no solo Nominas) debe anadir su metodo aqui y en
 * {@link EmailServiceImpl}, en vez de instanciar JavaMailSender por su cuenta.
 */
public interface EmailService {

    void enviarNomina(Empleado destinatario, Resource archivo, String nombreArchivoParaAdjunto,
            int periodoMes, int periodoAnio);

    /**
     * Fase 6.1 (recuperacion de contraseña): {@code tokenEnClaro} solo vive en memoria hasta
     * este punto - nunca se persiste, solo su hash (ver RecuperacionPasswordService).
     */
    void enviarRecuperacionPassword(Empleado destinatario, String tokenEnClaro);
}
