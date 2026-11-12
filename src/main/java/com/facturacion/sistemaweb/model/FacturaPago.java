package com.facturacion.sistemaweb.model;
import java.math.BigDecimal;
import jakarta.persistence.*;
@Entity @Table(name="\"FacturaPago\"",schema="dbo",uniqueConstraints=@UniqueConstraint(columnNames="ID_FACTURA"))
public class FacturaPago {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="ID_FACTURA_PAGO") private Long id;
    @Column(name="ID_FACTURA",nullable=false) private Long idFactura;
    @Column(name="ID_FORMA_PAGO",nullable=false) private Integer idFormaPago;
    @Column(name="NOMBRE_FORMA_PAGO",nullable=false,length=80) private String nombre;
    @Column(name="IMPORTE",nullable=false,precision=18,scale=4) private BigDecimal importe;
    // Migración 005. Nunca se guarda el número completo de la tarjeta, su vencimiento ni el CVV.
    @Column(name="MONTO_RECIBIDO",precision=18,scale=4) private BigDecimal montoRecibido;
    @Column(name="CAMBIO",precision=18,scale=4) private BigDecimal cambio;
    @Column(name="BANCO",length=80) private String banco;
    @Column(name="REFERENCIA",length=30) private String referencia;
    @Column(name="MARCA_TARJETA",length=30) private String marcaTarjeta;
    @Column(name="ULTIMOS_DIGITOS",length=4,columnDefinition="char(4)") private String ultimosDigitos;
    @Column(name="CUOTAS") private Short cuotas;
    protected FacturaPago(){}
    public FacturaPago(Long factura,FormaPago forma,BigDecimal importe){this.idFactura=factura;this.idFormaPago=forma.getId();this.nombre=forma.getNombre();this.importe=importe;}
    public FacturaPago(Long factura,FormaPago forma,BigDecimal importe,com.facturacion.sistemaweb.service.FacturaPagoReglas.Detalle d){
        this(factura,forma,importe);
        montoRecibido=d.montoRecibido();cambio=d.cambio();banco=d.banco();referencia=d.referencia();
        marcaTarjeta=d.marcaTarjeta();ultimosDigitos=d.ultimosDigitos();cuotas=d.cuotas()==null?null:d.cuotas().shortValue();
    }
    public Long getIdFactura(){return idFactura;}public Integer getIdFormaPago(){return idFormaPago;}public String getNombre(){return nombre;}public BigDecimal getImporte(){return importe;}
    public BigDecimal getMontoRecibido(){return montoRecibido;}public BigDecimal getCambio(){return cambio;}public String getBanco(){return banco;}
    public String getReferencia(){return referencia;}public String getMarcaTarjeta(){return marcaTarjeta;}public String getUltimosDigitos(){return ultimosDigitos;}
    public Integer getCuotas(){return cuotas==null?null:cuotas.intValue();}
}
