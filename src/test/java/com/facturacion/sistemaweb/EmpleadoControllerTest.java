package com.facturacion.sistemaweb;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.facturacion.sistemaweb.controller.EmpleadoController;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.service.*;

@WebMvcTest(EmpleadoController.class)
@ActiveProfiles("sqlserver")
@org.springframework.security.test.context.support.WithMockUser
@org.springframework.context.annotation.Import({com.facturacion.sistemaweb.security.SecurityConfig.class,CapturaVistasConfig.class})
class EmpleadoControllerTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.springframework.security.core.userdetails.UserDetailsService autenticacionSimulada;
    // Estas pruebas cubren las acciones del administrador; la matriz de permisos se prueba en PermisosModulosSecurityTest.
    @org.springframework.test.context.bean.override.mockito.MockitoBean(name="adminAccess") com.facturacion.sistemaweb.security.AdminAccess adminAccess;
    @org.junit.jupiter.api.BeforeEach void comoAdministrador() { org.mockito.Mockito.when(adminAccess.allowed(org.mockito.ArgumentMatchers.any())).thenReturn(true); }
    @Autowired MockMvc mvc;
    @MockitoBean EmpleadoService service;
    Persona persona() {
        Persona p = new Persona(); p.setIdPersona(2L); p.setCedulaPersona("0912345678   ");
        p.setNombresPersona("Ana   "); p.setApellidosPersona("Paz   "); return p;
    }
    Usuario usuario(boolean activo) {
        Usuario u = mock(Usuario.class); when(u.getIdUsuario()).thenReturn(3L);
        when(u.getNombreUsuario()).thenReturn("ana.usuario   "); when(u.isActivo()).thenReturn(activo); return u;
    }
    Empleado empleado() {
        Empleado e = mock(Empleado.class); when(e.getIdEmpleado()).thenReturn(1L);
        when(e.getPersona()).thenReturn(persona()); doReturn(usuario(true)).when(e).getUsuario(); return e;
    }
    void selectores(Long id) {
        when(service.personasDisponibles(id)).thenReturn(List.of(persona()));
        doReturn(List.of(usuario(true))).when(service).usuariosDisponibles(id);
    }
    @Test void listaBuscaYRecortaSoloPresentacion() throws Exception {
        doReturn(List.of(empleado())).when(service).listar("Ana");
        mvc.perform(get("/modulos/empleados").param("q", "Ana"))
            .andExpect(status().isOk()).andExpect(view().name("empleados/lista"))
            .andExpect(content().string(containsString(">Ana</td>")))
            .andExpect(content().string(containsString(">ana.usuario</td>")))
            .andExpect(content().string(containsString("Limpiar")))
            .andExpect(content().string(containsString("return confirm(")))
            .andExpect(content().string(not(containsString("CONTRASENIA"))));
        verify(service).listar("Ana");
    }
    @Test void formularioNuevoMuestraSelectoresSinContrasenia() throws Exception {
        selectores(null);
        mvc.perform(get("/modulos/empleados/nuevo")).andExpect(status().isOk())
            .andExpect(content().string(containsString("0912345678 — Ana Paz")))
            .andExpect(content().string(not(containsString("type=\"password\""))));
    }
    @Test void formularioVacioDeshabilitaRegistro() throws Exception {
        mvc.perform(get("/modulos/empleados/nuevo")).andExpect(status().isOk())
            .andExpect(content().string(containsString("No hay personas disponibles")))
            .andExpect(content().string(containsString("disabled")));
    }
    @Test void validaCamposAntesDelServicio() throws Exception {
        selectores(null);
        mvc.perform(post("/modulos/empleados").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).param("idPersona", "-1"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("empleadoForm", "idPersona", "idUsuario"));
        verify(service, never()).registrar(any());
    }
    @Test void registraConMockSinEscrituraReal() throws Exception {
        mvc.perform(post("/modulos/empleados").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).param("idPersona", "2").param("idUsuario", "3"))
            .andExpect(redirectedUrl("/modulos/empleados"))
            .andExpect(flash().attribute("successMessage", "Empleado registrado correctamente."));
        verify(service).registrar(argThat(f -> f.getIdPersona() == 2 && f.getIdUsuario() == 3));
    }
    @Test void muestraErrorDeAsociacion() throws Exception {
        selectores(null);
        when(service.registrar(any())).thenThrow(new EmpleadoValidacionException("idUsuario", "Usuario no disponible."));
        mvc.perform(post("/modulos/empleados").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).param("idPersona", "2").param("idUsuario", "3"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("empleadoForm", "idUsuario"))
            .andExpect(content().string(containsString("Usuario no disponible.")));
    }
    @Test void edicionMuestraActualInactivo() throws Exception {
        Empleado e = empleado(); doReturn(usuario(false)).when(e).getUsuario();
        when(service.buscarPorId(1L)).thenReturn(e); selectores(1L);
        doReturn(List.of(e.getUsuario())).when(service).usuariosDisponibles(1L);
        mvc.perform(get("/modulos/empleados/1/editar")).andExpect(status().isOk())
            .andExpect(content().string(containsString("inactivo, actual")))
            .andExpect(content().string(containsString("selected=\"selected\"")));
    }
    @Test void guardaEdicionConMock() throws Exception {
        doReturn(empleado()).when(service).buscarPorId(1L);
        mvc.perform(post("/modulos/empleados/1/editar").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).param("idPersona", "2").param("idUsuario", "3"))
            .andExpect(redirectedUrl("/modulos/empleados"));
        verify(service).actualizar(eq(1L), any());
    }
    @Test void edicionInvalidaConservaFormulario() throws Exception {
        doReturn(empleado()).when(service).buscarPorId(1L); selectores(1L);
        mvc.perform(post("/modulos/empleados/1/editar").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).param("idPersona", "2"))
            .andExpect(status().isOk()).andExpect(view().name("empleados/formulario"));
        verify(service, never()).actualizar(any(), any());
    }
    @Test void eliminaConMock() throws Exception {
        mvc.perform(post("/modulos/empleados/1/eliminar").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andExpect(redirectedUrl("/modulos/empleados"))
            .andExpect(flash().attribute("successMessage", "Empleado eliminado correctamente."));
        verify(service).eliminar(1L);
    }
    @Test void comunicaProteccionPorFacturas() throws Exception {
        doThrow(new EmpleadoRelacionadoException("Tiene facturas asociadas.")).when(service).eliminar(1L);
        mvc.perform(post("/modulos/empleados/1/eliminar").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andExpect(redirectedUrl("/modulos/empleados"))
            .andExpect(flash().attribute("errorMessage", "Tiene facturas asociadas."));
    }
    @Test void inexistenteDevuelve404() throws Exception {
        when(service.buscarPorId(99L)).thenThrow(new NoSuchElementException());
        doThrow(new NoSuchElementException()).when(service).eliminar(99L);
        mvc.perform(get("/modulos/empleados/99/editar")).andExpect(status().isNotFound());
        mvc.perform(post("/modulos/empleados/99/eliminar").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isNotFound());
    }
}
