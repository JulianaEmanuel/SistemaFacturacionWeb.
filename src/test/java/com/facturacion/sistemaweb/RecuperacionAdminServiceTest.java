package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import com.facturacion.sistemaweb.repository.*;
import com.facturacion.sistemaweb.repository.RecuperacionAdminRepository.Cuenta;
import com.facturacion.sistemaweb.security.*;
import com.facturacion.sistemaweb.service.AdministracionException;

@SpringJUnitConfig(RecuperacionAdminServiceTest.Config.class)
@ActiveProfiles({"sqlserver","recuperar-admin"})
class RecuperacionAdminServiceTest {
    @Configuration @EnableTransactionManagement static class Config {
        @Bean DataSource ds() {return mock(DataSource.class);}
        @Bean DataSourceTransactionManager transactions(DataSource ds) {return new DataSourceTransactionManager(ds);}
        @Bean RecuperacionAdminRepository repo() {return mock(RecuperacionAdminRepository.class);}
        @Bean AdministracionRepository adminRepo() {return mock(AdministracionRepository.class);}
        @Bean ClaveSegura claves() {return new ClaveSegura();}
        @Bean AutenticacionUsuarios auth() {return mock(AutenticacionUsuarios.class);}
        @Bean AdminAccess admin() {return mock(AdminAccess.class);}
        @Bean ApplicationArguments args() {return new DefaultApplicationArguments("--recuperar-admin");}
        @Bean RecuperacionAdminService service(RecuperacionAdminRepository r,AdministracionRepository a,ClaveSegura c,AutenticacionUsuarios u,AdminAccess admin,ApplicationArguments args,ApplicationContext ctx) {
            return new RecuperacionAdminService(r,a,c,u,admin,args,ctx);
        }
    }
    @Autowired RecuperacionAdminService service;
    @Autowired RecuperacionAdminRepository repo;
    @Autowired AdministracionRepository adminRepo;
    @Autowired AutenticacionUsuarios auth;
    @Autowired AdminAccess admin;
    @Autowired DataSource ds;
    final List<Connection> conexiones=new CopyOnWriteArrayList<>();
    final AtomicReference<byte[]> almacenado=new AtomicReference<>();
    final AtomicReference<byte[]> temporal=new AtomicReference<>();
    final Cuenta cuenta=new Cuenta(824,37,"cuenta-simulada   ",true);
    @BeforeEach void configurar() throws Exception {
        adminRepo=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(adminRepo);
        reset(repo,adminRepo,auth,admin,ds);conexiones.clear();
        when(ds.getConnection()).thenAnswer(i->{var c=mock(Connection.class);when(c.getAutoCommit()).thenReturn(true);conexiones.add(c);return c;});
        when(admin.reservado()).thenReturn("ADMINISTRADOR");when(admin.allowed(any())).thenReturn(true);
        when(repo.bloquearCuenta(824,"ADMINISTRADOR")).thenReturn(cuenta);
        when(repo.actualizarClave(eq(824L),eq("ADMINISTRADOR"),any())).thenAnswer(i->{
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            byte[] b=i.getArgument(2);temporal.set(b);almacenado.set(b.clone());return 1;
        });
        when(auth.loadUserByUsername("cuenta-simulada")).thenAnswer(i->User.withUsername("cuenta-simulada")
            .password(new String(almacenado.get(),StandardCharsets.UTF_8)).authorities("USER","ACCOUNT_ID_824").build());
    }
    @Test void cambiaSoloLaClaveYVerificaAntesDeCommit() throws Exception {
        service.recuperar(824,"frase-ficticia-de-prueba","frase-ficticia-de-prueba");
        verify(conexiones.getFirst()).commit();verify(conexiones.getFirst(),never()).rollback();
        var orden=inOrder(repo,adminRepo,auth);
        orden.verify(repo).exigirPermisos();orden.verify(adminRepo).bloquear();orden.verify(repo).bloquearCuenta(824,"ADMINISTRADOR");
        orden.verify(repo).exigirSinTriggers();orden.verify(repo).actualizarClave(eq(824L),eq("ADMINISTRADOR"),any());
        orden.verify(auth).loadUserByUsername("cuenta-simulada");orden.verify(repo).bloquearCuenta(824,"ADMINISTRADOR");
        assertTrue(todosCero(temporal.get()));assertFalse(todosCero(almacenado.get()));
        verifyNoMoreInteractions(repo);verify(adminRepo,never()).insertar(any(),any(),anyBoolean(),any());
    }
    @Test void permisosInsuficientesImpidenTodaActualizacion() throws Exception {
        doThrow(new AdministracionException("Sin permisos")).when(repo).exigirPermisos();
        assertThrows(AdministracionException.class,()->service.recuperar(824,"frase-ficticia-de-prueba","frase-ficticia-de-prueba"));
        verify(repo,never()).actualizarClave(anyLong(),anyString(),any());verifyNoInteractions(auth,adminRepo);verify(conexiones.getFirst()).rollback();
    }
    @Test void cuentaNoAdministradoraOInactivaSeRechaza() throws Exception {
        when(repo.bloquearCuenta(anyLong(),anyString())).thenThrow(new AdministracionException("No recuperable"));
        assertThrows(AdministracionException.class,()->service.recuperar(824,"frase-ficticia-de-prueba","frase-ficticia-de-prueba"));
        verify(repo,never()).actualizarClave(anyLong(),anyString(),any());verify(conexiones.getFirst()).rollback();
    }
    @Test void triggersActivosImpidenLaActualizacion() throws Exception {
        doThrow(new AdministracionException("Trigger activo")).when(repo).exigirSinTriggers();
        assertThrows(AdministracionException.class,()->service.recuperar(824,"frase-ficticia-de-prueba","frase-ficticia-de-prueba"));
        verify(repo,never()).actualizarClave(anyLong(),anyString(),any());verify(conexiones.getFirst()).rollback();
    }
    @Test void falloDeVerificacionRevierteYLimpiaLosBytes() throws Exception {
        when(auth.loadUserByUsername(anyString())).thenReturn(User.withUsername("cuenta-simulada").password("formato-no-admitido").authorities("ACCOUNT_ID_824").build());
        assertThrows(AdministracionException.class,()->service.recuperar(824,"frase-ficticia-de-prueba","frase-ficticia-de-prueba"));
        verify(conexiones.getFirst()).rollback();assertTrue(todosCero(temporal.get()));
    }
    @Test void perdidaDePrivilegiosRevierte() throws Exception {
        when(admin.allowed(any())).thenReturn(false);
        assertThrows(AdministracionException.class,()->service.recuperar(824,"frase-ficticia-de-prueba","frase-ficticia-de-prueba"));verify(conexiones.getFirst()).rollback();
    }
    @Test void cambiosDeMetadatosRevierte() throws Exception {
        when(repo.bloquearCuenta(824,"ADMINISTRADOR")).thenReturn(cuenta,new Cuenta(824,99,cuenta.nombre(),true));
        assertThrows(AdministracionException.class,()->service.recuperar(824,"frase-ficticia-de-prueba","frase-ficticia-de-prueba"));verify(conexiones.getFirst()).rollback();
    }
    @Test void recuentoDeFilasDistintoDeUnoRevierte() throws Exception {
        when(repo.actualizarClave(anyLong(),anyString(),any())).thenReturn(0);
        assertThrows(AdministracionException.class,()->service.recuperar(824,"frase-ficticia-de-prueba","frase-ficticia-de-prueba"));verifyNoInteractions(auth);verify(conexiones.getFirst()).rollback();
    }
    @Test void confirmacionIncorrectaNoEscribe() {
        assertThrows(AdministracionException.class,()->service.recuperar(824,"frase-ficticia-de-prueba","otra-confirmacion"));verifyNoInteractions(repo,adminRepo,auth);
    }
    @Test void listarSoloConsultaSinBloquearNiActualizar() throws Exception {
        when(repo.candidatas("ADMINISTRADOR")).thenReturn(List.of(cuenta));assertEquals(1,service.candidatas().size());
        verify(repo).exigirPermisos();verify(repo).exigirSinTriggers();verify(repo).candidatas("ADMINISTRADOR");verifyNoMoreInteractions(repo);verifyNoInteractions(adminRepo,auth);
    }
    @Test void recuperacionesConcurrentesSeSerializanHastaCompletarLaTransaccion() throws Exception {
        var lock=new ReentrantLock();var primeraActualizada=new CountDownLatch(1);var ambosIntentan=new CountDownLatch(2);var terminarPrimera=new CountDownLatch(1);var llamadas=new AtomicInteger();
        doAnswer(i->{ambosIntentan.countDown();lock.lock();TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){lock.unlock();}});return null;}).when(adminRepo).bloquear();
        when(repo.actualizarClave(eq(824L),anyString(),any())).thenAnswer(i->{byte[] b=i.getArgument(2);almacenado.set(b.clone());if(llamadas.incrementAndGet()==1){primeraActualizada.countDown();assertTrue(terminarPrimera.await(10,TimeUnit.SECONDS));}return 1;});
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->service.recuperar(824,"primera-frase-ficticia","primera-frase-ficticia"));
            assertTrue(primeraActualizada.await(10,TimeUnit.SECONDS));
            var b=pool.submit(()->service.recuperar(824,"segunda-frase-ficticia","segunda-frase-ficticia"));
            try {assertTrue(ambosIntentan.await(10,TimeUnit.SECONDS));assertEquals(1,llamadas.get());}finally{terminarPrimera.countDown();}
            a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        assertEquals(2,llamadas.get());for(var c:conexiones)verify(c).commit();
    }
    private boolean todosCero(byte[] bytes) {for(byte b:bytes)if(b!=0)return false;return true;}
}
