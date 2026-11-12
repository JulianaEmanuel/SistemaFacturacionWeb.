package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.service.*;

class FacturaCalculoTest {
    final FacturaCalculoService calculo=new FacturaCalculoService();
    Producto producto(String precio,String iva) { var p=new Producto();p.setPrecioUnitario(new BigDecimal(precio));p.setIvaProducto(new BigDecimal(iva));return p; }
    static FacturaLineaForm linea(long id,String cantidad) { var l=new FacturaLineaForm();l.setIdProducto(id);l.setCantidad(new BigDecimal(cantidad));return l; }
    @Test void subtotalIvaYTotalConCuatroDecimales() {
        var r=calculo.calcular(producto("12.3456","15.00"),new BigDecimal("2.50"));
        assertEquals(new BigDecimal("30.8640"),r.subtotal());assertEquals(new BigDecimal("4.6296"),r.iva());assertEquals(new BigDecimal("35.4936"),r.total());
    }
    @Test void redondeaHalfUpPorLinea() {
        var r=calculo.calcular(producto("0.0001","50.00"),new BigDecimal("0.50"));
        assertEquals(new BigDecimal("0.0001"),r.subtotal());assertEquals(new BigDecimal("0.0001"),r.iva());assertEquals(new BigDecimal("0.0002"),r.total());
    }
    @Test void admitePrecioEImpuestoCero() {
        assertEquals(new BigDecimal("0.0000"),calculo.calcular(producto("0","0"),BigDecimal.ONE).total());
    }
    @ParameterizedTest @ValueSource(strings={"0.00","5.00","12.50","15.00"})
    void respetaIvaConfiguradoEnProducto(String porcentaje) {
        var r=calculo.calcular(producto("100.0000",porcentaje),BigDecimal.ONE);
        assertEquals(new BigDecimal(porcentaje).setScale(4),r.iva());
        assertEquals(new BigDecimal("100.0000").add(r.iva()),r.total());
    }
    @Test void porcentajeVisibleNoModificaElHistorico() {
        var linea=new FacturaVista.Linea(1L,"Producto",BigDecimal.ONE,BigDecimal.TEN,new BigDecimal("15.00"),BigDecimal.TEN,new BigDecimal("1.5000"),new BigDecimal("11.5000"));
        assertEquals("15 %",linea.porcentajeVisible());assertEquals(new BigDecimal("15.00"),linea.porcentaje());
    }
    @Test void presentacionUsaHalfUpSinAlterarImporteAlmacenado() {
        BigDecimal almacenado=new BigDecimal("1.0050");
        assertEquals("1.01",FacturaVista.dinero(almacenado));assertEquals(new BigDecimal("1.0050"),almacenado);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","0.001","1000000","1E+1000000"})
    void rechazaCantidadesFueraDeColumna(String value) { assertThrows(FacturaException.class,()->calculo.cantidad(new BigDecimal(value))); }
    @Test void rechazaCantidadNula() { assertThrows(FacturaException.class,()->calculo.cantidad(null)); }
    @Test void acumulaDuplicadosYOrdenaIdsParaBloquear() {
        var result=calculo.acumular(List.of(linea(9,"1.25"),linea(2,"2"),linea(9,"0.75")));
        assertEquals(List.of(2L,9L),new ArrayList<>(result.keySet()));assertEquals(new BigDecimal("2.00"),result.get(9L));
    }
    @Test void validaSumaAcumulada() {
        assertThrows(FacturaException.class,()->calculo.acumular(List.of(linea(1,"999999.99"),linea(1,"0.01"))));
    }
    @Test void rechazaSinLineasYProductoInvalido() {
        assertThrows(FacturaException.class,()->calculo.acumular(List.of()));
        assertThrows(FacturaException.class,()->calculo.acumular(List.of(linea(-1,"1"))));
        assertThrows(FacturaException.class,()->calculo.acumular(Collections.nCopies(201,linea(1,"1"))));
    }
    @Test void validaPrecioIvaYDesbordamientoDelTotal() {
        assertThrows(FacturaException.class,()->calculo.calcular(producto("10000000000","0"),BigDecimal.ONE));
        assertThrows(FacturaException.class,()->calculo.calcular(producto("1","1000"),BigDecimal.ONE));
        assertThrows(FacturaException.class,()->calculo.calcular(producto("9999999999.9999","999.99"),new BigDecimal("999999.99")));
        assertThrows(FacturaException.class,()->calculo.importe(new BigDecimal("100000000000000")));
    }
}
