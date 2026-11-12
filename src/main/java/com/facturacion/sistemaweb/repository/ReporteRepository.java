package com.facturacion.sistemaweb.repository;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.model.ReporteResultado.*;

/** Solo SELECT. Los importes de cabecera nunca se suman sobre una unión 1:N. */
@Repository @Profile("sqlserver")
public class ReporteRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ReporteRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String BASE=" FROM dbo.Factura f LEFT JOIN dbo.Persona c ON c.ID_PERSONA=f.ID_PERSONA ";
    private static final String EMPLEADO=" JOIN dbo.Empleado e ON e.ID_EMPLEADO=f.ID_EMPLEADO JOIN dbo.Persona ep ON ep.ID_PERSONA=e.ID_PERSONA_EMPLEADO ";
    private static final String WHERE=" WHERE f.FECHA_FACTURA>=:inicio AND f.FECHA_FACTURA<:fin "
        +"AND (:empleado IS NULL OR f.ID_EMPLEADO=:empleado) AND (:cliente IS NULL OR f.ID_PERSONA=:cliente) "
        +"AND (:q='' OR c.CEDULA_PERSONA LIKE :buscar OR c.NOMBRES_PERSONA LIKE :buscar OR c.APELLIDOS_PERSONA LIKE :buscar "
        +"OR CONCAT(RTRIM(c.NOMBRES_PERSONA),' ',RTRIM(c.APELLIDOS_PERSONA)) LIKE :buscar) "
        +"AND (:producto IS NULL OR EXISTS(SELECT 1 FROM dbo.Detallefactura x WHERE x.ID_FACTURA=f.ID_FACTURA AND x.ID_PRODUCTO=:producto)) ";
    private static final String IMPORTES="COUNT_BIG(*) facturas,SUM(f.SUBTOTAL_FACTURA) subtotal,SUM(f.TOTAL_FACTURA-f.SUBTOTAL_FACTURA) iva,SUM(f.TOTAL_FACTURA) total ";
    private static final String CLIENTE="COALESCE(NULLIF(CONCAT(RTRIM(c.NOMBRES_PERSONA),' ',RTRIM(c.APELLIDOS_PERSONA)),' '),'Cliente no disponible')";
    private static final String NOMBRE_EMPLEADO="CONCAT(RTRIM(ep.NOMBRES_PERSONA),' ',RTRIM(ep.APELLIDOS_PERSONA))";
    private static final RowMapper<Fila> FILA=(r,n) -> new Fila(r.getString("id").stripTrailing(),r.getString("nombre").stripTrailing(),r.getString("referencia").stripTrailing(),
        r.getLong("facturas"),r.getBigDecimal("cantidad"),r.getBigDecimal("subtotal"),r.getBigDecimal("iva"),r.getBigDecimal("total"));
    private MapSqlParameterSource parametros(ReporteFiltro.Validado f) {
        return new MapSqlParameterSource().addValue("inicio",f.inicio()).addValue("fin",f.finExclusivo())
            .addValue("empleado",f.empleado(),java.sql.Types.BIGINT).addValue("cliente",f.cliente(),java.sql.Types.BIGINT)
            .addValue("producto",f.producto(),java.sql.Types.BIGINT).addValue("q",f.q()).addValue("buscar","%"+f.q()+"%").addValue("limite",f.limite());
    }
    public Resumen resumen(ReporteTipo tipo,ReporteFiltro.Validado f) {
        String sql;
        if(tipo==ReporteTipo.PRODUCTOS) sql="SELECT COUNT_BIG(DISTINCT f.ID_FACTURA) facturas,COALESCE(SUM(d.SUBTOTAL_DETALLE),0) subtotal,"
            +"COALESCE(SUM(d.TOTAL_DETALLE-d.SUBTOTAL_DETALLE),0) iva,COALESCE(SUM(d.TOTAL_DETALLE),0) total "
            +BASE+" JOIN dbo.Detallefactura d ON d.ID_FACTURA=f.ID_FACTURA "+WHERE+"AND (:producto IS NULL OR d.ID_PRODUCTO=:producto)";
        else sql="SELECT COUNT_BIG(*) facturas,COALESCE(SUM(f.SUBTOTAL_FACTURA),0) subtotal,"
            +"COALESCE(SUM(f.TOTAL_FACTURA-f.SUBTOTAL_FACTURA),0) iva,COALESCE(SUM(f.TOTAL_FACTURA),0) total "+BASE+WHERE;
        return jdbc.queryForObject(sql,parametros(f),(r,n) -> new Resumen(r.getLong("facturas"),r.getBigDecimal("subtotal"),r.getBigDecimal("iva"),r.getBigDecimal("total")));
    }
    public List<Fila> filas(ReporteTipo tipo,ReporteFiltro.Validado f) {
        String sql;
        // Comparaciones directas: evita la clase sintetica $1 de un switch de enum.
        // Si el IDE y Maven recompilan en momentos distintos, esa clase puede faltar.
        if(tipo==ReporteTipo.VENTAS) {
                if(f.agrupacion().equals("factura")) sql="SELECT TOP (10001) CONVERT(varchar(20),f.ID_FACTURA) id,'Factura #'+CONVERT(varchar(20),f.ID_FACTURA) nombre,"
                    +"CONCAT(CONVERT(varchar(19),f.FECHA_FACTURA,120),' | ',"+CLIENTE+",' | ',"+NOMBRE_EMPLEADO+") referencia,"
                    +"CAST(1 AS bigint) facturas,CAST(NULL AS decimal(38,2)) cantidad,f.SUBTOTAL_FACTURA subtotal,f.TOTAL_FACTURA-f.SUBTOTAL_FACTURA iva,f.TOTAL_FACTURA total "
                    +BASE+EMPLEADO+WHERE+"ORDER BY f.FECHA_FACTURA,f.ID_FACTURA";
                else {
                    String periodo=f.agrupacion().equals("mes")?"CONVERT(char(7),f.FECHA_FACTURA,126)":"CONVERT(char(10),f.FECHA_FACTURA,23)";
                    sql="SELECT "+periodo+" id,"+periodo+" nombre,'' referencia,CAST(NULL AS decimal(38,2)) cantidad,"+IMPORTES+BASE+WHERE+"GROUP BY "+periodo+" ORDER BY id";
                }
            }
        else if(tipo==ReporteTipo.PRODUCTOS) sql="SELECT TOP (:limite) CONVERT(varchar(20),d.ID_PRODUCTO) id,MAX(RTRIM(p.NOMBRE_PRODUCTO)) nombre,'' referencia,"
                +"COUNT_BIG(DISTINCT f.ID_FACTURA) facturas,SUM(d.CANTIDAD) cantidad,SUM(d.SUBTOTAL_DETALLE) subtotal,SUM(d.TOTAL_DETALLE-d.SUBTOTAL_DETALLE) iva,SUM(d.TOTAL_DETALLE) total "
                +BASE+" JOIN dbo.Detallefactura d ON d.ID_FACTURA=f.ID_FACTURA JOIN dbo.Producto p ON p.ID_PRODUCTO=d.ID_PRODUCTO "+WHERE
                +"AND (:producto IS NULL OR d.ID_PRODUCTO=:producto) GROUP BY d.ID_PRODUCTO ORDER BY "+(f.orden().equals("importe")?"total":"cantidad")+" DESC,d.ID_PRODUCTO";
        else if(tipo==ReporteTipo.EMPLEADOS) sql="SELECT TOP (10001) CONVERT(varchar(20),f.ID_EMPLEADO) id,MAX("+NOMBRE_EMPLEADO+") nombre,'' referencia,CAST(NULL AS decimal(38,2)) cantidad,"
                +IMPORTES+BASE+EMPLEADO+WHERE+"GROUP BY f.ID_EMPLEADO ORDER BY total DESC,f.ID_EMPLEADO";
        else if(tipo==ReporteTipo.CLIENTES) sql="SELECT TOP (10001) CONVERT(varchar(20),f.ID_PERSONA) id,MAX("+CLIENTE+") nombre,COALESCE(MAX(RTRIM(c.CEDULA_PERSONA)),'No disponible') referencia,CAST(NULL AS decimal(38,2)) cantidad,"
                +IMPORTES+BASE+WHERE+"GROUP BY f.ID_PERSONA ORDER BY total DESC,f.ID_PERSONA";
        else if(tipo==ReporteTipo.RESUMEN) return List.of();
        else throw new IllegalArgumentException("Tipo de reporte no válido.");
        return jdbc.query(sql,parametros(f),FILA);
    }
}
