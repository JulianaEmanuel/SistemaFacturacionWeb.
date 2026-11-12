package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.facturacion.sistemaweb.model.FacturaForm;
import com.facturacion.sistemaweb.model.FacturaVista;
import com.facturacion.sistemaweb.service.FacturaException;
import com.facturacion.sistemaweb.service.FacturaPagoReglas;

class FacturaPagoReglasTest {
    static final BigDecimal TOTAL=new BigDecimal("11.6419");
    FacturaForm form() { return new FacturaForm(); }
    FacturaForm tarjeta(String marca,String ultimos,String autorizacion) {
        var f=form();f.setMarcaTarjeta(marca);f.setUltimosDigitos(ultimos);f.setReferencia(autorizacion);return f;
    }

    @Test void efectivoCalculaElCambioDesdeElTotalDeCuatroDecimales() {
        var f=form();f.setMontoRecibido(new BigDecimal("20.00"));
        var d=FacturaPagoReglas.validar("EFECTIVO",f,TOTAL);
        assertEquals(new BigDecimal("20.00"),d.montoRecibido());assertEquals(new BigDecimal("8.36"),d.cambio());
        assertNull(d.banco());assertNull(d.referencia());assertNull(d.ultimosDigitos());
    }
    @Test void efectivoConElTotalExactoMostradoEnPantallaSeAcepta() {
        // La pantalla muestra 11.64 (HALF_UP): pagar exactamente eso no debe fallar por un centavo.
        var f=form();f.setMontoRecibido(new BigDecimal("11.64"));
        assertEquals(new BigDecimal("0.00"),FacturaPagoReglas.validar("EFECTIVO",f,TOTAL).cambio());
        // 11.6450 se muestra como 11.65 (HALF_UP, igual que facturas.js): pagar 11.65 deja cambio 0.
        var redondeoArriba=form();redondeoArriba.setMontoRecibido(new BigDecimal("11.65"));
        assertEquals(new BigDecimal("0.00"),FacturaPagoReglas.validar("EFECTIVO",redondeoArriba,new BigDecimal("11.6450")).cambio());
        var unCentavoMenos=form();unCentavoMenos.setMontoRecibido(new BigDecimal("11.64"));
        assertThrows(FacturaException.class,()->FacturaPagoReglas.validar("EFECTIVO",unCentavoMenos,new BigDecimal("11.6450")));
    }
    @Test void efectivoVacioEsPagoExacto() {
        var d=FacturaPagoReglas.validar("EFECTIVO",form(),TOTAL);
        assertEquals(new BigDecimal("11.64"),d.montoRecibido());assertEquals(new BigDecimal("0.00"),d.cambio());
    }
    @Test void efectivoInsuficienteSeRechaza() {
        var f=form();f.setMontoRecibido(new BigDecimal("11.63"));
        var ex=assertThrows(FacturaException.class,()->FacturaPagoReglas.validar("EFECTIVO",f,TOTAL));
        assertTrue(ex.getMessage().contains("menor que el total"));
    }
    @Test void efectivoDescartaDatosDeOtrasFormas() {
        var f=tarjeta("Visa","1234","ABC123");f.setBanco("Banco Pichincha");f.setCuotas(3);
        var d=FacturaPagoReglas.validar("EFECTIVO",f,TOTAL);
        assertNull(d.marcaTarjeta());assertNull(d.ultimosDigitos());assertNull(d.referencia());assertNull(d.banco());assertNull(d.cuotas());
    }

    @Test void transferenciaRequiereBancoYComprobante() {
        var sinBanco=form();sinBanco.setReferencia("TR-001");
        assertThrows(FacturaException.class,()->FacturaPagoReglas.validar("TRANSFERENCIA",sinBanco,TOTAL));
        var sinComprobante=form();sinComprobante.setBanco("Banco Pichincha");
        assertThrows(FacturaException.class,()->FacturaPagoReglas.validar("TRANSFERENCIA",sinComprobante,TOTAL));
        var ok=form();ok.setBanco("  Banco   Pichincha ");ok.setReferencia("TR-00123");ok.setMontoRecibido(new BigDecimal("50"));
        var d=FacturaPagoReglas.validar("TRANSFERENCIA",ok,TOTAL);
        assertEquals("Banco Pichincha",d.banco());assertEquals("TR-00123",d.referencia());assertNull(d.montoRecibido());assertNull(d.cambio());
    }

