package com.facturacion.sistemaweb.service;

import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import com.facturacion.sistemaweb.model.FacturaForm;

@Service @Profile("sqlserver") @PreAuthorize("isAuthenticated()")
public class FacturaRegistroService {
    /** {@code nueva} es falso cuando el token ya había registrado la factura (doble envío). */
    public record Registro(Long id,boolean nueva) { }
    private final FacturaService facturas;
    private final FacturaEnvios envios;
    public FacturaRegistroService(FacturaService facturas,FacturaEnvios envios) { this.facturas=facturas;this.envios=envios; }
    public Registro registrar(HttpSession session,FacturaForm form) {
        var nueva=new AtomicBoolean(false);
        Long id=envios.ejecutar(session,facturas.usuarioId(),form.getToken(),() -> {
            Long registrada=facturas.registrar(form);nueva.set(true);return registrada;
        });
        return new Registro(id,nueva.get());
    }
}
