package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.repository.*;
import com.facturacion.sistemaweb.security.*;
import com.facturacion.sistemaweb.service.*;

@ExtendWith(MockitoExtension.class)
class AdministracionServiceTest {
    @Mock AdministracionRepository usuarios;
    @Mock TipoUsuarioRepository tipos;
    @Mock AdminAccess access;
    @Mock ClaveSegura claves;
    AdministracionService service;
    static final String CLAVE="solo-pruebas-no-cuenta-real";
    @BeforeEach void setup() {
        service=new AdministracionService(usuarios,tipos,access,claves,Validation.buildDefaultValidatorFactory().getValidator());
    }
    UsuarioForm form() { var f=new UsuarioForm();f.setNombre("operador");f.setIdTipo(2);return f; }
    void actual(boolean activo) { when(usuarios.buscarId(7L)).thenReturn(Optional.of(new UsuarioResumen(7L,3,"admin","ADMINISTRADOR",activo))); }
    void tipoExiste() { when(tipos.findById(2)).thenReturn(Optional.of(new TipoUsuario())); }
    @Test void registraConBytesYBloqueoAntesDeValidar() {
        tipoExiste();when(claves.codificar(CLAVE)).thenReturn(new byte[]{1,2});
        service.registrar(form(),CLAVE,CLAVE);
        var orden=inOrder(usuarios,access);orden.verify(usuarios).bloquear();orden.verify(access).exigir();
        verify(usuarios).insertar(eq("operador"),eq(2),eq(true),any(byte[].class));
    }
    @Test void evitaDuplicadoSegunConsultaSql() {
        tipoExiste();when(usuarios.nombreDuplicado("operador",null)).thenReturn(true);
        assertThrows(AdministracionException.class,()->service.registrar(form(),CLAVE,CLAVE));verify(usuarios,never()).insertar(any(),any(),anyBoolean(),any());
    }
    @Test void validaTipoExistente() {
        assertThrows(AdministracionException.class,()->service.registrar(form(),CLAVE,CLAVE));
    }
    @Test void validaCamposEnServicio() {
        var f=form();f.setNombre(" ");assertThrows(AdministracionException.class,()->service.registrar(f,CLAVE,CLAVE));
        f.setNombre("x".repeat(101));assertThrows(AdministracionException.class,()->service.editar(7L,f));
    }
    @Test void edicionNoEscribeClaveNiEstado() {
        actual(false);tipoExiste();service.editar(7L,form());
        verify(usuarios).editar(7L,"operador",2);verify(usuarios,never()).clave(any(),any());verify(usuarios,never()).estado(any(),anyBoolean());
    }
    @Test void impideQuitarUltimoAdministrador() {
        actual(true);tipoExiste();when(access.reservado()).thenReturn("ADMINISTRADOR");
        when(usuarios.tipoReservado(3,"ADMINISTRADOR")).thenReturn(true);
        when(usuarios.administradoresActivos("ADMINISTRADOR")).thenReturn(1L);
        assertThrows(AdministracionException.class,()->service.editar(7L,form()));verify(usuarios,never()).editar(any(),any(),any());
    }
    @Test void impideDesactivarUltimoAdministrador() {
        actual(true);when(access.reservado()).thenReturn("ADMINISTRADOR");when(usuarios.tipoReservado(3,"ADMINISTRADOR")).thenReturn(true);
        when(usuarios.administradoresActivos("ADMINISTRADOR")).thenReturn(1L);
        assertThrows(AdministracionException.class,()->service.estado(7L,false));verify(usuarios,never()).estado(any(),anyBoolean());
    }
    @Test void permiteDesactivarSiQuedaOtroAdministrador() {
        actual(true);when(access.reservado()).thenReturn("ADMINISTRADOR");when(usuarios.tipoReservado(3,"ADMINISTRADOR")).thenReturn(true);
        when(usuarios.administradoresActivos("ADMINISTRADOR")).thenReturn(2L);
        service.estado(7L,false);verify(usuarios).estado(7L,false);
    }
    @Test void activaCuentaSinCambiarAsociaciones() { actual(false);service.estado(7L,true);verify(usuarios).estado(7L,true); }
    @Test void cambiarClaveEsOperacionSeparada() {
        actual(false);when(claves.codificar(CLAVE)).thenReturn(new byte[]{2,3});service.cambiarClave(7L,CLAVE,CLAVE);
        verify(usuarios).clave(eq(7L),any());verify(usuarios,never()).editar(any(),any(),any());
    }
    TipoUsuarioForm tipoForm() { var f=new TipoUsuarioForm();f.setNombre("OPERADOR");f.setDescripcion("Operación diaria");return f; }
    @Test void registraTipo() { service.guardarTipo(null,tipoForm());verify(tipos).saveAndFlush(any()); }
    @Test void rechazaTipoDuplicado() {
        when(tipos.duplicados("OPERADOR",null)).thenReturn(1L);assertThrows(AdministracionException.class,()->service.guardarTipo(null,tipoForm()));
    }
    @Test void editaTipoNormal() {
        when(tipos.findById(2)).thenReturn(Optional.of(new TipoUsuario()));service.guardarTipo(2,tipoForm());verify(tipos).saveAndFlush(any());
    }
    @Test void noRenombraReservado() {
        var tipo=new TipoUsuario();tipo.setNombre("ADMINISTRADOR");when(tipos.findById(3)).thenReturn(Optional.of(tipo));
        when(access.reservado()).thenReturn("ADMINISTRADOR");when(usuarios.tipoReservado(3,"ADMINISTRADOR")).thenReturn(true);
        assertThrows(AdministracionException.class,()->service.guardarTipo(3,tipoForm()));
    }
    @Test void noEliminaTipoEnUso() {
        when(tipos.findById(2)).thenReturn(Optional.of(new TipoUsuario()));when(usuarios.tipoEnUso(2)).thenReturn(true);
        assertThrows(AdministracionException.class,()->service.eliminarTipo(2));verify(tipos,never()).deleteById(any());
    }
    @Test void noEliminaReservado() {
        when(tipos.findById(3)).thenReturn(Optional.of(new TipoUsuario()));when(access.reservado()).thenReturn("ADMINISTRADOR");when(usuarios.tipoReservado(3,"ADMINISTRADOR")).thenReturn(true);
        assertThrows(AdministracionException.class,()->service.eliminarTipo(3));
    }
    @Test void eliminaTipoLibre() { when(tipos.findById(2)).thenReturn(Optional.of(new TipoUsuario()));service.eliminarTipo(2);verify(tipos).deleteById(2); }
    @Test void rechazoSiSePierdenPermisosDuranteEspera() {
        doThrow(new org.springframework.security.access.AccessDeniedException("Sin permiso")).when(access).exigir();
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.estado(7L,false));verify(usuarios,never()).buscarId(any());
    }
    @Test void cuentaOperativaSeCreaActivaYDevuelveId() {
        tipoExiste();when(access.reservado()).thenReturn("ADMINISTRADOR");when(claves.codificar(CLAVE)).thenReturn(new byte[]{1,2});
        when(usuarios.insertarDevolviendoId(eq("operador"),eq(2),eq(true),any(byte[].class))).thenReturn(15L);
        var f=form();f.setActivo(false);
        assertEquals(15L,service.registrarCuentaOperativa(f,CLAVE,CLAVE));
        var orden=inOrder(usuarios,access);orden.verify(usuarios).bloquear();orden.verify(access).exigir();
    }
    @Test void cuentaOperativaNoAceptaTipoAdministrativo() {
        tipoExiste();when(access.reservado()).thenReturn("ADMINISTRADOR");when(usuarios.tipoReservado(2,"ADMINISTRADOR")).thenReturn(true);
        assertThrows(AdministracionException.class,()->service.registrarCuentaOperativa(form(),CLAVE,CLAVE));
        verify(usuarios,never()).insertarDevolviendoId(any(),any(),anyBoolean(),any());
    }
    @Test void cuentaOperativaValidaClaveYDuplicado() {
        tipoExiste();doThrow(new AdministracionException("corta")).when(claves).validar("corta","corta");
        assertThrows(AdministracionException.class,()->service.registrarCuentaOperativa(form(),"corta","corta"));
        when(usuarios.nombreDuplicado("operador",null)).thenReturn(true);when(access.reservado()).thenReturn("ADMINISTRADOR");
        assertThrows(AdministracionException.class,()->service.registrarCuentaOperativa(form(),CLAVE,CLAVE));
        verify(usuarios,never()).insertarDevolviendoId(any(),any(),anyBoolean(),any());
    }
    @Test void sinTipoSeCreaEmpleadoLaPrimeraVez() {
        var creado=mock(TipoUsuario.class);when(creado.getId()).thenReturn(5);
        when(tipos.buscar("EMPLEADO")).thenReturn(List.of());when(tipos.saveAndFlush(any(TipoUsuario.class))).thenReturn(creado);
        when(tipos.findById(5)).thenReturn(Optional.of(creado));when(access.reservado()).thenReturn("ADMINISTRADOR");
        when(claves.codificar(CLAVE)).thenReturn(new byte[]{1});
        when(usuarios.insertarDevolviendoId(eq("operador"),eq(5),eq(true),any(byte[].class))).thenReturn(20L);
        var f=form();f.setIdTipo(null);
        assertEquals(20L,service.registrarCuentaOperativa(f,CLAVE,CLAVE));
        verify(tipos).saveAndFlush(argThat(t -> "EMPLEADO".equals(t.getNombre()) && !t.getDescripcion().isBlank()));
        var orden=inOrder(usuarios,tipos);orden.verify(usuarios).bloquear();orden.verify(tipos).saveAndFlush(any());
    }
    @Test void sinTipoReutilizaEmpleadoExistente() {
        var existente=mock(TipoUsuario.class);when(existente.getId()).thenReturn(4);when(existente.getNombre()).thenReturn("EMPLEADO      ");
        when(tipos.buscar("EMPLEADO")).thenReturn(List.of(existente));when(tipos.findById(4)).thenReturn(Optional.of(existente));
        when(access.reservado()).thenReturn("ADMINISTRADOR");when(claves.codificar(CLAVE)).thenReturn(new byte[]{1});
        when(usuarios.insertarDevolviendoId(eq("operador"),eq(4),eq(true),any(byte[].class))).thenReturn(21L);
        var f=form();f.setIdTipo(null);
        assertEquals(21L,service.registrarCuentaOperativa(f,CLAVE,CLAVE));
        verify(tipos,never()).saveAndFlush(any());
    }
    @Test void rolAdministradorUsaElTipoReservadoAunqueSeEnvieOtro() {
        var admin=mock(TipoUsuario.class);when(admin.getId()).thenReturn(1);when(admin.getNombre()).thenReturn("ADMINISTRADOR   ");
        when(access.reservado()).thenReturn("ADMINISTRADOR");when(tipos.buscar("ADMINISTRADOR")).thenReturn(List.of(admin));
        when(tipos.findById(1)).thenReturn(Optional.of(admin));when(claves.codificar(CLAVE)).thenReturn(new byte[]{1});
        when(usuarios.insertarDevolviendoId(eq("operador"),eq(1),eq(true),any(byte[].class))).thenReturn(30L);
        var f=form();f.setIdTipo(2);
        assertEquals(30L,service.registrarCuentaDeEmpleado(f,CLAVE,CLAVE,true));
        verify(tipos,never()).saveAndFlush(any());
    }
    @Test void rolEmpleadoNoPuedeElegirElTipoAdministrativo() {
        tipoExiste();when(access.reservado()).thenReturn("ADMINISTRADOR");when(usuarios.tipoReservado(2,"ADMINISTRADOR")).thenReturn(true);
        var ex=assertThrows(AdministracionException.class,()->service.registrarCuentaDeEmpleado(form(),CLAVE,CLAVE,false));
        assertTrue(ex.getMessage().contains("rol"));
        verify(usuarios,never()).insertarDevolviendoId(any(),any(),anyBoolean(),any());
    }
    @Test void tiposOperativosExcluyenAlAdministrador() {
        var admin=mock(TipoUsuario.class);when(admin.getId()).thenReturn(1);
        var empleado=mock(TipoUsuario.class);when(empleado.getId()).thenReturn(2);
        when(tipos.buscar("")).thenReturn(List.of(admin,empleado));when(access.reservado()).thenReturn("ADMINISTRADOR");
        when(usuarios.tipoReservado(1,"ADMINISTRADOR")).thenReturn(true);
        assertEquals(List.of(empleado),service.tiposOperativos());
    }
}
