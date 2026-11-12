package com.facturacion.sistemaweb.model;

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
@Table(name = "Proveedor", schema = "dbo")
public class Proveedor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ID_PROVEEDOR", nullable = false)
	private Long idProveedor;

	@Column(name = "ID_PERSONA_PROVEEDOR", nullable = false, insertable = false, updatable = false)
	private Long idPersonaProveedor;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ID_PERSONA_PROVEEDOR", nullable = false)
	private Persona persona;

	@Column(name = "EMAIL", length = 100, columnDefinition = "nchar(100)")
	private String email;

	public Proveedor() {
	}

	public Long getIdProveedor() {
		return idProveedor;
	}

	public Long getIdPersonaProveedor() {
		return idPersonaProveedor;
	}

	public Persona getPersona() {
		return persona;
	}

	public void setPersona(Persona persona) {
		this.persona = persona;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}
}