    @Test void tarjetaDeCreditoGuardaSoloDatosDelVoucher() {
        var f=tarjeta("Visa","4321","abc123");f.setCuotas(6);f.setBanco("no corresponde");
        var d=FacturaPagoReglas.validar("CREDITO",f,TOTAL);
        assertEquals("Visa",d.marcaTarjeta());assertEquals("4321",d.ultimosDigitos());assertEquals("ABC123",d.referencia());
        assertEquals(6,d.cuotas());assertNull(d.banco());assertNull(d.montoRecibido());
    }
    @Test void creditoSinCuotasEsCorrienteYDebitoNoTieneCuotas() {
        assertEquals(1,FacturaPagoReglas.validar("CREDITO",tarjeta("Mastercard","0001","123456"),TOTAL).cuotas());
        var debito=tarjeta("Mastercard","0001","123456");debito.setCuotas(12);
        assertNull(FacturaPagoReglas.validar("DEBITO",debito,TOTAL).cuotas());
    }
    @ParameterizedTest @ValueSource(strings={"12a4","12345","123",""})
    void ultimosDigitosInvalidosSeRechazan(String ultimos) {
        var ex=assertThrows(FacturaException.class,()->FacturaPagoReglas.validar("DEBITO",tarjeta("Visa",ultimos,"123456"),TOTAL));
        assertTrue(ex.getMessage().contains("últimos 4 dígitos"));
    }
    @Test void unNumeroDeTarjetaCompletoNoCabeComoAutorizacion() {
        assertThrows(FacturaException.class,()->FacturaPagoReglas.validar("CREDITO",tarjeta("Visa","1111","4111111111111111"),TOTAL));
    }
    @Test void marcaFueraDeLaListaSeRechaza() {
        assertThrows(FacturaException.class,()->FacturaPagoReglas.validar("DEBITO",tarjeta("<script>","1234","123456"),TOTAL));
        assertThrows(FacturaException.class,()->FacturaPagoReglas.validar("DEBITO",tarjeta(null,"1234","123456"),TOTAL));
    }
    @Test void cuotasFueraDeRangoSeRechazan() {
        var f=tarjeta("Visa","1234","123456");f.setCuotas(60);
        assertThrows(FacturaException.class,()->FacturaPagoReglas.validar("CREDITO",f,TOTAL));
    }
    @Test void codigoDesconocidoONuloNoGuardaDetalle() {
        assertEquals(FacturaPagoReglas.Detalle.VACIO,FacturaPagoReglas.validar(null,tarjeta("Visa","1234","123456"),TOTAL));
        assertEquals(FacturaPagoReglas.Detalle.VACIO,FacturaPagoReglas.validar("CHEQUE",form(),TOTAL));
    }

    @Test void resumenVisibleEnmascaraLaTarjeta() {
        var pago=new FacturaVista.Pago("Tarjeta de crédito",TOTAL,null,null,null,"ABC123","Visa","4321",3);
        assertEquals("Visa •••• 4321 · Autorización ABC123 · 3 cuotas",pago.detalleVisible());
        var efectivo=new FacturaVista.Pago("Efectivo",TOTAL,new BigDecimal("20.0000"),new BigDecimal("8.3600"),null,null,null,null,null);
        assertEquals("Recibido $ 20.00 · Cambio $ 8.36",efectivo.detalleVisible());
        var transferencia=new FacturaVista.Pago("Transferencia",TOTAL,null,null,"Produbanco","TR-1",null,null,null);
        assertEquals("Produbanco · Comprobante TR-1",transferencia.detalleVisible());
        assertEquals("",new FacturaVista.Pago("Efectivo",TOTAL).detalleVisible());
    }
}
