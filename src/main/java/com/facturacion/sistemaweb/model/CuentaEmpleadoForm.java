package com.facturacion.sistemaweb.model;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.*;

/**
 * Alta completa en un paso: datos personales + cuenta de acceso no administrativa.
 * La contraseña no forma parte del formulario enlazado (viaja como parámetro y nunca vuelve al HTML).
 */
public class CuentaEmpleadoForm {
    @NotBlank(message = "La cédula es obligatoria.")
    @Pattern(regexp = "[0-9]{10}", message = "La cédula debe contener exactamente 10 dígitos numéricos.")
    private String cedulaPersona;
    @NotBlank(message = "Los nombres son obligatorios.") @Size(max = 100, message = "Los nombres admiten hasta 100 caracteres.")
    private String nombresPersona;
    @NotBlank(message = "Los apellidos son obligatorios.") @Size(max = 100, message = "Los apellidos admiten hasta 100 caracteres.")
    private String apellidosPersona;
    @NotNull(message = "La fecha de nacimiento es obligatoria.")
    @Past(message = "La fecha de nacimiento debe ser anterior a hoy.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaNacimientoPersona;
    @NotBlank(message = "La dirección es obligatoria.") @Size(max = 250, message = "La dirección admite hasta 250 caracteres.")
    private String direccionPersona;
    @Size(max = 25, message = "El teléfono admite hasta 25 caracteres.")
    @Pattern(regexp = "[0-9+() .-]*", message = "Ingresa un teléfono válido.")
    private String telefonoPersona;
    @NotBlank(message = "El correo es obligatorio.") @Email(message = "Ingresa un correo electrónico válido.")
    @Size(max = 254, message = "El correo admite hasta 254 caracteres.")
    private String correoPersona;
    @NotBlank(message = "El nombre de usuario es obligatorio.") @Size(max = 100, message = "El nombre de usuario admite hasta 100 caracteres.")
    private String nombre;
    /** Opcional: sin valor se usa el tipo EMPLEADO (se crea si no existe). */
    @Positive(message = "Selecciona un tipo de usuario válido.")
    private Integer idTipo;
    /** true: cuenta administradora que también factura. false (por defecto): empleado sin administración. */
    private boolean administrador;
    public boolean isAdministrador() { return administrador; }
    public void setAdministrador(boolean v) { administrador = v; }

    /** Datos personales en el formato que usa PersonaService. */
    public PersonaForm persona() {
        var p = new PersonaForm();
        p.setCedulaPersona(cedulaPersona); p.setNombresPersona(nombresPersona); p.setApellidosPersona(apellidosPersona);
        p.setFechaNacimientoPersona(fechaNacimientoPersona); p.setDireccionPersona(direccionPersona);
        p.setTelefonoPersona(telefonoPersona == null || telefonoPersona.isBlank() ? null : telefonoPersona);
        p.setCorreoPersona(correoPersona);
        return p;
    }

    private static String limpio(String v) { return v == null ? null : v.strip(); }
    public String getCedulaPersona() { return cedulaPersona; }
    public void setCedulaPersona(String v) { cedulaPersona = limpio(v); }
    public String getNombresPersona() { return nombresPersona; }
    public void setNombresPersona(String v) { nombresPersona = limpio(v); }
    public String getApellidosPersona() { return apellidosPersona; }
    public void setApellidosPersona(String v) { apellidosPersona = limpio(v); }
    public LocalDate getFechaNacimientoPersona() { return fechaNacimientoPersona; }
    public void setFechaNacimientoPersona(LocalDate v) { fechaNacimientoPersona = v; }
    public String getDireccionPersona() { return direccionPersona; }
    public void setDireccionPersona(String v) { direccionPersona = limpio(v); }
    public String getTelefonoPersona() { return telefonoPersona; }
    public void setTelefonoPersona(String v) { telefonoPersona = limpio(v); }
    public String getCorreoPersona() { return correoPersona; }
    public void setCorreoPersona(String v) { correoPersona = limpio(v); }
    public String getNombre() { return nombre; }
    public void setNombre(String v) { nombre = limpio(v); }
    public Integer getIdTipo() { return idTipo; }
    public void setIdTipo(Integer v) { idTipo = v; }
}
