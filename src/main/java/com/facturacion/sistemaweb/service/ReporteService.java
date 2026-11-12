package com.facturacion.sistemaweb.service;

import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.model.ReporteResultado.*;
import com.facturacion.sistemaweb.repository.ReporteRepository;

@Service @Profile("sqlserver") @PreAuthorize("isAuthenticated()")
@Transactional(readOnly=true,isolation=Isolation.SERIALIZABLE,timeout=30)
public class ReporteService {
    public static final int MAX_FILAS=10000;
    private final ReporteRepository repository;
    private final EmpleadoSesionService sesion;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public ReporteService(ReporteRepository repository,EmpleadoSesionService sesion) { this(repository,sesion,Clock.system(ZoneId.of("America/Guayaquil"))); }
    public ReporteService(ReporteRepository repository,EmpleadoSesionService sesion,Clock clock) { this.repository=repository;this.sesion=sesion;this.clock=clock; }
    public ReporteResultado consultar(ReporteTipo tipo,ReporteFiltro input) {
        if(tipo==null || input==null) throw new ReporteException("Selecciona un reporte y filtros válidos.");
        var cuenta=sesion.actual(false);
        var filtro=validar(input);
        Long empleado=filtro.empleado();
        String alcance="Todas las ventas autorizadas";
        if(!cuenta.administrador()) {
            Long propio;
            try { propio=sesion.empleado(cuenta.id(),false).getIdEmpleado(); }
            catch(FacturaException ex) { throw new ReporteException(ex.getMessage()); }
            if(propio==null) throw new AccessDeniedException("La asociación del empleado no es válida.");
            if(empleado!=null && !empleado.equals(propio)) throw new AccessDeniedException("Solo puedes consultar tus propias ventas.");
            empleado=propio;alcance="Ventas propias · empleado "+propio;
        } else if(empleado!=null) alcance="Ventas del empleado "+empleado;
        // El alcance se fija en el servicio; ningún parámetro puede volverlo global.
        filtro=new ReporteFiltro.Validado(filtro.desde(),filtro.hasta(),filtro.cliente(),filtro.producto(),empleado,filtro.agrupacion(),filtro.orden(),filtro.q(),filtro.limite());
        Resumen resumen=repository.resumen(tipo,filtro);
        List<Fila> filas=repository.filas(tipo,filtro);
        exigirLimite(filas.size());
        if(tipo==ReporteTipo.VENTAS && !filtro.agrupacion().equals("factura")) filas=completarPeriodos(filas,filtro);
        if(tipo==ReporteTipo.RESUMEN) filas=List.of(new Fila("resumen","Período consultado",filtro.desde()+" a "+filtro.hasta(),resumen.facturas(),null,resumen.subtotal(),resumen.iva(),resumen.total()));
        List<Fila> paraGrafico=filas;
        if(tipo==ReporteTipo.CLIENTES || tipo==ReporteTipo.EMPLEADOS || (tipo==ReporteTipo.VENTAS && filtro.agrupacion().equals("factura")))
            paraGrafico=filas.stream().sorted(Comparator.comparing(Fila::total).reversed().thenComparing(Fila::id)).limit(20).toList();
        List<Punto> grafico=tipo==ReporteTipo.RESUMEN?List.of():paraGrafico.stream().map(f -> new Punto(f.nombre()+" ["+f.id()+"]",f.total())).toList();
        String clase=tipo==ReporteTipo.VENTAS && filtro.agrupacion().equals("dia")?"line":"bar";
        return new ReporteResultado(tipo,filtro,alcance,cuenta.administrador(),resumen,filas,grafico,clase,tipo==ReporteTipo.PRODUCTOS);
    }
    public ReportePanel consultarPanel(ReporteFiltro input) {
        // Resuelve identidad, permisos y filtros en el servicio antes de cualquier serie.
        // Todos los SELECT del panel comparten esta transaccion de solo lectura.
        var reporte=consultar(ReporteTipo.RESUMEN,input);
        if(reporte.sinVentas()) return new ReportePanel(reporte,List.of(),List.of(),List.of(),0);
        var f=reporte.filtro();
        var periodos=new ReporteFiltro.Validado(f.desde(),f.hasta(),f.cliente(),f.producto(),f.empleado(),
            f.agrupacion().equals("mes")?"mes":"dia","cantidad",f.q(),10);
        var ranking=new ReporteFiltro.Validado(f.desde(),f.hasta(),f.cliente(),f.producto(),f.empleado(),
            "dia","cantidad",f.q(),10);
        var ventas=repository.filas(ReporteTipo.VENTAS,periodos);
        var clientes=repository.filas(ReporteTipo.CLIENTES,ranking);
        var productos=repository.filas(ReporteTipo.PRODUCTOS,ranking);
        exigirLimite(ventas.size());exigirLimite(clientes.size());exigirLimite(productos.size());
        return new ReportePanel(reporte,completarPeriodos(ventas,periodos),clientes.stream()
            .sorted(Comparator.comparing(Fila::total).reversed().thenComparing(Fila::id)).limit(10).toList(),
            productos,clientes.size());
    }

