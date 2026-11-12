package com.facturacion.sistemaweb;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.facturacion.sistemaweb.controller.RecuperacionController;
import com.facturacion.sistemaweb.controller.AccesoController;
import com.facturacion.sistemaweb.security.*;
import com.facturacion.sistemaweb.service.RecuperacionContraseniaService;

@WebMvcTest({RecuperacionController.class,AccesoController.class})
@ActiveProfiles("sqlserver")
@Import({SecurityConfig.class,CapturaVistasConfig.class})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class RecuperacionSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean RecuperacionContraseniaService recuperacion;
    @MockitoBean CorreoRecuperacion correo;

    @Test void loginEnlazaRecuperacionYLaPantallaEsPublica() throws Exception {
        String login=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(true,login.contains(
            "<a href=\"/recuperar-contrasenia\">¿Olvidaste tu contraseña?</a>"));
        mvc.perform(get("/recuperar-contrasenia"))
            .andExpect(status().isOk())
            .andExpect(view().name("recuperacion/solicitar"))
            .andExpect(content().string(containsString("correo verificado asociado a tu cuenta")))
            .andExpect(content().string(containsString("action=\"/recuperar-contrasenia\"")))
            .andExpect(content().string(containsString("name=\"email\"")));
    }

    @Test void formulariosPublicosConservanCsrf() throws Exception {
        mvc.perform(post("/recuperar-contrasenia").param("email","usuario@example.test"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/restablecer-contrasenia").param("token","token").param("password","pass"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(recuperacion,correo);
    }

    @Test void respuestaPublicaEsIgualCuandoNoSeEncuentraCuenta() throws Exception {
        when(recuperacion.solicitarRestablecimiento("nadie@example.test","127.0.0.1")).thenReturn(null);
        var respuesta=mvc.perform(post("/recuperar-contrasenia").with(csrf())
            .param("email","nadie@example.test")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        verify(recuperacion).solicitarRestablecimiento(eq("nadie@example.test"),anyString());
        assertEquals(true,respuesta.contains("Si existe una cuenta activa con ese correo verificado"));
        verifyNoInteractions(correo);
        when(recuperacion.solicitarRestablecimiento("cuenta@example.test","127.0.0.1"))
            .thenReturn(new RecuperacionContraseniaService.Enlace("cuenta@example.test","token-seguro"));
        String respuestaCuenta=mvc.perform(post("/recuperar-contrasenia").with(csrf())
            .param("email","cuenta@example.test")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertEquals(respuesta.contains("Si existe una cuenta activa con ese correo verificado"),
            respuestaCuenta.contains("Si existe una cuenta activa con ese correo verificado"));
        verify(correo).enviarRestablecimiento("cuenta@example.test","token-seguro");
    }

    @Test void errorSmtpNoRevelaDetallesNiCambiaLaRespuestaPublica() throws Exception {
        when(recuperacion.solicitarRestablecimiento("cuenta@example.test","127.0.0.1"))
            .thenReturn(new RecuperacionContraseniaService.Enlace("cuenta@example.test","token-seguro"));
        doThrow(new org.springframework.mail.MailSendException("synthetic SMTP password"))
            .when(correo).enviarRestablecimiento("cuenta@example.test","token-seguro");

        String respuesta=mvc.perform(post("/recuperar-contrasenia").with(csrf())
            .param("email","cuenta@example.test")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertEquals(true,respuesta.contains("Si existe una cuenta activa con ese correo verificado"));
        assertEquals(false,respuesta.contains("synthetic SMTP password"));
    }

    @Test void abrirEnlacesNoConsumeTokensYConfirmarCorreoRequiereCsrf() throws Exception {
        mvc.perform(get("/restablecer-contrasenia").param("token","token-simulacion")).andExpect(status().isOk())
            .andExpect(content().string(containsString("token-simulacion")));
        mvc.perform(get("/verificar-correo").param("token","token-simulacion")).andExpect(status().isOk());
        mvc.perform(post("/verificar-correo").param("token","token-simulacion")).andExpect(status().isForbidden());
        verifyNoInteractions(recuperacion);
    }

    @Test void solicitudDeCorreoRequiereSesionYSoloTomaSuIdentidadAutenticada() throws Exception {
        mvc.perform(get("/cuenta/correo")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        when(recuperacion.solicitarVerificacion(73L,"nuevo@example.test","127.0.0.1"))
            .thenReturn(new RecuperacionContraseniaService.Enlace("nuevo@example.test","token-seguro"));
        mvc.perform(post("/cuenta/correo").with(user("usuario").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ACCOUNT_ID_73"))).with(csrf())
            .param("email","nuevo@example.test")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Si el correo puede asociarse a esta cuenta")));
        verify(recuperacion).solicitarVerificacion(73L,"nuevo@example.test","127.0.0.1");
        verify(correo).enviarVerificacion("nuevo@example.test","token-seguro");
    }
}
