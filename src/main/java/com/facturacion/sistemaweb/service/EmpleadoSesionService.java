package com.facturacion.sistemaweb.service;

import java.util.*;
import jakarta.persistence.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.security.AdminAccess;

@Service @Profile("sqlserver")
public class EmpleadoSesionService {
    private final EntityManager em;
    private final AdminAccess admin;
    public record Cuenta(Long id,boolean administrador) { }
    public EmpleadoSesionService(EntityManager em,AdminAccess admin) { this.em=em;this.admin=admin; }
    public Cuenta actual(boolean bloquear) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) throw new AccessDeniedException("Inicia sesión para continuar.");
        var ids=auth.getAuthorities().stream().map(a -> a.getAuthority()).filter(a -> a.startsWith("ACCOUNT_ID_")).toList();
        Long id;
        try { if(ids.size()!=1) throw new IllegalArgumentException();id=Long.valueOf(ids.getFirst().substring(11)); }
        catch(IllegalArgumentException ex) { throw new AccessDeniedException("La sesión no identifica una cuenta válida."); }
        Usuario usuario=bloquear?em.find(Usuario.class,id,LockModeType.PESSIMISTIC_READ):em.find(Usuario.class,id);
        if(usuario==null || !usuario.isActivo() || !usuario.getNombreUsuario().stripTrailing().equals(auth.getName()))
            throw new AccessDeniedException("La cuenta ya no está disponible. Inicia sesión nuevamente.");
        return new Cuenta(id,admin.allowed(auth));
    }
    public Empleado empleado(Long usuario,boolean bloquear) {
        var query=em.createQuery("SELECT e FROM Empleado e JOIN FETCH e.persona WHERE e.usuario.idUsuario=:usuario",Empleado.class).setParameter("usuario",usuario);
        if(bloquear) query.setLockMode(LockModeType.PESSIMISTIC_READ);
        var empleados=query.getResultList();
        if(empleados.isEmpty()) throw new FacturaException("Tu cuenta no tiene un empleado asociado: cada factura debe registrar qué empleado la emite, por eso no puedes registrar facturas. Un administrador debe asociar tu usuario a una persona en Empleados.");
        if(empleados.size()!=1) throw new FacturaException("La cuenta tiene varios empleados asociados. Un administrador debe corregir la asociación.");
        return empleados.getFirst();
    }
}
