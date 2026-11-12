package com.facturacion.sistemaweb;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.facturacion.sistemaweb.controller.*;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.repository.AdministracionRepository;
import com.facturacion.sistemaweb.security.*;
import com.facturacion.sistemaweb.service.*;

@WebMvcTest({AdministracionController.class,AccesoController.class,HomeController.class,ModuleController.class})
@Import({SecurityConfig.class,ClaveSegura.class,CapturaVistasConfig.class}) @ActiveProfiles("sqlserver")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AdministracionSecurityTest {
    @Test void listaDeTiposYPantallaDePermisosSeRenderizan() throws Exception {
        var tipo=new TipoUsuario();tipo.setNombre("OPERADOR");tipo.setDescripcion("Operaciones del sistema");
        when(service.tipos("")).thenReturn(List.of(tipo));
        mvc.perform(get("/modulos/tipos-usuario").with(admin())).andExpect(status().isOk());
        mvc.perform(get("/sin-permiso").with(operador())).andExpect(status().isForbidden()).andExpect(content().string(containsString("Acceso denegado")));
    }
    @Autowired MockMvc mvc;
    @Autowired ClaveSegura claves;
    @MockitoBean AdministracionService service;
    @MockitoBean AdministracionRepository repository;
    @MockitoBean(name="adminAccess") AdminAccess access;
    @MockitoBean UserDetailsService users;
    static final String RAW="ficticia-para-prueba-local";
    @BeforeEach void setup() {
        when(access.allowed(any())).thenAnswer(i -> { Authentication a=i.getArgument(0);return a!=null && a.isAuthenticated() && a.getName().equals("admin"); });
        when(repository.buscarNombre(anyString())).thenAnswer(i -> Optional.of(new UsuarioResumen(1L,2,i.getArgument(0),"TIPO",true)));
    }
    org.springframework.test.web.servlet.request.RequestPostProcessor admin() { return user("admin").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ACCOUNT_ID_1")); }
    org.springframework.test.web.servlet.request.RequestPostProcessor operador() { return user("operador").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ACCOUNT_ID_1")); }
    UsuarioResumen usuario() { return new UsuarioResumen(9L,2,"operador   ","OPERADOR   ",true); }
    @Test void anonimoBloqueadoEnTodosLosModulos() throws Exception {
        for(String path:List.of("/","/modulos/clientes","/modulos/proveedores","/modulos/productos","/modulos/empleados","/modulos/usuarios","/modulos/tipos-usuario","/modulos/facturas","/modulos/reportes"))
            mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(service);
    }
    @Test void loginRenderizaCsrfSinRegistroPublico() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("_csrf")));
    }
    @Test void loginCorrectoEIncorrectoConArgon2id() throws Exception {
        when(users.loadUserByUsername("admin")).thenReturn(User.withUsername("admin").password(claves.encode(RAW)).authorities("ACCOUNT_ID_1").build());
        mvc.perform(post("/login").with(csrf()).param("username","admin").param("password",RAW))
            .andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername("admin"));
        mvc.perform(post("/login").with(csrf()).param("username","admin").param("password","incorrecta"))
            .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }
    @Test void usuarioInactivoNoIniciaSesion() throws Exception {
        when(users.loadUserByUsername("inactivo")).thenReturn(User.withUsername("inactivo").password(claves.encode(RAW)).authorities("USER").disabled(true).build());
        mvc.perform(post("/login").with(csrf()).param("username","inactivo").param("password",RAW)).andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
    }
    @Test void operadorNoAccedeAdministracionNiPosts() throws Exception {
        for(String path:List.of("/modulos/usuarios","/modulos/tipos-usuario","/modulos/usuarios/nuevo","/modulos/usuarios/9/contrasenia"))
            mvc.perform(get(path).with(operador())).andExpect(status().isForbidden());
        mvc.perform(post("/modulos/usuarios/9/desactivar").with(operador()).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void csrfProtegeInclusoAdministradorYLogin() throws Exception {
        mvc.perform(post("/modulos/usuarios/9/desactivar").with(admin())).andExpect(status().isForbidden());
        mvc.perform(post("/login").param("username","admin").param("password",RAW)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void adminListaUsuariosYTipos() throws Exception {
        when(service.usuarios("",null,null)).thenReturn(List.of(usuario()));
        mvc.perform(get("/modulos/usuarios").with(admin())).andExpect(status().isOk()).andExpect(view().name("usuarios/lista"))
            .andExpect(content().string(containsString("operador"))).andExpect(content().string(not(containsString("$argon2id$"))));
        mvc.perform(get("/modulos/tipos-usuario").with(admin())).andExpect(status().isOk());
    }
    @Test void filtrosYFormularios() throws Exception {
        mvc.perform(get("/modulos/usuarios").with(admin()).param("q","operador").param("tipo","2").param("activo","true")).andExpect(status().isOk());
        verify(service).usuarios("operador",2,true);
        mvc.perform(get("/modulos/usuarios/nuevo").with(admin())).andExpect(status().isOk());
        mvc.perform(get("/modulos/tipos-usuario/nuevo").with(admin())).andExpect(status().isOk());
        when(service.usuario(9L)).thenReturn(usuario());
        mvc.perform(get("/modulos/usuarios/9/editar").with(admin())).andExpect(status().isOk()).andExpect(content().string(not(containsString("name=\"password\""))));
        mvc.perform(get("/modulos/usuarios/9/contrasenia").with(admin())).andExpect(status().isOk());
        var tipo=new TipoUsuario();tipo.setNombre("OPERADOR");tipo.setDescripcion("Operaciones");when(service.tipo(2)).thenReturn(tipo);
        mvc.perform(get("/modulos/tipos-usuario/2/editar").with(admin())).andExpect(status().isOk());
    }
    @Test void registraEditaActivaDesactivaYCambiaClaveConMocks() throws Exception {
        mvc.perform(post("/modulos/usuarios").with(admin()).with(csrf()).param("nombre","operador").param("idTipo","2").param("password",RAW).param("confirmation",RAW)).andExpect(redirectedUrl("/modulos/usuarios"));
        verify(service).registrar(any(),eq(RAW),eq(RAW));
        mvc.perform(post("/modulos/usuarios/9/editar").with(admin()).with(csrf()).param("nombre","nuevo").param("idTipo","2")).andExpect(redirectedUrl("/modulos/usuarios"));
        verify(service).editar(eq(9L),any());
        mvc.perform(post("/modulos/usuarios/9/activar").with(admin()).with(csrf())).andExpect(redirectedUrl("/modulos/usuarios"));
        mvc.perform(post("/modulos/usuarios/9/desactivar").with(admin()).with(csrf())).andExpect(redirectedUrl("/modulos/usuarios"));
        verify(service).estado(9L,true);verify(service).estado(9L,false);
        mvc.perform(post("/modulos/usuarios/9/contrasenia").with(admin()).with(csrf()).param("password",RAW).param("confirmation",RAW)).andExpect(redirectedUrl("/modulos/usuarios"));
        verify(service).cambiarClave(9L,RAW,RAW);
    }
    @Test void validacionNoDevuelveSecretosAlHtmlNiModelo() throws Exception {
        var response=mvc.perform(post("/modulos/usuarios").with(admin()).with(csrf()).param("nombre","").param("password",RAW).param("confirmation",RAW))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("usuarioForm","nombre","idTipo"))
            .andExpect(content().string(not(containsString(RAW)))).andReturn();
        assertFalse(response.getModelAndView().getModel().containsKey("password"));
        verify(service,never()).registrar(any(),any(),any());
    }
    @Test void duplicadosYUltimoAdministradorSeComunican() throws Exception {
        doThrow(new AdministracionException("Ya existe un usuario con ese nombre.")).when(service).registrar(any(),any(),any());
        mvc.perform(post("/modulos/usuarios").with(admin()).with(csrf()).param("nombre","duplicado").param("idTipo","2").param("password",RAW).param("confirmation",RAW))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Ya existe"))).andExpect(content().string(not(containsString(RAW))));
        doThrow(new AdministracionException("Último administrador activo.")).when(service).estado(9L,false);
        mvc.perform(post("/modulos/usuarios/9/desactivar").with(admin()).with(csrf())).andExpect(flash().attribute("errorMessage","Último administrador activo."));
    }
    @Test void tiposRegistroEdicionYProteccion() throws Exception {
        mvc.perform(post("/modulos/tipos-usuario").with(admin()).with(csrf()).param("nombre","OPERADOR").param("descripcion","Operaciones"))
            .andExpect(redirectedUrl("/modulos/tipos-usuario"));verify(service).guardarTipo(isNull(),any());
        mvc.perform(post("/modulos/tipos-usuario/2/editar").with(admin()).with(csrf()).param("nombre","OPERADOR").param("descripcion","Actualizado"))
            .andExpect(redirectedUrl("/modulos/tipos-usuario"));verify(service).guardarTipo(eq(2),any());
        doThrow(new AdministracionException("Tipo asociado a usuarios.")).when(service).eliminarTipo(2);
        mvc.perform(post("/modulos/tipos-usuario/2/eliminar").with(admin()).with(csrf())).andExpect(flash().attribute("errorMessage","Tipo asociado a usuarios."));
    }
    @Test void sesionDesactivadaORenombreNoHeredaOtraCuenta() throws Exception {
        when(repository.buscarNombre("admin")).thenReturn(Optional.of(new UsuarioResumen(99L,2,"admin","ADMINISTRADOR",true)));
        mvc.perform(get("/modulos/usuarios").with(admin())).andExpect(redirectedUrl("/login?expired"));verifyNoInteractions(service);
    }
    @Test void logoutSoloPostConCsrf() throws Exception {
        mvc.perform(post("/logout").with(admin())).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(admin()).with(csrf())).andExpect(redirectedUrl("/login?logout")).andExpect(unauthenticated());
    }
}
