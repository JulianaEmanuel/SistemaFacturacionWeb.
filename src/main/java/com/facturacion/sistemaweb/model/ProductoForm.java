package com.facturacion.sistemaweb.model;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ProductoForm {

	@NotNull(message = "Selecciona un proveedor.")
	private Long idProveedor;

	@NotBlank(message = "El nombre del producto es obligatorio.")
	@Size(max = 30, message = "El nombre admite hasta 30 caracteres.")
	private String nombreProducto;

	@NotNull(message = "El precio unitario es obligatorio.")
	@DecimalMin(value = "0.0", message = "El precio no puede ser negativo.")
	@Digits(integer = 10, fraction = 4, message = "El precio admite hasta 10 enteros y 4 decimales.")
	private BigDecimal precioUnitario;

	@NotNull(message = "El valor de IVA es obligatorio.")
	@DecimalMin(value = "0.0", message = "El IVA no puede ser negativo.")
	@Digits(integer = 3, fraction = 2, message = "El IVA admite hasta 3 enteros y 2 decimales.")
	private BigDecimal ivaProducto;

	public Long getIdProveedor() {
		return idProveedor;
	}

	public void setIdProveedor(Long idProveedor) {
		this.idProveedor = idProveedor;
	}

	public String getNombreProducto() {
		return nombreProducto;
	}

	public void setNombreProducto(String nombreProducto) {
		this.nombreProducto = nombreProducto;
	}

	public BigDecimal getPrecioUnitario() {
		return precioUnitario;
	}

	public void setPrecioUnitario(BigDecimal precioUnitario) {
		this.precioUnitario = precioUnitario;
	}

	public BigDecimal getIvaProducto() {
		return ivaProducto;
	}

	public void setIvaProducto(BigDecimal ivaProducto) {
		this.ivaProducto = ivaProducto;
	}
}