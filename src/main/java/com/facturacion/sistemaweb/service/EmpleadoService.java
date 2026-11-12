package com.facturacion.sistemaweb.service;

import java.util.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.repository.EmpleadoRepository;

@Service
@Profile("sqlserver")
@Transactional(readOnly = true)
public class EmpleadoService {
    private final EmpleadoRepository empleados;
    private final EntityManager entityManager;
    public EmpleadoService(EmpleadoRepository empleados, EntityManager entityManager) {
        this.empleados = empleados;
        this.entityManager = entityManager;
    }
    public List<Empleado> listar(String q) { return empleados.buscar(q == null ? "" : q.strip()); }
    public Empleado buscarPorId(Long id) {
        return empleados.buscarCompleto(id).orElseThrow(() -> new NoSuchElementException("No se encontró el empleado."));
    }
    public List<Persona> personasDisponibles(Long id) { return empleados.personasDisponibles(id); }
    public List<Usuario> usuariosDisponibles(Long id) { return empleados.usuariosDisponibles(id); }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Empleado registrar(EmpleadoForm form) { return guardar(new Empleado(), form); }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Empleado actualizar(Long id, EmpleadoForm form) {
        Empleado empleado = bloquear(id);
        return guardar(empleado, form);
    }

    private Empleado bloquear(Long id) {
        Empleado empleado = entityManager.find(Empleado.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (empleado == null) throw new NoSuchElementException("No se encontró el empleado.");
        return empleado;
    }

    private Empleado guardar(Empleado empleado, EmpleadoForm form) {
        if (form.getIdPersona() == null || form.getIdPersona() <= 0)
            throw new EmpleadoValidacionException("idPersona", "Selecciona una persona válida.");
        if (form.getIdUsuario() == null || form.getIdUsuario() <= 0)
            throw new EmpleadoValidacionException("idUsuario", "Selecciona un usuario válido.");
        // Bloquea las filas compartidas: serializa asociaciones concurrentes aun sin UNIQUE en SQL.
        Persona persona = entityManager.find(Persona.class, form.getIdPersona(), LockModeType.PESSIMISTIC_WRITE);
        if (persona == null) throw new EmpleadoValidacionException("idPersona", "La persona seleccionada no existe.");
        Usuario usuario = entityManager.find(Usuario.class, form.getIdUsuario(), LockModeType.PESSIMISTIC_WRITE);
        if (usuario == null) throw new EmpleadoValidacionException("idUsuario", "El usuario seleccionado no existe.");
        boolean conservaUsuario = empleado.getUsuario() != null
            && Objects.equals(empleado.getUsuario().getIdUsuario(), usuario.getIdUsuario());
        if (!usuario.isActivo() && !conservaUsuario)
            throw new EmpleadoValidacionException("idUsuario", "No puedes asignar un usuario inactivo.");
        if (empleados.personaAsignada(form.getIdPersona(), empleado.getIdEmpleado()))
            throw new EmpleadoValidacionException("idPersona", "La persona ya está asignada a otro empleado.");
        if (empleados.usuarioAsignado(form.getIdUsuario(), empleado.getIdEmpleado()))
            throw new EmpleadoValidacionException("idUsuario", "El usuario ya está asignado a otro empleado.");
        empleado.setPersona(persona);
        empleado.setUsuario(usuario);
        return empleados.saveAndFlush(empleado);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void eliminar(Long id) {
        Empleado empleado = bloquear(id);
        if (empleados.tieneFacturas(id))
            throw new EmpleadoRelacionadoException("No se puede eliminar: el empleado tiene facturas asociadas.");
        try {
            empleados.delete(empleado);
            empleados.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new EmpleadoRelacionadoException("No se puede eliminar: existen registros relacionados con el empleado.");
        }
    }
}
