package com.facturacion.sistemaweb;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.facturacion.sistemaweb.controller.*;
import com.facturacion.sistemaweb.repository.ProveedorRepository;
import com.facturacion.sistemaweb.security.AdminAccess;
import com.facturacion.sistemaweb.security.SecurityConfig;
import com.facturacion.sistemaweb.service.*;

@WebMvcTest({ClienteController.class, ProductoController.class, ProveedorController.class, EmpleadoController.class, CuentaEmpleadoController.class})
@Import({SecurityConfig.class, CapturaVistasConfig.class})
@ActiveProfiles("sqlserver")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class PermisosModulosSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean PersonaService personas;
    @MockitoBean ProductoService productos;
    @MockitoBean ProveedorService proveedores;
    @MockitoBean EmpleadoService empleados;
    @MockitoBean CuentaEmpleadoService cuentas;
    @MockitoBean AdministracionService administracion;
    @MockitoBean ProveedorRepository proveedorRepository;
    @MockitoBean(name="adminAccess") AdminAccess adminAccess;
    @MockitoBean UserDetailsService users;

    @BeforeEach
    void preparar() {
        when(adminAccess.allowed(any())).thenAnswer(invocation -> {
            Authentication authentication = invocation.getArgument(0);
            return authentication != null && authentication.isAuthenticated()
                && authentication.getName().equals("admin");
        });
        when(personas.listar(any())).thenReturn(List.of());
        when(personas.contactosDisponibles()).thenReturn(true);
        when(productos.listar(any())).thenReturn(List.of());
        when(proveedorRepository.count()).thenReturn(0L);
        when(proveedorRepository.listarParaSelector()).thenReturn(List.of());
        when(proveedores.listar(any())).thenReturn(List.of());
        when(empleados.listar(any())).thenReturn(List.of());
    }

    @Test
    void usuarioOperativoConsultaClientesYProductosYRegistraClientes() throws Exception {
        mvc.perform(get("/modulos/clientes").with(operativo()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Registrar cliente")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Editar"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Eliminar"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Proveedores"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Empleados"))));

        mvc.perform(get("/modulos/clientes/nuevo").with(operativo()))
            .andExpect(status().isOk())
            .andExpect(view().name("clientes/formulario"));
        mvc.perform(post("/modulos/clientes").with(operativo()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(view().name("clientes/formulario"));

        mvc.perform(get("/modulos/productos").with(operativo()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Registrar producto"))));
    }

    @Test
    void usuarioOperativoNoPuedeModificarNiAdministrarCatalogos() throws Exception {
        for (var request : List.of(
                get("/modulos/clientes/1/editar"),
                get("/modulos/productos/nuevo"),
                get("/modulos/productos/1/editar"),
                get("/modulos/proveedores"),
                get("/modulos/proveedores/nuevo"),
                get("/modulos/empleados"),
                get("/modulos/empleados/nuevo"),
                get("/modulos/empleados/nueva-cuenta"))) {
            mvc.perform(request.with(operativo())).andExpect(status().isForbidden());
        }

        for (var request : List.of(
                post("/modulos/clientes/1/editar"),
                post("/modulos/clientes/1/eliminar"),
                post("/modulos/productos"),
                post("/modulos/productos/1/editar"),
                post("/modulos/productos/1/eliminar"),
                post("/modulos/proveedores"),
                post("/modulos/proveedores/1/editar"),
                post("/modulos/proveedores/1/eliminar"),
                post("/modulos/empleados"),
                post("/modulos/empleados/1/editar"),
                post("/modulos/empleados/1/eliminar"),
                post("/modulos/empleados/nueva-cuenta"))) {
            mvc.perform(request.with(operativo()).with(csrf())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(productos, proveedores, empleados, cuentas, administracion);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder altaEmpleado(String tipo) {
        return post("/modulos/empleados/nueva-cuenta").with(admin()).with(csrf())
            .param("cedulaPersona", "0912345678").param("nombresPersona", " Ana ").param("apellidosPersona", "Paz")
            .param("fechaNacimientoPersona", "1995-04-20").param("direccionPersona", "Av. Principal 123")
            .param("telefonoPersona", "0991234567").param("correoPersona", "ana@example.invalid")
            .param("nombre", "ana.paz").param("idTipo", tipo)
            .param("password", "clave-de-prueba-123").param("confirmation", "clave-de-prueba-123");
    }

    @Test
    void administradorIngresaDatosPersonalesYCuentaEnUnSoloFormulario() throws Exception {
        when(administracion.tiposOperativos()).thenReturn(List.of());
        mvc.perform(get("/modulos/empleados").with(admin()))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/modulos/empleados/nueva-cuenta")));
        mvc.perform(get("/modulos/empleados/nueva-cuenta").with(admin()))
            .andExpect(status().isOk()).andExpect(view().name("empleados/cuenta"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"cedulaPersona\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"nombresPersona\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"apellidosPersona\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"fechaNacimientoPersona\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"correoPersona\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"password\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("se creará automáticamente")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ADMINISTRADOR"))));

        mvc.perform(altaEmpleado(""))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/modulos/empleados"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attribute("successMessage",
                org.hamcrest.Matchers.containsString("ana.paz")));
        org.mockito.Mockito.verify(cuentas).registrar(org.mockito.ArgumentMatchers.argThat(f ->
                f.getCedulaPersona().equals("0912345678") && f.getNombresPersona().equals("Ana") && f.getApellidosPersona().equals("Paz")
                && f.getFechaNacimientoPersona().equals(java.time.LocalDate.of(1995, 4, 20)) && f.getCorreoPersona().equals("ana@example.invalid")
                && f.getNombre().equals("ana.paz") && f.getIdTipo() == null),
            org.mockito.ArgumentMatchers.eq("clave-de-prueba-123"), org.mockito.ArgumentMatchers.eq("clave-de-prueba-123"));
    }

    @Test
    void elAdministradorPuedeCrearOtroAdministradorQueTambienFactura() throws Exception {
        when(administracion.tiposOperativos()).thenReturn(List.of());
        mvc.perform(get("/modulos/empleados/nueva-cuenta").with(admin()))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"rol-administrador\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"rol-empleado\"")));
        mvc.perform(altaEmpleado("").param("administrador", "true"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/modulos/empleados"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attribute("successMessage",
                org.hamcrest.Matchers.startsWith("Administrador Ana Paz")));
        org.mockito.Mockito.verify(cuentas).registrar(org.mockito.ArgumentMatchers.argThat(f -> f.isAdministrador()),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void altaDeEmpleadoValidaCamposYNoDevuelveLaContrasenia() throws Exception {
        when(administracion.tiposOperativos()).thenReturn(List.of());
        mvc.perform(post("/modulos/empleados/nueva-cuenta").with(admin()).with(csrf())
                .param("cedulaPersona", "123").param("correoPersona", "no-es-correo")
                .param("password", "clave-de-prueba-123").param("confirmation", "clave-de-prueba-123"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attributeHasFieldErrors("cuentaEmpleadoForm",
                "cedulaPersona", "nombresPersona", "apellidosPersona", "fechaNacimientoPersona", "direccionPersona", "correoPersona", "nombre"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("clave-de-prueba-123"))));
        verifyNoInteractions(cuentas);

        when(cuentas.registrar(any(), any(), any())).thenThrow(new CedulaDuplicadaException("Ya existe una persona registrada con esa cédula."));
        mvc.perform(altaEmpleado("")).andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attributeHasFieldErrors("cuentaEmpleadoForm", "cedulaPersona"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Ya existe una persona con esa cédula")));

        org.mockito.Mockito.reset(cuentas);
        when(cuentas.registrar(any(), any(), any())).thenThrow(new AdministracionException("Ya existe un usuario con ese nombre."));
        mvc.perform(altaEmpleado("")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Ya existe un usuario con ese nombre.")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("clave-de-prueba-123"))))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"0912345678\"")));
    }


    @Test
    void administradorPuedeAbrirCatalogosAdministrativos() throws Exception {
        mvc.perform(get("/modulos/productos/nuevo").with(admin()))
            .andExpect(status().isOk())
            .andExpect(view().name("productos/formulario"));
        mvc.perform(get("/modulos/proveedores").with(admin()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Registrar proveedor")));
        mvc.perform(get("/modulos/empleados").with(admin()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Nuevo empleado")));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor operativo() {
        return user("operativo").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ACCOUNT_ID_2"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return user("admin").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ACCOUNT_ID_1"));
    }
}
