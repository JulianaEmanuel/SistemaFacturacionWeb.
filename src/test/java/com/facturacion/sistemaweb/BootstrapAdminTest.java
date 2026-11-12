package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.facturacion.sistemaweb.security.*;
import com.facturacion.sistemaweb.repository.*;
import com.facturacion.sistemaweb.model.TipoUsuario;
import com.facturacion.sistemaweb.service.AdministracionException;

class BootstrapAdminTest {
    @Test void sinConsolaNoSolicitaNiCreaCuenta() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.console()==null);
        var service=mock(BootstrapAdminService.class);
        assertThrows(IllegalStateException.class,()->new BootstrapAdminRunner(service).run(null));
        verifyNoInteractions(service);
    }
    @Test void creaSoloInicialSinIdFijo() {
        var u=mock(AdministracionRepository.class);var t=mock(TipoUsuarioRepository.class);var a=mock(AdminAccess.class);var c=mock(ClaveSegura.class);
        when(a.reservado()).thenReturn("ADMINISTRADOR");when(t.buscar("")).thenReturn(List.of());
        TipoUsuario tipo=mock(TipoUsuario.class);when(tipo.getId()).thenReturn(42);when(t.saveAndFlush(any())).thenReturn(tipo);
        when(c.codificar(any())).thenReturn(new byte[]{1});
        new BootstrapAdminService(u,t,a,c).crear("inicial","ficticia-solo-pruebas","ficticia-solo-pruebas");
        var orden=inOrder(u);orden.verify(u).bloquear();orden.verify(u).totalUsuarios();
        verify(u).insertar(eq("inicial"),eq(42),eq(true),any());
    }
    @Test void segundaEjecucionNoCreaNada() {
        var u=mock(AdministracionRepository.class);var t=mock(TipoUsuarioRepository.class);when(u.totalUsuarios()).thenReturn(1L);
        var service=new BootstrapAdminService(u,t,mock(AdminAccess.class),mock(ClaveSegura.class));
        assertThrows(AdministracionException.class,()->service.crear("inicial","",""));verify(u,never()).insertar(any(),any(),anyBoolean(),any());verifyNoInteractions(t);
    }
    @Test void noAceptaNombreInvalidoNiConfirmacionIncorrecta() {
        var u=mock(AdministracionRepository.class);var t=mock(TipoUsuarioRepository.class);
        var service=new BootstrapAdminService(u,t,mock(AdminAccess.class),new ClaveSegura());
        assertThrows(AdministracionException.class,()->service.crear(" ","",""));
        assertThrows(AdministracionException.class,()->service.crear("inicial","ficticia-solo-pruebas","distinta"));verifyNoInteractions(t);
    }
}
