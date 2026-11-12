package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.facturacion.sistemaweb.repository.AdministracionRepository;

/** Solo estado de transacciones, SELECT y bloqueos temporales; nunca DML ni credenciales. */
@SpringBootTest @ActiveProfiles("sqlserver")
class BootstrapLockIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired AdministracionRepository repository;
    private int trancount() { return jdbc.queryForObject("SELECT @@TRANCOUNT",Integer.class); }
    private String lockMode() {
        return jdbc.queryForObject("SELECT APPLOCK_MODE('public','SistemaFacturacionWeb.Admin','Transaction')",String.class);
    }
    @Test void reproduceMenos999YCorrigeLaPrimeraOperacionDeLaTransaccion() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertFalse(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) c -> c.getAutoCommit()));
            assertEquals(0,trancount());
            Integer antiguo=jdbc.queryForObject("DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource='SistemaFacturacionWeb.LockRegression', @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=0; SELECT @r",Integer.class);
            assertEquals(-999,antiguo);assertEquals(0,trancount());
            repository.bloquear();
            assertEquals(1,trancount());assertEquals("Exclusive",lockMode());
            repository.totalUsuarios();assertEquals("Exclusive",lockMode());
            status.setRollbackOnly();
        });
        assertDisponible();
    }
    private void assertDisponible() {
        assertEquals(1,jdbc.queryForObject("SELECT APPLOCK_TEST('public','SistemaFacturacionWeb.Admin','Exclusive','Session')",Integer.class));
    }
    @Test void conservaTransaccionExistenteYLiberaEnCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Usuario",Long.class);
            int before=trancount();assertEquals(1,before);repository.bloquear();assertEquals(before,trancount());
            assertEquals("Exclusive",lockMode());
        });
        assertDisponible();
    }
    @Test void exigeTransaccionExterna() {
        assertThrows(IllegalTransactionStateException.class,repository::bloquear);
    }
    @Test void dosTransaccionesSeExcluyenHastaFinalizarLaPrimera() throws Exception {
        var firstAcquired=new CountDownLatch(1);var releaseFirst=new CountDownLatch(1);
        var secondAttempt=new CountDownLatch(1);var secondAcquired=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                repository.bloquear();firstAcquired.countDown();
                try { if(!releaseFirst.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Espera de prueba agotada."); }
                catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new IllegalStateException(ex); }
                status.setRollbackOnly();
            }));
            assertTrue(firstAcquired.await(5,TimeUnit.SECONDS));
            var second=pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                // Session consulta disponibilidad sin requerir una transacción física previa.
                assertEquals(0,jdbc.queryForObject("SELECT APPLOCK_TEST('public','SistemaFacturacionWeb.Admin','Exclusive','Session')",Integer.class));
                secondAttempt.countDown();repository.bloquear();secondAcquired.countDown();
                assertEquals("Exclusive",lockMode());status.setRollbackOnly();
            }));
            assertTrue(secondAttempt.await(5,TimeUnit.SECONDS));
            assertFalse(secondAcquired.await(200,TimeUnit.MILLISECONDS));
            releaseFirst.countDown();first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
            assertEquals(0,secondAcquired.getCount());assertDisponible();
        } finally { releaseFirst.countDown();pool.shutdownNow(); }
    }
}
