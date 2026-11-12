package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import com.facturacion.sistemaweb.service.*;

class FacturaEnviosTest {
    @Test void reenvioDevuelveLaFacturaRegistradaSinRepetirOperacion() {
        var tokens=new FacturaEnvios();var session=new MockHttpSession();String token=tokens.nuevo(session,7L);
        var count=new AtomicInteger();
        assertEquals(19L,tokens.ejecutar(session,7L,token,()->{count.incrementAndGet();return 19L;}));
        assertEquals(19L,tokens.ejecutar(session,7L,token,()->{fail("No debe guardar otra factura");return 20L;}));assertEquals(1,count.get());
    }
    @Test void rechazoNoConsumeElToken() {
        var tokens=new FacturaEnvios();var session=new MockHttpSession();String token=tokens.nuevo(session,7L);
        assertThrows(FacturaException.class,()->tokens.ejecutar(session,7L,token,()->{throw new FacturaException("Rollback simulado");}));
        assertEquals(19L,tokens.ejecutar(session,7L,token,()->19L));
    }
    @Test void tokenVinculadoASesionCuentaYExistencia() {
        var tokens=new FacturaEnvios();var session=new MockHttpSession();String token=tokens.nuevo(session,7L);
        assertThrows(FacturaException.class,()->tokens.ejecutar(session,8L,token,()->19L));
        assertThrows(FacturaException.class,()->tokens.ejecutar(new MockHttpSession(),7L,token,()->19L));
        assertThrows(FacturaException.class,()->tokens.ejecutar(session,7L,"forjado",()->19L));
    }
    @Test void caducidadYLimiteDeFormularios() {
        var session=new MockHttpSession();var initial=Instant.parse("2026-10-30T12:00:00Z");
        var tokens=new FacturaEnvios(Clock.fixed(initial,ZoneOffset.UTC));String token=tokens.nuevo(session,7L);
        var after=new FacturaEnvios(Clock.fixed(initial.plusSeconds(1800),ZoneOffset.UTC));
        assertThrows(FacturaException.class,()->after.ejecutar(session,7L,token,()->19L));
        for(int i=0;i<20;i++) tokens.nuevo(session,7L);
        assertThrows(FacturaException.class,()->tokens.ejecutar(session,7L,token,()->19L));
    }
    @Test void dobleEnvioConcurrenteEsperaAlCommitYGuardaUnaSolaVez() throws Exception {
        var tokens=new FacturaEnvios();var session=new MockHttpSession();String token=tokens.nuevo(session,7L);
        var start=new CountDownLatch(1);var release=new CountDownLatch(1);var count=new AtomicInteger();var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->tokens.ejecutar(session,7L,token,()->{
                count.incrementAndGet();start.countDown();
                try { if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException(); }
                catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new IllegalStateException(ex); }return 19L;
            }));
            assertTrue(start.await(5,TimeUnit.SECONDS));
            var second=pool.submit(()->tokens.ejecutar(session,7L,token,()->{count.incrementAndGet();return 20L;}));
            assertThrows(TimeoutException.class,()->second.get(150,TimeUnit.MILLISECONDS));
            release.countDown();assertEquals(19L,first.get(5,TimeUnit.SECONDS));assertEquals(19L,second.get(5,TimeUnit.SECONDS));assertEquals(1,count.get());
        } finally { release.countDown();pool.shutdownNow(); }
    }
}
