package com.facturacion.sistemaweb.security;

import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Component;

@Component @Profile("sqlserver")
public class CorreoRecuperacion {
    private final JavaMailSender remitente;
    private final String correoRemitente;
    private final String baseUrl;
    private final String servidorSmtp;
    public CorreoRecuperacion(JavaMailSender remitente,
            @Value("${app.recovery.mail-from:${spring.mail.username:}}") String correoRemitente,
            @Value("${app.recovery.public-base-url:http://localhost:8081}") String baseUrl,
            @Value("${spring.mail.host:}") String servidorSmtp) {
        this.remitente=remitente;this.correoRemitente=correoRemitente;this.baseUrl=baseUrl.replaceAll("/+$","");
        this.servidorSmtp=servidorSmtp;
    }

    public void enviarVerificacion(String destino,String token) {
        enviar(destino,"Verifica el correo de tu cuenta",
            enlace("/verificar-correo",token)+"\n\nEl enlace vence en 15 minutos. Si no solicitaste esta verificación, ignora este mensaje.");
    }

    public void enviarRestablecimiento(String destino,String token) {
        enviar(destino,"Restablece la contraseña de tu cuenta",
            enlace("/restablecer-contrasenia",token)+"\n\nEl enlace vence en 15 minutos. Si no solicitaste el cambio, ignora este mensaje.");
    }

    private String enlace(String ruta,String token) {
        return org.springframework.web.util.UriComponentsBuilder.fromUriString(baseUrl).path(ruta)
            .queryParam("token",token).build().encode().toUriString();
    }

    private void enviar(String destino,String asunto,String texto) {
        if(servidorSmtp==null || servidorSmtp.isBlank())
            throw new MailSendException("SMTP_HOST no está configurado.");
        try {
            var mensaje=remitente.createMimeMessage();
            var helper=new MimeMessageHelper(mensaje,false,"UTF-8");
            helper.setFrom(correoRemitente);helper.setTo(destino);helper.setSubject(asunto);helper.setText(texto,false);
            remitente.send(mensaje);
        } catch(MessagingException | MailException ex) {
            throw new MailSendException("No se pudo enviar el correo de seguridad.",ex);
        }
    }
}
