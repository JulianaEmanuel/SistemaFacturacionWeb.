package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import com.facturacion.sistemaweb.model.UsuarioResumen;
import com.facturacion.sistemaweb.repository.*;
import com.facturacion.sistemaweb.security.*;
import com.facturacion.sistemaweb.service.*;

@SpringJUnitConfig(AdministracionMethodSecurityTest.Config.class)
class AdministracionMethodSecurityTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean AdministracionRepository repository() { return mock(AdministracionRepository.class); }
        @Bean TipoUsuarioRepository tipos() { return mock(TipoUsuarioRepository.class); }
        @Bean AdminAccess adminAccess(AdministracionRepository repository) { return new AdminAccess(repository,"ADMINISTRADOR"); }
        @Bean AdministracionService service(AdministracionRepository repository,TipoUsuarioRepository tipos,AdminAccess access) {
            return new AdministracionService(repository,tipos,access,new ClaveSegura(),Validation.buildDefaultValidatorFactory().getValidator());
        }
    }
    @Autowired AdministracionService service;
    @Autowired AdministracionRepository repository;
    @BeforeEach void resetMocks() { reset(repository); }
    @Test @org.springframework.security.test.context.support.WithAnonymousUser
    void servicioAnonimoBloqueado() { assertThrows(AccessDeniedException.class,()->service.usuarios("",null,null));verifyNoInteractions(repository); }
    @Test @WithMockUser(username="operador",authorities="ACCOUNT_ID_8")
    void servicioNoAdminBloqueado() {
        when(repository.buscarNombre("operador")).thenReturn(Optional.of(new UsuarioResumen(8L,4,"operador","OPERADOR",true)));
        assertThrows(AccessDeniedException.class,()->service.estado(9L,false));verify(repository,never()).bloquear();
    }
    @Test @WithMockUser(username="admin",authorities="ACCOUNT_ID_9")
    void servicioAdminPermitidoSinIdsDeTipoFijos() {
        when(repository.buscarNombre("admin")).thenReturn(Optional.of(new UsuarioResumen(9L,42,"admin","ADMINISTRADOR",true)));
        when(repository.esAdmin("admin","ADMINISTRADOR")).thenReturn(true);
        service.usuarios("",null,null);verify(repository).buscar("",null,null);
    }
    @Test @WithMockUser(username="admin",authorities="ACCOUNT_ID_9")
    void sesionAnteriorNoHeredaCuentaReutilizada() {
        when(repository.buscarNombre("admin")).thenReturn(Optional.of(new UsuarioResumen(99L,42,"admin","ADMINISTRADOR",true)));
        assertThrows(AccessDeniedException.class,()->service.usuarios("",null,null));
    }
}
