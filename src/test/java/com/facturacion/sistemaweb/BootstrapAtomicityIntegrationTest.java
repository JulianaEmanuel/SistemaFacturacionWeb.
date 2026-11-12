package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.*;
import com.facturacion.sistemaweb.model.TipoUsuario;
import com.facturacion.sistemaweb.repository.*;
import com.facturacion.sistemaweb.security.*;

/** Transacción y bloqueo reales; todas las escrituras de la creación son mocks. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("sqlserver")
@Import(BootstrapAtomicityIntegrationTest.Config.class)
class BootstrapAtomicityIntegrationTest {
    @TestConfiguration
    static class Config {
        @Bean BootstrapAdminService bootstrapDePrueba(AdministracionRepository usuarios,TipoUsuarioRepository tipos,AdminAccess access,ClaveSegura claves) {
            return new BootstrapAdminService(usuarios,tipos,access,claves);
        }
    }
    @Autowired BootstrapAdminService bootstrap;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AdministracionRepository usuarios;
    @MockitoBean TipoUsuarioRepository tipos;
    @MockitoBean ClaveSegura claves;
    final Set<Integer> sesiones=new HashSet<>();
    final AtomicInteger completion=new AtomicInteger(-1);
    void comprobarAmbito() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1,jdbc.queryForObject("SELECT @@TRANCOUNT",Integer.class));
        assertEquals("Exclusive",jdbc.queryForObject("SELECT APPLOCK_MODE('public','SistemaFacturacionWeb.Admin','Transaction')",String.class));
        sesiones.add(jdbc.queryForObject("SELECT @@SPID",Integer.class));
    }
    @BeforeEach void configurarEscriturasSimuladas() {
        sesiones.clear();completion.set(-1);
        doAnswer(i -> {
            new AdministracionRepository(jdbc).bloquear();comprobarAmbito();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { completion.set(status); }
            });return null;
        }).when(usuarios).bloquear();
        when(usuarios.totalUsuarios()).thenAnswer(i -> { comprobarAmbito();return 0L; });
        when(tipos.buscar("")).thenReturn(List.of());
        when(tipos.saveAndFlush(any(TipoUsuario.class))).thenAnswer(i -> { comprobarAmbito();return i.getArgument(0); });
        when(claves.codificar(anyString())).thenReturn(new byte[]{1,2,3});
    }
    void assertBloqueoLiberado() {
        assertEquals(1,sesiones.size());
        assertEquals(1,jdbc.queryForObject("SELECT APPLOCK_TEST('public','SistemaFacturacionWeb.Admin','Exclusive','Session')",Integer.class));
    }
    @Test void comprobacionYCreacionCompartenTransaccionYConexionHastaCommit() {
        doAnswer(i -> { comprobarAmbito();return null; }).when(usuarios).insertar(anyString(),nullable(Integer.class),eq(true),any(byte[].class));
        bootstrap.crear("prueba-no-persistida","entrada-simulada","entrada-simulada");
        verify(usuarios).insertar(eq("prueba-no-persistida"),nullable(Integer.class),eq(true),any());
        assertEquals(TransactionSynchronization.STATUS_COMMITTED,completion.get());assertBloqueoLiberado();
    }
    @Test void falloDeCreacionProvocaRollbackDeLaMismaTransaccion() {
        doAnswer(i -> { comprobarAmbito();throw new DataIntegrityViolationException("Fallo simulado sin ejecutar DML"); })
            .when(usuarios).insertar(anyString(),nullable(Integer.class),eq(true),any(byte[].class));
        assertThrows(DataIntegrityViolationException.class,()->bootstrap.crear("prueba-no-persistida","entrada-simulada","entrada-simulada"));
        assertEquals(TransactionSynchronization.STATUS_ROLLED_BACK,completion.get());assertBloqueoLiberado();
    }
}
