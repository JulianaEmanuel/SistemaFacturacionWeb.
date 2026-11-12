package com.facturacion.sistemaweb.model;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public class FacturaLineaForm {
    @NotNull(message="Selecciona un producto.") @Positive private Long idProducto;
    @NotNull(message="Ingresa la cantidad.") @DecimalMin(value="0",inclusive=false,message="La cantidad debe ser positiva.")
    @Digits(integer=6,fraction=2,message="La cantidad admite hasta 6 enteros y 2 decimales.") private BigDecimal cantidad;
    public Long getIdProducto() { return idProducto; }
    public void setIdProducto(Long idProducto) { this.idProducto=idProducto; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad=cantidad; }
}
