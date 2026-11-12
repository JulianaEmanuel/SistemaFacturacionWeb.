package com.facturacion.sistemaweb.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.facturacion.sistemaweb.repository.AdministracionRepository;

@Component("adminAccess") @Profile("sqlserver")
public class AdminAccess {
    private final AdministracionRepository repository;
    private final String reservado;
    public AdminAccess(AdministracionRepository repository,@Value("${app.security.admin-type:ADMINISTRADOR}") String reservado) {
        if (reservado.isBlank() || reservado.length()>50) throw new IllegalArgumentException("El tipo administrativo configurado no es válido.");
        this.repository=repository; this.reservado=reservado.strip();
    }
    public String reservado() { return reservado; }
    public boolean allowed(Authentication auth) {
        if(auth == null || !auth.isAuthenticated()
            || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) return false;
        var actual=repository.buscarNombre(auth.getName());
        return actual.isPresent() && actual.get().activo() && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ACCOUNT_ID_"+actual.get().id())) && repository.esAdmin(auth.getName(),reservado);
    }
    public void exigir() {
        if (!allowed(SecurityContextHolder.getContext().getAuthentication())) throw new AccessDeniedException("Sin permisos administrativos.");
    }
}
