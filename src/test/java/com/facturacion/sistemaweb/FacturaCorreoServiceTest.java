package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import com.facturacion.sistemaweb.model.FacturaVista;
import com.facturacion.sistemaweb.service.*;

class FacturaCorreoServiceTest {
    FacturaService facturas=mock(FacturaService.class);
    JavaMailSender remitente=mock(JavaMailSender.class);
    SpringTemplateEngine plantillas=new SpringTemplateEngine();

    @BeforeEach void preparar() {
        var resolver=new ClassLoaderTemplateResolver();resolver.setPrefix("templates/");resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");plantillas.setTemplateResolver(resolver);
        when(remitente.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }
    FacturaCorreoService servicio(String desde) { return new FacturaCorreoService(facturas,remitente,plantillas,desde,"Sistema de Facturación"); }
    FacturaVista factura(String correo,String cliente) {
        var linea=new FacturaVista.Linea(3L,"Arroz <b>premium</b>",new BigDecimal("2"),new BigDecimal("10.0000"),new BigDecimal("15.00"),
            new BigDecimal("20.0000"),new BigDecimal("3.0000"),new BigDecimal("23.0000"));
        return new FacturaVista(99L,LocalDateTime.of(2026,11,11,10,30),"0912345678",cliente,"Ana Paz",
            linea.subtotal(),linea.iva(),linea.total(),List.of(linea),new FacturaVista.Contacto("Av. 1",null,correo,true),
            new FacturaVista.Pago("Efectivo",linea.total()));
    }
    static String html(MimeMessage mensaje) throws Exception {
        mensaje.saveChanges();
        String html=buscar(mensaje.getContent(),"text/html");
        if(html==null) throw new AssertionError("Sin parte HTML");
        if(buscar(mensaje.getContent(),"text/plain")==null) throw new AssertionError("Sin alternativa de texto plano");
        return html;
    }
    static String buscar(Object contenido,String tipo) throws Exception {
        if(!(contenido instanceof MimeMultipart partes)) return null;
        for(int i=0;i<partes.getCount();i++) {
            var parte=partes.getBodyPart(i);
            if(parte.isMimeType(tipo)) return (String)parte.getContent();
            String interno=buscar(parte.getContent(),tipo);
            if(interno!=null) return interno;
        }
        return null;
    }

    @Test void enviaAlCorreoHistoricoConDetalleEscapado() throws Exception {
        when(facturas.consultar(99L)).thenReturn(factura(" cliente@example.invalid ","<script>alert(1)</script> Pérez"));
        var resultado=servicio("ventas@example.invalid").enviar(99L);
        assertEquals(FacturaCorreoService.Estado.ENVIADO,resultado.estado());
        var captor=ArgumentCaptor.forClass(MimeMessage.class);verify(remitente,times(1)).send(captor.capture());
        var mensaje=captor.getValue();
        assertEquals("cliente@example.invalid",mensaje.getAllRecipients()[0].toString());
        assertEquals("Factura #99 - Sistema de Facturación",mensaje.getSubject());
        String cuerpo=html(mensaje);
        assertTrue(cuerpo.contains("Factura #99"));assertTrue(cuerpo.contains("$ 23.00"));assertTrue(cuerpo.contains("Efectivo"));
        assertTrue(cuerpo.contains("&lt;script&gt;"),"El nombre del cliente debe escaparse");
        assertFalse(cuerpo.contains("<script>"));assertFalse(cuerpo.contains("<b>premium</b>"));
        assertTrue(resultado.mensaje().contains("cliente@example.invalid"));
    }
    @Test void sinCorreoNoIntentaEnviar() {
        for(String correo:Arrays.asList(null,"","  ","no-es-correo")) {
            when(facturas.consultar(99L)).thenReturn(factura(correo,"Cliente"));
            assertEquals(FacturaCorreoService.Estado.SIN_CORREO,servicio("ventas@example.invalid").enviar(99L).estado());
        }
        verify(remitente,never()).send(any(MimeMessage.class));
    }
    @Test void sinRemitenteConfiguradoNoIntentaEnviar() {
        when(facturas.consultar(99L)).thenReturn(factura("cliente@example.invalid","Cliente"));
        var resultado=servicio(" ").enviar(99L);
        assertEquals(FacturaCorreoService.Estado.NO_CONFIGURADO,resultado.estado());
        assertTrue(resultado.mensaje().contains("SMTP_USERNAME"));
        verify(remitente,never()).send(any(MimeMessage.class));
    }
    @Test void falloSmtpSeInformaSinPropagar() {
        when(facturas.consultar(99L)).thenReturn(factura("cliente@example.invalid","Cliente"));
        doThrow(new MailSendException("Authentication failed")).when(remitente).send(any(MimeMessage.class));
        var resultado=servicio("ventas@example.invalid").enviar(99L);
        assertEquals(FacturaCorreoService.Estado.FALLO,resultado.estado());assertFalse(resultado.enviado());
    }
}
