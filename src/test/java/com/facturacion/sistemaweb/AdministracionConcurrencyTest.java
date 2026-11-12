package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import com.facturacion.sistemaweb.model.UsuarioResumen;
import com.facturacion.sistemaweb.repository.*;
import com.facturacion.sistemaweb.security.*;
import com.facturacion.sistemaweb.service.*;

class AdministracionConcurrencyTest {
    @Test void dosDesactivacionesConcurrentesConservanUnAdministrador() throws Exception {
        var repository=mock(AdministracionRepository.class);var access=mock(AdminAccess.class);
        when(access.reservado()).thenReturn("ADMINISTRADOR");when(repository.tipoReservado(3,"ADMINISTRADOR")).thenReturn(true);
        AtomicBoolean primero=new AtomicBoolean(true),segundo=new AtomicBoolean(true);
        when(repository.buscarId(anyLong())).thenAnswer(i -> Optional.of(new UsuarioResumen(i.getArgument(0),3,"cuenta","ADMINISTRADOR",((Long)i.getArgument(0))==1?primero.get():segundo.get())));
        when(repository.administradoresActivos("ADMINISTRADOR")).thenAnswer(i -> (primero.get()?1L:0L)+(segundo.get()?1L:0L));
        ReentrantLock lock=new ReentrantLock();
        doAnswer(i -> {
            lock.lock();TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { lock.unlock(); }
            });return null;
        }).when(repository).bloquear();
        doAnswer(i -> { if(((Long)i.getArgument(0))==1) primero.set(false);else segundo.set(false);return null; }).when(repository).estado(anyLong(),eq(false));
        var target=new AdministracionService(repository,mock(TipoUsuarioRepository.class),access,new ClaveSegura(),Validation.buildDefaultValidatorFactory().getValidator());
        var tx=new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction() { return new Object(); }
            protected void doBegin(Object transaction,TransactionDefinition definition) { }
            protected void doCommit(DefaultTransactionStatus status) { }
            protected void doRollback(DefaultTransactionStatus status) { }
        };
        var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));
        var service=(AdministracionService)factory.getProxy();
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            java.util.function.Function<Long,Boolean> operation=id -> {
                try { start.await();service.estado(id,false);return true; }
                catch(AdministracionException ex) { return false; }
                catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new RuntimeException(ex); }
            };
            var a=pool.submit(()->operation.apply(1L));var b=pool.submit(()->operation.apply(2L));start.countDown();
            boolean ra=a.get(10,TimeUnit.SECONDS),rb=b.get(10,TimeUnit.SECONDS);
            assertNotEquals(ra,rb);assertEquals(1,(primero.get()?1:0)+(segundo.get()?1:0));
            verify(repository,times(2)).bloquear();verify(repository,times(1)).estado(anyLong(),eq(false));
        } finally { pool.shutdownNow(); }
    }
}
