package com.facturacion.sistemaweb.model;

import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public class FacturaForm {
    @NotNull(message="Selecciona una forma de pago.") @Positive private Integer idFormaPago;
    public Integer getIdFormaPago(){return idFormaPago;}
    public void setIdFormaPago(Integer v){idFormaPago=v;}
    @NotNull(message="Selecciona un cliente.") @Positive private Long idCliente;
    @NotBlank(message="El formulario ha caducado. Abre un nuevo formulario.") @Size(max=36) private String token;
    @NotEmpty(message="Agrega al menos un producto.") @Size(max=200,message="La factura admite hasta 200 líneas.")
    @Valid private List<FacturaLineaForm> lineas=new ArrayList<>();
    public Long getIdCliente() { return idCliente; }
    public void setIdCliente(Long idCliente) { this.idCliente=idCliente; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token=token; }
    public List<FacturaLineaForm> getLineas() { return lineas; }
    public void setLineas(List<FacturaLineaForm> lineas) { this.lineas=lineas; }

    // Detalle del pago: el servidor valida según la forma de pago (FacturaPagoReglas).
    // Nunca se reciben el número completo de la tarjeta, su vencimiento ni el CVV.
    private java.math.BigDecimal montoRecibido;
    @Size(max=80,message="El nombre del banco admite hasta 80 caracteres.") private String banco;
    @Size(max=30,message="La referencia admite hasta 30 caracteres.") private String referencia;
    @Size(max=30) private String marcaTarjeta;
    @Pattern(regexp="[0-9]{0,4}",message="Ingresa solo los últimos 4 dígitos de la tarjeta.") private String ultimosDigitos;
    @Min(value=1,message="Las cuotas deben estar entre 1 y 48.") @Max(value=48,message="Las cuotas deben estar entre 1 y 48.") private Integer cuotas;
    public java.math.BigDecimal getMontoRecibido(){return montoRecibido;}
    public void setMontoRecibido(java.math.BigDecimal v){montoRecibido=v;}
    public String getBanco(){return banco;}
    public void setBanco(String v){banco=v;}
    public String getReferencia(){return referencia;}
    public void setReferencia(String v){referencia=v;}
    public String getMarcaTarjeta(){return marcaTarjeta;}
    public void setMarcaTarjeta(String v){marcaTarjeta=v;}
    public String getUltimosDigitos(){return ultimosDigitos;}
    public void setUltimosDigitos(String v){ultimosDigitos=v==null?null:v.strip();}
    public Integer getCuotas(){return cuotas;}
    public void setCuotas(Integer v){cuotas=v;}
}
