package com.facturacion.sistemaweb.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import com.facturacion.sistemaweb.model.FacturaForm;

/**
 * Reglas del detalle de pago. El servidor decide qué datos se guardan según el código de la forma de pago.
 * Por seguridad (PCI-DSS) nunca se acepta el número completo de la tarjeta, su vencimiento ni el CVV:
 * solo marca, últimos 4 dígitos y número de autorización del datáfono.
 */
public final class FacturaPagoReglas {
    public static final List<String> MARCAS=List.of("Visa","Mastercard","American Express","Diners Club","Discover","Otra");
    public record Detalle(BigDecimal montoRecibido,BigDecimal cambio,String banco,String referencia,
            String marcaTarjeta,String ultimosDigitos,Integer cuotas) {
        public static final Detalle VACIO=new Detalle(null,null,null,null,null,null,null);
    }
    private FacturaPagoReglas() { }

    private static String texto(String v) { return v==null || v.isBlank()?null:v.strip().replaceAll("\\s+"," "); }

    /** Valida y normaliza. Los campos que no corresponden a la forma de pago se descartan. */
    public static Detalle validar(String codigo,FacturaForm form,BigDecimal total) {
        String tipo=codigo==null?"":codigo.strip().toUpperCase();
        return switch(tipo) {
            case "EFECTIVO" -> efectivo(form.getMontoRecibido(),total);
            case "TRANSFERENCIA" -> transferencia(form.getBanco(),form.getReferencia());
            case "DEBITO" -> tarjeta(form,false);
            case "CREDITO" -> tarjeta(form,true);
            default -> Detalle.VACIO;
        };
    }

    private static Detalle efectivo(BigDecimal recibido,BigDecimal total) {
        BigDecimal aPagar=total.setScale(2,RoundingMode.HALF_UP);
        // Sin monto recibido se entiende pago exacto.
        BigDecimal monto=recibido==null?aPagar:recibido.setScale(2,RoundingMode.HALF_UP);
        if(monto.signum()<0) throw new FacturaException("El monto recibido no puede ser negativo.");
        if(monto.compareTo(aPagar)<0)
            throw new FacturaException("El monto recibido ($ "+monto.toPlainString()+") es menor que el total a pagar ($ "+aPagar.toPlainString()+").");
        if(monto.precision()-monto.scale()>12) throw new FacturaException("El monto recibido es demasiado alto.");
        return new Detalle(monto,monto.subtract(aPagar),null,null,null,null,null);
    }

    private static Detalle transferencia(String bancoTexto,String referenciaTexto) {
        String banco=texto(bancoTexto),referencia=texto(referenciaTexto);
        if(banco==null) throw new FacturaException("Indica el banco de la transferencia.");
        if(banco.length()>80) throw new FacturaException("El nombre del banco admite hasta 80 caracteres.");
        if(referencia==null) throw new FacturaException("Indica el número de comprobante de la transferencia.");
        if(!referencia.matches("[A-Za-z0-9-]{1,30}"))
            throw new FacturaException("El comprobante admite hasta 30 letras, números o guiones, sin espacios.");
        return new Detalle(null,null,banco,referencia,null,null,null);
    }

    private static Detalle tarjeta(FacturaForm form,boolean credito) {
        String marca=texto(form.getMarcaTarjeta()),ultimos=texto(form.getUltimosDigitos()),autorizacion=texto(form.getReferencia());
        if(marca==null || !MARCAS.contains(marca)) throw new FacturaException("Selecciona la marca de la tarjeta.");
        if(ultimos==null || !ultimos.matches("[0-9]{4}"))
            throw new FacturaException("Ingresa solo los últimos 4 dígitos de la tarjeta. Nunca el número completo.");
        // 4 a 12 caracteres: suficiente para una autorización y demasiado corto para un número de tarjeta.
        if(autorizacion==null || !autorizacion.matches("[A-Za-z0-9]{4,12}"))
            throw new FacturaException("Ingresa el número de autorización del voucher (4 a 12 letras o números).");
        Integer cuotas=null;
        if(credito) {
            cuotas=form.getCuotas()==null?1:form.getCuotas();
            if(cuotas<1 || cuotas>48) throw new FacturaException("Las cuotas deben estar entre 1 y 48.");
        }
        return new Detalle(null,null,null,autorizacion.toUpperCase(),marca,ultimos,cuotas);
    }
}
