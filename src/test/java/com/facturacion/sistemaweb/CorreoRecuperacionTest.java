package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Properties;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import com.facturacion.sistemaweb.security.CorreoRecuperacion;

class CorreoRecuperacionTest {
    @Test void generaElEnlaceYUsaLaCuentaGmailConfiguradaComoRemitente() throws Exception {
        JavaMailSender sender=mock(JavaMailSender.class);
        MimeMessage mensaje=new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mensaje);
        CorreoRecuperacion correo=new CorreoRecuperacion(sender,"cuenta@gmail.com",
                "http://localhost:8081","smtp.gmail.com");

        correo.enviarRestablecimiento("destino@example.test","token-de-prueba");

        assertEquals("cuenta@gmail.com",((InternetAddress)mensaje.getFrom()[0]).getAddress());
        assertEquals("destino@example.test",
                ((InternetAddress)mensaje.getRecipients(Message.RecipientType.TO)[0]).getAddress());
        assertEquals("Restablece la contraseña de tu cuenta",mensaje.getSubject());
        assertTrue(mensaje.getContent().toString()
                .contains("http://localhost:8081/restablecer-contrasenia?token=token-de-prueba"));
        verify(sender).send(mensaje);
    }

    @Test void fallaConMensajeSeguroSiSmtpNoEstaConfigurado() {
        JavaMailSender sender=mock(JavaMailSender.class);
        CorreoRecuperacion correo=new CorreoRecuperacion(sender,"","http://localhost:8081","");

        MailSendException error=assertThrows(MailSendException.class,
                ()->correo.enviarRestablecimiento("destino@example.test","token"));

        assertEquals("SMTP_HOST no está configurado.",error.getMessage());
        verifyNoInteractions(sender);
    }

    @Test void noExponeElDetalleDelErrorSmtpEnElMensajeDeLaExcepcionPublica() {
        JavaMailSender sender=mock(JavaMailSender.class);
        MimeMessage mensaje=new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mensaje);
        doThrow(new MailSendException("SMTP authentication failed: synthetic-password"))
                .when(sender).send(mensaje);
        CorreoRecuperacion correo=new CorreoRecuperacion(sender,"cuenta@gmail.com",
                "http://localhost:8081","smtp.gmail.com");

        MailSendException error=assertThrows(MailSendException.class,
                ()->correo.enviarRestablecimiento("destino@example.test","token"));

        assertEquals("No se pudo enviar el correo de seguridad.",error.getMessage());
        assertFalse(error.getMessage().contains("synthetic-password"));
    }
}
