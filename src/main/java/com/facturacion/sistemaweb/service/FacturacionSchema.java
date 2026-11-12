package com.facturacion.sistemaweb.service;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Detecta migraciones manuales; nunca crea tablas ni completa registros. */
@Service @Profile("sqlserver")
public class FacturacionSchema {
    private final JdbcTemplate jdbc;
    private volatile Boolean contactos,disponible;
    public FacturacionSchema(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public boolean contactos(){
        if(contactos==null) contactos=jdbc.queryForObject("SELECT CASE WHEN COL_LENGTH('dbo.Persona','DIRECCION_PERSONA') IS NOT NULL AND COL_LENGTH('dbo.Persona','TELEFONO_PERSONA') IS NOT NULL AND COL_LENGTH('dbo.Persona','CORREO_PERSONA') IS NOT NULL THEN 1 ELSE 0 END",Integer.class)==1;
        return contactos;
    }
    public boolean disponible(){
        if(disponible==null) disponible=contactos() && jdbc.queryForObject("SELECT CASE WHEN OBJECT_ID('dbo.FormaPago','U') IS NOT NULL AND OBJECT_ID('dbo.FacturaPago','U') IS NOT NULL AND OBJECT_ID('dbo.FacturaCliente','U') IS NOT NULL AND COL_LENGTH('dbo.FacturaPago','REFERENCIA') IS NOT NULL AND COL_LENGTH('dbo.FacturaPago','CUOTAS') IS NOT NULL THEN 1 ELSE 0 END",Integer.class)==1;
        return disponible;
    }
    public void exigirDisponible(){if(!disponible())throw new FacturaException("Faltan las migraciones de contactos y pagos (001 a 005). No se guardó la factura. Solicita su aplicación autorizada y reinicia la aplicación.");}
}
