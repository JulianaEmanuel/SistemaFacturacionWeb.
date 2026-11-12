package com.facturacion.sistemaweb.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "Producto", schema = "dbo")
public class Producto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ID_PRODUCTO", nullable = false)
	private Long idProducto;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ID_PROVEEDOR", nullable = false)
	private Proveedor proveedor;

	@Column(name = "NOMBRE_PRODUCTO", nullable = false, length = 30, columnDefinition = "char(30)")
	private String nombreProducto;

	@Column(name = "PRECIO_UNITARIO", nullable = false, precision = 14, scale = 4)
	private BigDecimal precioUnitario;

	@Column(name = "IVA_PRODUCTO", nullable = false, precision = 5, scale = 2)
	private BigDecimal ivaProducto;

	public Producto() {
	}

	public Long getIdProducto() {
		return idProducto;
	}

	public void setIdProducto(Long idProducto) {
		this.idProducto = idProducto;
	}

	public Proveedor getProveedor() {
		return proveedor;
	}

	public void setProveedor(Proveedor proveedor) {
		this.proveedor = proveedor;
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