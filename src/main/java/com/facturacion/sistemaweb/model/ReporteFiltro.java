package com.facturacion.sistemaweb.model;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Solo filtros: no recibe roles, importes ni alcance de autorización. */
public class ReporteFiltro {
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate desde;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate hasta;
    private Long cliente,producto,empleado;
    private String agrupacion="dia",orden="cantidad",q="";
    private Integer limite=10;
    public LocalDate getDesde() { return desde; }
    public void setDesde(LocalDate desde) { this.desde=desde; }
    public LocalDate getHasta() { return hasta; }
    public void setHasta(LocalDate hasta) { this.hasta=hasta; }
    public Long getCliente() { return cliente; }
    public void setCliente(Long cliente) { this.cliente=cliente; }
    public Long getProducto() { return producto; }
    public void setProducto(Long producto) { this.producto=producto; }
    public Long getEmpleado() { return empleado; }
    public void setEmpleado(Long empleado) { this.empleado=empleado; }
    public String getAgrupacion() { return agrupacion; }
    public void setAgrupacion(String agrupacion) { this.agrupacion=agrupacion; }
    public String getOrden() { return orden; }
    public void setOrden(String orden) { this.orden=orden; }
    public String getQ() { return q; }
    public void setQ(String q) { this.q=q; }
    public Integer getLimite() { return limite; }
    public void setLimite(Integer limite) { this.limite=limite; }
    public record Validado(LocalDate desde,LocalDate hasta,Long cliente,Long producto,Long empleado,
        String agrupacion,String orden,String q,int limite) {
        public java.time.LocalDateTime inicio() { return desde.atStartOfDay(); }
        public java.time.LocalDateTime finExclusivo() { return hasta.plusDays(1).atStartOfDay(); }
        public String descripcion() {
            return "Cliente: "+(cliente==null?"todos":cliente)+" · Producto: "+(producto==null?"todos":producto)
                +" · Empleado: "+(empleado==null?"todos":empleado)+" · Búsqueda: "+(q.isBlank()?"sin filtro":q)
                +" · Agrupación: "+agrupacion+" · Ranking: "+limite+" por "+orden;
        }
    }
}
