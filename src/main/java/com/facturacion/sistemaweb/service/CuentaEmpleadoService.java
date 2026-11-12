package com.facturacion.sistemaweb.service;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.facturacion.sistemaweb.model.*;

/**
 * Registra en una sola transacción la persona, su cuenta de acceso y el empleado.
 * Si falla cualquier paso se revierte todo: no quedan personas ni cuentas huérfanas.
 */
@Service @Profile("sqlserver")
@PreAuthorize("@adminAccess.allowed(authentication)")
public class CuentaEmpleadoService {
    private final PersonaService personas;
    private final AdministracionService administracion;
    private final EmpleadoService empleados;
    public CuentaEmpleadoService(PersonaService personas,AdministracionService administracion,EmpleadoService empleados) {
        this.personas=personas;this.administracion=administracion;this.empleados=empleados;
    }
    // SERIALIZABLE conserva la protección contra cédulas duplicadas concurrentes de PersonaService.
    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Empleado registrar(CuentaEmpleadoForm form,String clave,String confirmacion) {
        var usuario=new UsuarioForm();
        usuario.setNombre(form.getNombre());usuario.setIdTipo(form.getIdTipo());usuario.setActivo(true);
        // La cuenta se valida primero (contraseña, nombre repetido) para no insertar la persona en vano.
        Long idUsuario=administracion.registrarCuentaDeEmpleado(usuario,clave,confirmacion,form.isAdministrador());
        Persona persona=personas.registrar(form.persona());
        var empleado=new EmpleadoForm();
        empleado.setIdPersona(persona.getIdPersona());empleado.setIdUsuario(idUsuario);
        return empleados.registrar(empleado);
    }
}
