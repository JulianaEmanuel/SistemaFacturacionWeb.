package com.facturacion.sistemaweb.controller;

import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class CuentaActualAdvice {
    private final org.springframework.beans.factory.ObjectProvider<com.facturacion.sistemaweb.security.AdminAccess> admin;
    public CuentaActualAdvice(org.springframework.beans.factory.ObjectProvider<com.facturacion.sistemaweb.security.AdminAccess> admin) {this.admin=admin;}
    @ModelAttribute("cuentaActual") public String cuenta(Principal principal) { return principal==null?"":principal.getName(); }
    @ModelAttribute("administracionDisponible") public boolean administracion() {
        var acceso=admin.getIfAvailable();
        return acceso!=null && acceso.allowed(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    }
}
