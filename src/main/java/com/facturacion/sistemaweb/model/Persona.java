package com.facturacion.sistemaweb.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Persona", schema = "dbo")
public class Persona {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ID_PERSONA", nullable = false)
	private Long idPersona;

	@Column(name = "CEDULA_PERSONA", nullable = false, length = 13, columnDefinition = "char(13)")
	private String cedulaPersona;

	@Column(name = "NOMBRES_PERSONA", nullable = false, length = 100, columnDefinition = "char(100)")
	private String nombresPersona;

	@Column(name = "APELLIDOS_PERSONA", nullable = false, length = 100, columnDefinition = "char(100)")
	private String apellidosPersona;

	@Column(name = "FECHA_NACIMIENTO_PERSONA")
	private LocalDateTime fechaNacimientoPersona;

    // Persistidos por PersonaContactosRepository para conservar lectura del esquema anterior.
    @jakarta.persistence.Transient private String direccionPersona;
    @jakarta.persistence.Transient private String telefonoPersona;
    @jakarta.persistence.Transient private String correoPersona;
    public String getDireccionPersona(){return direccionPersona;}
    public void setDireccionPersona(String v){direccionPersona=v;}
    public String getTelefonoPersona(){return telefonoPersona;}
    public void setTelefonoPersona(String v){telefonoPersona=v;}
    public String getCorreoPersona(){return correoPersona;}
    public void setCorreoPersona(String v){correoPersona=v;}
	public Persona() {
	}

	public Long getIdPersona() {
		return idPersona;
	}

	public void setIdPersona(Long idPersona) {
		this.idPersona = idPersona;
	}

	public String getCedulaPersona() {
		return cedulaPersona;
	}

	public void setCedulaPersona(String cedulaPersona) {
		this.cedulaPersona = cedulaPersona;
	}

	public String getNombresPersona() {
		return nombresPersona;
	}

	public void setNombresPersona(String nombresPersona) {
		this.nombresPersona = nombresPersona;
	}

	public String getApellidosPersona() {
		return apellidosPersona;
	}

	public void setApellidosPersona(String apellidosPersona) {
		this.apellidosPersona = apellidosPersona;
	}

	public LocalDateTime getFechaNacimientoPersona() {
		return fechaNacimientoPersona;
	}

	public void setFechaNacimientoPersona(LocalDateTime fechaNacimientoPersona) {
		this.fechaNacimientoPersona = fechaNacimientoPersona;
	}
}