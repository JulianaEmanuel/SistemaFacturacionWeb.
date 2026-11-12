package com.facturacion.sistemaweb;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.facturacion.sistemaweb.controller.PerfilController;
import com.facturacion.sistemaweb.security.SecurityConfig;
import com.facturacion.sistemaweb.service.FotoPerfilService;

@WebMvcTest(PerfilController.class)
@ActiveProfiles("sqlserver")
@Import({SecurityConfig.class,CapturaVistasConfig.class})
class PerfilControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean FotoPerfilService fotos;
    @MockitoBean UserDetailsService autenticacion;
    @Test void anonimoNoAccedeAlPerfilNiALasFotos() throws Exception {
        mvc.perform(get("/perfil")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/perfil/foto")).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(fotos);
    }
    @Test void muestraNombreFormularioFotoYCsrf() throws Exception {
        mvc.perform(get("/perfil").with(user("Ana Administradora")))
            .andExpect(status().isOk()).andExpect(view().name("perfil"))
            .andExpect(content().string(containsString("Ana Administradora")))
            .andExpect(content().string(containsString("Mi perfil · Cambiar foto")))
            .andExpect(content().string(containsString("name=\"_csrf\"")))
            .andExpect(content().string(containsString("multipart/form-data")));
    }
    @Test void cambioExigeCsrf() throws Exception {
        mvc.perform(multipart("/perfil/foto").file(new MockMultipartFile("foto",new byte[]{1})).with(user("ana")))
            .andExpect(status().isForbidden());verifyNoInteractions(fotos);
    }
    @Test void cambioSoloUtilizaLaCuentaAutenticada() throws Exception {
        var archivo=new MockMultipartFile("foto","foto.png","image/png",new byte[]{1});
        mvc.perform(multipart("/perfil/foto").file(archivo).param("usuario","otra-cuenta").with(user("ana")).with(csrf()))
            .andExpect(redirectedUrl("/perfil")).andExpect(flash().attributeExists("exitoPerfil"));
        verify(fotos).guardar(argThat(a->a.getName().equals("ana")),eq(archivo));
    }
    @Test void imagenPrivadaNoSeAlmacenaEnCache() throws Exception {
        when(fotos.leer(any(Authentication.class))).thenReturn(new byte[]{1,2,3});
        mvc.perform(get("/perfil/foto").with(user("ana"))).andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG)).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("X-Content-Type-Options","nosniff"));
    }
    @Test void muestraAvatarInicialYErroresDeValidacion() throws Exception {
        mvc.perform(get("/perfil/foto").with(user("ana"))).andExpect(status().isOk()).andExpect(content().contentType("image/svg+xml"));
        doThrow(new IllegalArgumentException("Selecciona una foto JPG o PNG.")).when(fotos).guardar(any(),any());
        mvc.perform(multipart("/perfil/foto").file(new MockMultipartFile("foto",new byte[]{1})).with(user("ana")).with(csrf()))
            .andExpect(redirectedUrl("/perfil")).andExpect(flash().attribute("errorPerfil","Selecciona una foto JPG o PNG."));
    }
}
