package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.Console;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import com.facturacion.sistemaweb.repository.*;
import com.facturacion.sistemaweb.security.*;
import com.facturacion.sistemaweb.service.AdministracionException;

class RecuperacionAdminRunnerTest {
    @Test void sinTerminalNoConsultaNiRecupera() {
        var service=mock(RecuperacionAdminService.class);
        assertThrows(AdministracionException.class,()->new RecuperacionAdminRunner(service,()->null).run(new DefaultApplicationArguments("--recuperar-admin")));verifyNoInteractions(service);
    }
    @Test void perfilSinArgumentoNoHabilitaLaRecuperacion() {
        var service=mock(RecuperacionAdminService.class);
        assertThrows(AdministracionException.class,()->new RecuperacionAdminRunner(service,()->mock(Console.class)).run(new DefaultApplicationArguments()));verifyNoInteractions(service);
    }
    @Test void confirmaSeleccionYBorraEntradasOcultas() {
        var service=mock(RecuperacionAdminService.class);var console=mock(Console.class);
        when(service.candidatas()).thenReturn(List.of(new RecuperacionAdminRepository.Cuenta(824,37,"simulado   ",true)));
        when(console.readLine(anyString())).thenReturn("824","RECUPERAR");
        char[] a="frase-ficticia-de-prueba".toCharArray(),b=a.clone();when(console.readPassword(anyString())).thenReturn(a,b);
        new RecuperacionAdminRunner(service,()->console).run(new DefaultApplicationArguments("--recuperar-admin"));
        verify(service).recuperar(824,"frase-ficticia-de-prueba","frase-ficticia-de-prueba");assertTrue(cero(a));assertTrue(cero(b));
    }
    @Test void cancelacionLimpiaLaPrimeraEntradaSinActualizar() {
        var service=mock(RecuperacionAdminService.class);var console=mock(Console.class);
        when(service.candidatas()).thenReturn(List.of(new RecuperacionAdminRepository.Cuenta(824,37,"simulado",true)));
        when(console.readLine(anyString())).thenReturn("824","RECUPERAR");char[] a="frase-ficticia-de-prueba".toCharArray();
        when(console.readPassword(anyString())).thenReturn(a,(char[])null);
        assertThrows(AdministracionException.class,()->new RecuperacionAdminRunner(service,()->console).run(new DefaultApplicationArguments("--recuperar-admin")));
        assertTrue(cero(a));verify(service,never()).recuperar(anyLong(),anyString(),anyString());
    }
    @Test void seleccionAjenaNoPideClaves() {
        var service=mock(RecuperacionAdminService.class);var console=mock(Console.class);
        when(service.candidatas()).thenReturn(List.of(new RecuperacionAdminRepository.Cuenta(824,37,"simulado",true)));
        when(console.readLine(anyString())).thenReturn("825");
        assertThrows(AdministracionException.class,()->new RecuperacionAdminRunner(service,()->console).run(new DefaultApplicationArguments("--recuperar-admin")));
        verify(console,never()).readPassword(anyString());verify(service,never()).recuperar(anyLong(),anyString(),anyString());
    }
    @Test void ningunComponenteExisteEnUnContextoWebInclusoConElPerfil() {
        new WebApplicationContextRunner().withPropertyValues("spring.profiles.active=sqlserver,recuperar-admin")
            .withUserConfiguration(RecuperacionAdminService.class,RecuperacionAdminRunner.class,RecuperacionAdminRepository.class)
            .run(ctx->{assertFalse(ctx.containsBean("recuperacionAdminService"));assertTrue(ctx.getBeansOfType(RecuperacionAdminService.class).isEmpty());assertTrue(ctx.getBeansOfType(RecuperacionAdminRepository.class).isEmpty());assertTrue(ctx.getBeansOfType(RecuperacionAdminRunner.class).isEmpty());});
    }
    @Test void servicioRechazaContextoWebYFaltaDeArgumentoSinAccederASql() {
        var repo=mock(RecuperacionAdminRepository.class);
        try(var web=new GenericWebApplicationContext();var local=new GenericApplicationContext()) {
            web.getEnvironment().setActiveProfiles("sqlserver","recuperar-admin");local.getEnvironment().setActiveProfiles("sqlserver","recuperar-admin");
            var a=new RecuperacionAdminService(repo,null,null,null,null,new DefaultApplicationArguments("--recuperar-admin"),web);
            var b=new RecuperacionAdminService(repo,null,null,null,null,new DefaultApplicationArguments(),local);
            assertThrows(AdministracionException.class,a::candidatas);assertThrows(AdministracionException.class,b::candidatas);verifyNoInteractions(repo);
        }
    }
    @Test void argumentosAlternativosSeRechazanAntesDeArrancarSpring() {
        for(String[] args:new String[][]{{"--recuperar-admin","--server.port=8080"},{"--recuperar-admin=true"},{"--bootstrap-admin","--recuperar-admin"},{"--spring.profiles.active=recuperar-admin"}})
            assertThrows(IllegalArgumentException.class,()->SistemawebApplication.main(args));
    }
    private boolean cero(char[] chars) {for(char c:chars)if(c!=0)return false;return true;}
}
