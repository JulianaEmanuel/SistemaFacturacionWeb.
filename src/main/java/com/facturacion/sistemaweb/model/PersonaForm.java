package com.facturacion.sistemaweb.model;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PersonaForm {

	@NotBlank(message = "La cédula es obligatoria.")
	@Pattern(regexp = "[0-9]{10}", message = "La cédula debe contener exactamente 10 dígitos numéricos.")
	private String cedulaPersona;

	@NotBlank(message = "Los nombres son obligatorios.")
	@Size(max = 100, message = "Los nombres admiten hasta 100 caracteres.")
	private String nombresPersona;

	@NotBlank(message = "Los apellidos son obligatorios.")
	@Size(max = 100, message = "Los apellidos admiten hasta 100 caracteres.")
	private String apellidosPersona;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate fechaNacimientoPersona;

    public interface Nuevo extends jakarta.validation.groups.Default { }
    @NotBlank(groups=Nuevo.class,message="La dirección es obligatoria para clientes nuevos.")
    @Size(max=250,message="La dirección admite hasta 250 caracteres.") private String direccionPersona;
    @Size(max=25,message="El teléfono admite hasta 25 caracteres.")
    @Pattern(regexp="[0-9+() .-]*",message="Ingresa un teléfono válido.") private String telefonoPersona;
    @NotBlank(groups=Nuevo.class,message="El correo es obligatorio para clientes nuevos.")
    @jakarta.validation.constraints.Email(message="Ingresa un correo electrónico válido.")
    @Size(max=254,message="El correo admite hasta 254 caracteres.") private String correoPersona;
    public String getDireccionPersona(){return direccionPersona;}
    public void setDireccionPersona(String v){direccionPersona=v==null?null:v.strip();}
    public String getTelefonoPersona(){return telefonoPersona;}
    public void setTelefonoPersona(String v){telefonoPersona=v==null?null:v.strip();}
    public String getCorreoPersona(){return correoPersona;}
    public void setCorreoPersona(String v){correoPersona=v==null?null:v.strip();}
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

	public LocalDate getFechaNacimientoPersona() {
		return fechaNacimientoPersona;
	}

	public void setFechaNacimientoPersona(LocalDate fechaNacimientoPersona) {
		this.fechaNacimientoPersona = fechaNacimientoPersona;
	}
}