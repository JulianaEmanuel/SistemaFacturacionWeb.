package com.facturacion.sistemaweb;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.facturacion.sistemaweb.controller.*;
import com.facturacion.sistemaweb.security.*;
import com.facturacion.sistemaweb.service.InicioService;

@WebMvcTest({HomeController.class,AccesoController.class,ModuleController.class}) @ActiveProfiles("sqlserver") @Import({SecurityConfig.class,CapturaVistasConfig.class})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class PortalControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean InicioService inicio;
    @MockitoBean UserDetailsService users;
    @MockitoBean(name="adminAccess") AdminAccess admin;
    @Test void plantillaDeModuloConservaNavegacionYTitulo() throws Exception {
        mvc.perform(get("/modulos/clientes").with(user("operador"))).andExpect(status().isOk()).andExpect(content().string(containsString("Clientes | Sistema de Facturación Web")));
    }
    @Test void loginMantieneCsrfYElControlAccesible() throws Exception {
        var html=mvc.perform(get("/login?error")).andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")))
            .andExpect(content().string(containsString("aria-controls=\"password\""))).andExpect(content().string(containsString("Comprueba tus credenciales"))).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Files.createDirectories(Path.of("target/ui-check"));Files.writeString(Path.of("target/ui-check/login.html"),html);
        mvc.perform(post("/login").param("username","cuenta-simulada").param("password","dato-simulado")).andExpect(status().isForbidden());
    }
    @Test void inicioAnonimoSeBloquea() throws Exception {mvc.perform(get("/")).andExpect(redirectedUrl("/login"));verifyNoInteractions(inicio);}
    @Test void inicioRenderizaExactamenteCuatroAccesosYSuActividad() throws Exception {
        var p=new InicioService.Panel(7,9,3L,new BigDecimal("15.1250"),List.of(new InicioService.Actividad(21,LocalDateTime.of(2026,10,30,9,20),"Cliente de prueba",new BigDecimal("4.6000"))),"Datos de prueba",null);
        when(inicio.consultar()).thenReturn(p);when(admin.allowed(any())).thenReturn(true);
        String html=mvc.perform(get("/").with(user("Cuenta de prueba"))).andExpect(status().isOk()).andExpect(content().string(containsString("Factura #21"))).andExpect(content().string(containsString("15.13")))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(4,html.split("class=\"quick-link\"",-1).length-1);assertTrue(html.contains("/modulos/facturas/nuevo"));assertTrue(html.contains("/modulos/tipos-usuario"));
        Files.createDirectories(Path.of("target/ui-check"));Files.writeString(Path.of("target/ui-check/inicio.html"),html);
    }
    @Test void noAdministradorNoVeEnlacesAdministrativos() throws Exception {
        when(inicio.consultar()).thenReturn(new InicioService.Panel(0,0,0L,BigDecimal.ZERO,List.of(),"Tus ventas",null));
        mvc.perform(get("/").with(user("operador"))).andExpect(content().string(not(containsString("href=\"/modulos/usuarios\"")))).andExpect(content().string(containsString("Aún no hay actividad")));
    }
}