    private ReporteFiltro.Validado validar(ReporteFiltro f) {
        LocalDate hoy=LocalDate.now(clock),desde=f.getDesde()==null?hoy.withDayOfMonth(1):f.getDesde(),hasta=f.getHasta()==null?hoy:f.getHasta();
        LocalDate min=LocalDate.of(1753,1,1),max=LocalDate.of(9999,12,30);
        if(desde.isBefore(min) || hasta.isAfter(max) || desde.isAfter(hasta)) throw new ReporteException("Revisa el rango de fechas.");
        if(!hasta.isBefore(desde.plusYears(1))) throw new ReporteException("El rango máximo es un año; reduce las fechas consultadas.");
        for(Long id:new Long[]{f.getCliente(),f.getProducto(),f.getEmpleado()}) if(id!=null && id<1) throw new ReporteException("Los identificadores de filtro deben ser positivos.");
        if(!Set.of("dia","mes","factura").contains(f.getAgrupacion()==null?"":f.getAgrupacion())) throw new ReporteException("Agrupación no válida.");
        if(!Set.of("cantidad","importe").contains(f.getOrden()==null?"":f.getOrden())) throw new ReporteException("Orden de productos no válido.");
        if(f.getLimite()==null || f.getLimite()<1 || f.getLimite()>100) throw new ReporteException("El ranking admite entre 1 y 100 productos.");
        String q=f.getQ()==null?"":f.getQ().strip();
        if(q.length()>100) throw new ReporteException("La búsqueda admite hasta 100 caracteres.");
        return new ReporteFiltro.Validado(desde,hasta,f.getCliente(),f.getProducto(),f.getEmpleado(),f.getAgrupacion(),f.getOrden(),q,f.getLimite());
    }
    private List<Fila> completarPeriodos(List<Fila> filas,ReporteFiltro.Validado filtro) {
        Map<String,Fila> existentes=new HashMap<>();filas.forEach(f -> existentes.put(f.id(),f));
        List<Fila> result=new ArrayList<>();
        if(filtro.agrupacion().equals("mes")) {
            for(YearMonth mes=YearMonth.from(filtro.desde());!mes.isAfter(YearMonth.from(filtro.hasta()));mes=mes.plusMonths(1))
                result.add(existentes.getOrDefault(mes.toString(),Fila.cero(mes.toString())));
        } else for(LocalDate dia=filtro.desde();!dia.isAfter(filtro.hasta());dia=dia.plusDays(1))
            result.add(existentes.getOrDefault(dia.toString(),Fila.cero(dia.toString())));
        return result;
    }
    public static void exigirLimite(int filas) {
        if(filas>MAX_FILAS) throw new ReporteException("El reporte supera 10.000 filas. Reduce los filtros antes de consultar o exportar.");
    }
}
