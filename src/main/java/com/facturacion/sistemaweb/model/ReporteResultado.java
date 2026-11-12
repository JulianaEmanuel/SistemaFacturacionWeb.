package com.facturacion.sistemaweb.model;

import java.math.*;
import java.util.List;

public record ReporteResultado(ReporteTipo tipo,ReporteFiltro.Validado filtro,String alcance,boolean administrador,
        Resumen resumen,List<Fila> filas,List<Punto> grafico,String tipoGrafico,boolean horizontal) {
    public ReporteResultado { filas=List.copyOf(filas);grafico=List.copyOf(grafico); }
    public static BigDecimal cuatro(BigDecimal value) { return value.setScale(4,RoundingMode.UNNECESSARY); }
    public static String dinero(BigDecimal value) { return value.setScale(2,RoundingMode.HALF_UP).toPlainString(); }
    public record Resumen(long facturas,BigDecimal subtotal,BigDecimal iva,BigDecimal total) {
        public Resumen { subtotal=cuatro(subtotal);iva=cuatro(iva);total=cuatro(total); }
        public BigDecimal ticket() { return facturas==0?new BigDecimal("0.0000"):total.divide(BigDecimal.valueOf(facturas),4,RoundingMode.HALF_UP); }
        public String subtotalVisible() { return dinero(subtotal); }
        public String ivaVisible() { return dinero(iva); }
        public String totalVisible() { return dinero(total); }
        public String ticketVisible() { return dinero(ticket()); }
    }
    public record Fila(String id,String nombre,String referencia,long facturas,BigDecimal cantidad,
            BigDecimal subtotal,BigDecimal iva,BigDecimal total) {
        public Fila { subtotal=cuatro(subtotal);iva=cuatro(iva);total=cuatro(total); }
        public String subtotalVisible() { return dinero(subtotal); }
        public String ivaVisible() { return dinero(iva); }
        public String totalVisible() { return dinero(total); }
        public String cantidadVisible() { return cantidad==null?"—":cantidad.setScale(2,RoundingMode.UNNECESSARY).toPlainString(); }
        public static Fila cero(String id) { var cero=new BigDecimal("0.0000");return new Fila(id,id,"",0,null,cero,cero,cero); }
    }
    public record Punto(String etiqueta,BigDecimal total) { }
    public boolean sinVentas() { return resumen.facturas()==0; }
}
