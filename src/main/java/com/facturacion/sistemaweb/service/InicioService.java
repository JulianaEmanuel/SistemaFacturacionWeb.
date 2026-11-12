package com.facturacion.sistemaweb.service;

import java.math.*;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.facturacion.sistemaweb.repository.InicioRepository;

@Service @Profile("sqlserver") @PreAuthorize("isAuthenticated()")
@Transactional(readOnly=true,isolation=Isolation.SERIALIZABLE,timeout=15)
public class InicioService {
    public record Actividad(long id,LocalDateTime fecha,String cliente,BigDecimal total) {
        public String totalVisible() {return total.setScale(2,RoundingMode.HALF_UP).toPlainString();}
    }
    public record Totales(long facturas,BigDecimal ventas) { }
    public record Panel(long clientes,long productos,Long facturas,BigDecimal ventas,List<Actividad> actividad,String alcance,String aviso) {
        public Panel {actividad=List.copyOf(actividad);}
        public String ventasVisible() {return ventas==null?"—":ventas.setScale(2,RoundingMode.HALF_UP).toPlainString();}
    }
    private final InicioRepository repo;
    private final EmpleadoSesionService sesion;
    public InicioService(InicioRepository repo,EmpleadoSesionService sesion) {this.repo=repo;this.sesion=sesion;}
    public Panel consultar() {
        var cuenta=sesion.actual(false);Long empleado=null;
        if(!cuenta.administrador()) {
            try {empleado=sesion.empleado(cuenta.id(),false).getIdEmpleado();}
            catch(FacturaException ex) {return new Panel(repo.clientes(),repo.productos(),null,null,List.of(),"Catálogos del sistema",ex.getMessage());}
            if(empleado==null)throw new org.springframework.security.access.AccessDeniedException("No se pudo determinar tu empleado.");
        }
        var total=repo.totales(empleado);
        return new Panel(repo.clientes(),repo.productos(),total.facturas(),total.ventas(),repo.recientes(empleado),
            cuenta.administrador()?"Todas las facturas registradas":"Tus facturas y ventas",null);
    }
}
