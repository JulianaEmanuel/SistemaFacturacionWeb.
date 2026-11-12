package com.facturacion.sistemaweb.repository;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import com.facturacion.sistemaweb.service.InicioService.*;

/** Indicadores de lectura; Persona es el directorio que utiliza Clientes. */
@Repository @Profile("sqlserver")
public class InicioRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public InicioRepository(NamedParameterJdbcTemplate jdbc) {this.jdbc=jdbc;}
    public long clientes() {return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Persona",new MapSqlParameterSource(),Long.class);}
    public long productos() {return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Producto",new MapSqlParameterSource(),Long.class);}
    private MapSqlParameterSource parametro(Long id) {return new MapSqlParameterSource().addValue("empleado",id,java.sql.Types.BIGINT);}
    public Totales totales(Long empleado) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*) facturas,COALESCE(SUM(TOTAL_FACTURA),0) ventas FROM dbo.Factura WHERE (:empleado IS NULL OR ID_EMPLEADO=:empleado)",
            parametro(empleado),(r,n)->new Totales(r.getLong("facturas"),r.getBigDecimal("ventas")));
    }
    public List<Actividad> recientes(Long empleado) {
        return jdbc.query("SELECT TOP (5) f.ID_FACTURA,f.FECHA_FACTURA,f.TOTAL_FACTURA,"
            +"COALESCE(NULLIF(CONCAT(RTRIM(p.NOMBRES_PERSONA),' ',RTRIM(p.APELLIDOS_PERSONA)),' '),'Cliente no disponible') cliente "
            +"FROM dbo.Factura f LEFT JOIN dbo.Persona p ON p.ID_PERSONA=f.ID_PERSONA "
            +"WHERE (:empleado IS NULL OR f.ID_EMPLEADO=:empleado) ORDER BY f.FECHA_FACTURA DESC,f.ID_FACTURA DESC",
            parametro(empleado),(r,n)->new Actividad(r.getLong("ID_FACTURA"),r.getTimestamp("FECHA_FACTURA").toLocalDateTime(),r.getString("cliente").stripTrailing(),r.getBigDecimal("TOTAL_FACTURA")));
    }
}
