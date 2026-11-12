package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.repository.EmpleadoRepository;
import com.facturacion.sistemaweb.service.*;

@ExtendWith(MockitoExtension.class)
class EmpleadoServiceTest {
    @Mock EmpleadoRepository repository;
    @Mock EntityManager em;
    EmpleadoService service;
    Persona persona;
    Usuario usuario;
    EmpleadoForm form;
    @BeforeEach void setup() {
        service = new EmpleadoService(repository, em);
        persona = new Persona(); persona.setIdPersona(2L);
        usuario = mock(Usuario.class);
        form = new EmpleadoForm(); form.setIdPersona(2L); form.setIdUsuario(3L);
    }
    void personaExiste() { when(em.find(Persona.class, 2L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(persona); }
    void usuarioExiste(boolean activo) {
        when(em.find(Usuario.class, 3L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(usuario);
        when(usuario.isActivo()).thenReturn(activo);
    }
    @Test void registraSinModificarPersonaNiUsuario() {
        personaExiste(); usuarioExiste(true);
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        Empleado result = service.registrar(form);
        assertSame(persona, result.getPersona()); assertSame(usuario, result.getUsuario());
        verify(em, never()).persist(any()); verify(em, never()).merge(any());
    }
    @Test void rechazaPersonaInexistente() {
        EmpleadoValidacionException ex = assertThrows(EmpleadoValidacionException.class, () -> service.registrar(form));
        assertEquals("idPersona", ex.getCampo()); verify(repository, never()).saveAndFlush(any());
    }
    @Test void rechazaUsuarioInexistente() {
        personaExiste();
        assertEquals("idUsuario", assertThrows(EmpleadoValidacionException.class, () -> service.registrar(form)).getCampo());
        verify(repository, never()).saveAndFlush(any());
    }
    @Test void rechazaUsuarioInactivoNuevo() {
        personaExiste(); usuarioExiste(false);
        assertThrows(EmpleadoValidacionException.class, () -> service.registrar(form));
        verify(repository, never()).saveAndFlush(any());
    }
    @Test void rechazaPersonaDuplicada() {
        personaExiste(); usuarioExiste(true);
        when(repository.personaAsignada(2L, null)).thenReturn(true);
        assertEquals("idPersona", assertThrows(EmpleadoValidacionException.class, () -> service.registrar(form)).getCampo());
        verify(repository, never()).saveAndFlush(any());
    }
    @Test void rechazaUsuarioDuplicado() {
        personaExiste(); usuarioExiste(true);
        when(repository.usuarioAsignado(3L, null)).thenReturn(true);
        assertEquals("idUsuario", assertThrows(EmpleadoValidacionException.class, () -> service.registrar(form)).getCampo());
        verify(repository, never()).saveAndFlush(any());
    }
    Empleado existente() {
        Empleado empleado = mock(Empleado.class);
        when(em.find(Empleado.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(empleado);
        return empleado;
    }
    @Test void editaAmbasAsociacionesExcluyendoEmpleadoActual() {
        Empleado empleado = existente(); when(empleado.getIdEmpleado()).thenReturn(1L);
        personaExiste(); usuarioExiste(true);
        service.actualizar(1L, form);
        verify(repository).personaAsignada(2L, 1L); verify(repository).usuarioAsignado(3L, 1L);
        verify(empleado).setPersona(persona); verify(empleado).setUsuario(usuario);
    }
    @Test void conservaUsuarioActualInactivo() {
        Empleado empleado = existente(); when(empleado.getUsuario()).thenReturn(usuario);
        when(usuario.getIdUsuario()).thenReturn(3L);
        personaExiste(); usuarioExiste(false);
        service.actualizar(1L, form);
        verify(repository).saveAndFlush(empleado);
    }
    @Test void rechazaCambioAUsuarioInactivo() {
        Empleado empleado = existente(); Usuario anterior = mock(Usuario.class);
        when(empleado.getUsuario()).thenReturn(anterior); when(anterior.getIdUsuario()).thenReturn(4L);
        when(usuario.getIdUsuario()).thenReturn(3L);
        personaExiste(); usuarioExiste(false);
        assertThrows(EmpleadoValidacionException.class, () -> service.actualizar(1L, form));
        verify(repository, never()).saveAndFlush(any());
    }
    @Test void rechazaDuplicadosDuranteEdicion() {
        Empleado empleado = existente(); when(empleado.getIdEmpleado()).thenReturn(1L);
        personaExiste(); usuarioExiste(true); when(repository.usuarioAsignado(3L, 1L)).thenReturn(true);
        assertThrows(EmpleadoValidacionException.class, () -> service.actualizar(1L, form));
        verify(empleado, never()).setPersona(any());
    }
    @Test void listaYBuscaConFiltroNormalizado() {
        service.listar(null); service.listar("  Ana  ");
        verify(repository).buscar(""); verify(repository).buscar("Ana");
    }
    @Test void cargaSelectoresYEmpleado() {
        Empleado empleado = new Empleado(); when(repository.buscarCompleto(1L)).thenReturn(Optional.of(empleado));
        assertSame(empleado, service.buscarPorId(1L));
        service.personasDisponibles(1L); service.usuariosDisponibles(1L);
        verify(repository).personasDisponibles(1L); verify(repository).usuariosDisponibles(1L);
    }
    @Test void empleadoInexistente() {
        assertThrows(NoSuchElementException.class, () -> service.buscarPorId(99L));
        assertThrows(NoSuchElementException.class, () -> service.actualizar(99L, form));
        assertThrows(NoSuchElementException.class, () -> service.eliminar(99L));
    }
    @Test void impideEliminarConFacturas() {
        existente(); when(repository.tieneFacturas(1L)).thenReturn(true);
        assertThrows(EmpleadoRelacionadoException.class, () -> service.eliminar(1L));
        verify(repository, never()).delete(any());
    }
    @Test void eliminaSoloEmpleadoSinFacturas() {
        Empleado empleado = existente(); service.eliminar(1L);
        verify(repository).delete(empleado); verify(repository).flush(); verify(em, never()).remove(any());
    }
    @Test void traduceProteccionDeIntegridad() {
        existente(); doThrow(new DataIntegrityViolationException("FK")).when(repository).flush();
        assertThrows(EmpleadoRelacionadoException.class, () -> service.eliminar(1L));
    }
}
