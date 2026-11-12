package com.facturacion.sistemaweb.service;

import java.io.Serializable;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/** Idempotencia acotada a la sesión y a una instancia. Nunca sustituye a CSRF. */
@Component
public class FacturaEnvios {
    private static final String KEY=FacturaEnvios.class.getName();
    private final Clock clock;
    public FacturaEnvios() { this(Clock.systemUTC()); }
    public FacturaEnvios(Clock clock) { this.clock=clock; }
    private static class Envio implements Serializable {
        private static final long serialVersionUID=1L;
        final Long usuario;final Instant creado;Long factura;
        Envio(Long usuario,Instant creado) { this.usuario=usuario;this.creado=creado; }
    }
    @SuppressWarnings("unchecked")
    private synchronized Map<String,Envio> mapa(HttpSession session) {
        var value=session.getAttribute(KEY);
        if(value==null) { value=new LinkedHashMap<String,Envio>();session.setAttribute(KEY,value); }
        return (Map<String,Envio>)value;
    }
    public String nuevo(HttpSession session,Long usuario) {
        var mapa=mapa(session);synchronized(mapa) {
            mapa.entrySet().removeIf(e -> expirado(e.getValue()));
            while(mapa.size()>=20) mapa.remove(mapa.keySet().iterator().next());
            String token=UUID.randomUUID().toString();mapa.put(token,new Envio(usuario,clock.instant()));return token;
        }
    }
    private boolean expirado(Envio e) { return !clock.instant().isBefore(e.creado.plus(Duration.ofMinutes(30))); }
    public Long ejecutar(HttpSession session,Long usuario,String token,Supplier<Long> guardar) {
        var mapa=mapa(session);Envio envio;synchronized(mapa) { envio=mapa.get(token); }
        if(envio==null || !Objects.equals(envio.usuario,usuario)) throw new FacturaException("El formulario no es válido. Abre una nueva factura.");
        synchronized(envio) {
            if(expirado(envio)) throw new FacturaException("El formulario ha caducado. Abre una nueva factura.");
            if(envio.factura!=null) return envio.factura;
            // guardar llama a un servicio proxificado: regresa después del COMMIT.
            Long id=guardar.get();
            if(id==null) throw new IllegalStateException("La operación no devolvió una factura.");
            envio.factura=id;return id;
        }
    }
}
