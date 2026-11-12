package com.facturacion.sistemaweb.service;

import java.util.Locale;
import java.util.regex.Pattern;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import com.facturacion.sistemaweb.model.FacturaVista;

/**
 * Envía al cliente el comprobante de una factura ya confirmada en la base.
 * Nunca lanza excepciones: un fallo de correo no debe deshacer ni duplicar la factura.
 */
@Service @Profile("sqlserver") @PreAuthorize("isAuthenticated()")
public class FacturaCorreoService {
    public enum Estado { ENVIADO, SIN_CORREO, NO_CONFIGURADO, FALLO }
    public record Resultado(Estado estado,String destino) {
        public String mensaje() {
            return switch(estado) {
                case ENVIADO -> "La factura se envió al correo del cliente: "+destino+".";
                case SIN_CORREO -> "La factura no se envió por correo: el cliente no tiene un correo registrado en esta factura.";
                case NO_CONFIGURADO -> "La factura no se envió por correo: el servidor de correo no está configurado (SMTP_USERNAME y SMTP_PASSWORD).";
                case FALLO -> "No se pudo enviar la factura al correo "+destino+". Puedes reintentarlo desde el detalle.";
            };
        }
        public boolean enviado() { return estado==Estado.ENVIADO; }
    }
    private static final Logger log=LoggerFactory.getLogger(FacturaCorreoService.class);
    private static final Pattern CORREO=Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final FacturaService facturas;
    private final JavaMailSender remitente;
    private final ITemplateEngine plantillas;
    private final String correoRemitente;
    private final String nombreRemitente;

    public FacturaCorreoService(FacturaService facturas,JavaMailSender remitente,ITemplateEngine plantillas,
            @Value("${app.facturas.mail-from:${spring.mail.username:}}") String correoRemitente,
            @Value("${app.facturas.mail-nombre:Sistema de Facturación}") String nombreRemitente) {
        this.facturas=facturas;this.remitente=remitente;this.plantillas=plantillas;
        this.correoRemitente=correoRemitente==null?"":correoRemitente.strip();this.nombreRemitente=nombreRemitente;
    }

    /** Consulta la factura con los permisos de la sesión actual y la envía al correo histórico del cliente. */
    public Resultado enviar(Long idFactura) {
        FacturaVista factura=facturas.consultar(idFactura);
        String destino=factura.contacto().correo()==null?"":factura.contacto().correo().strip();
        if(destino.isEmpty() || !CORREO.matcher(destino).matches()) {
            log.info("Factura {}: no se envió por correo, el cliente no tiene correo válido.",idFactura);
            return new Resultado(Estado.SIN_CORREO,null);
        }
        if(correoRemitente.isEmpty()) {
            log.warn("Factura {}: no se envió por correo, falta SMTP_USERNAME en esta instancia.",idFactura);
            return new Resultado(Estado.NO_CONFIGURADO,destino);
        }
        try {
            var contexto=new Context(Locale.forLanguageTag("es-EC"));
            contexto.setVariable("factura",factura);
            String html=plantillas.process("correo/factura",contexto);
            var mensaje=remitente.createMimeMessage();
            // Multipart: texto plano + HTML alternativos.
            var helper=new MimeMessageHelper(mensaje,true,"UTF-8");
            helper.setFrom(correoRemitente,nombreRemitente);helper.setTo(destino);
            helper.setSubject("Factura #"+factura.id()+" - Sistema de Facturación");
            helper.setText(textoPlano(factura),html);
            remitente.send(mensaje);
            log.info("Factura {}: enviada por correo al cliente.",idFactura);
            return new Resultado(Estado.ENVIADO,destino);
        } catch(MessagingException | java.io.UnsupportedEncodingException | RuntimeException ex) {
            // Solo la clase: el mensaje del servidor SMTP puede incluir datos de la cuenta.
            // Clase de la causa raíz (p. ej. AuthenticationFailedException = contraseña de aplicación incorrecta).
            Throwable raiz=ex;while(raiz.getCause()!=null && raiz.getCause()!=raiz) raiz=raiz.getCause();
            log.warn("No se pudo enviar la factura {} por correo ({} / {}).",idFactura,ex.getClass().getSimpleName(),raiz.getClass().getSimpleName());
            return new Resultado(Estado.FALLO,destino);
        }
    }

    private static String textoPlano(FacturaVista f) {
        var texto=new StringBuilder("Factura #").append(f.id()).append("\n")
            .append("Cliente: ").append(f.cliente()).append(" (").append(f.cedula()).append(")\n\n");
        for(var linea:f.lineas()) texto.append("- ").append(linea.nombre()).append(" x ").append(linea.cantidadVisible())
            .append(": $ ").append(linea.totalVisible()).append("\n");
        texto.append("\nSubtotal: $ ").append(f.subtotalVisible()).append("\nIVA: $ ").append(f.ivaVisible())
            .append("\nTotal: $ ").append(f.totalVisible()).append("\n");
        if(f.pago()!=null) {
            texto.append("Forma de pago: ").append(f.pago().nombre()).append("\n");
            if(!f.pago().detalleVisible().isEmpty()) texto.append(f.pago().detalleVisible()).append("\n");
        }
        return texto.append("\nGracias por su compra.").toString();
    }
}
