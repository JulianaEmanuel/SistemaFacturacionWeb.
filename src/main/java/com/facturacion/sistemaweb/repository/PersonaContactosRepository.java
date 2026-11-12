package com.facturacion.sistemaweb.repository;

import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.service.FacturacionSchema;

/** Mapeo explícito de las columnas adicionales para poder leer también el esquema anterior. */
@Repository @Profile("sqlserver")
public class PersonaContactosRepository {
    private final NamedParameterJdbcTemplate jdbc;private final FacturacionSchema schema;
    public PersonaContactosRepository(NamedParameterJdbcTemplate jdbc,FacturacionSchema schema){this.jdbc=jdbc;this.schema=schema;}
    public boolean disponible(){return schema.contactos();}
    public void exigirDisponible(){if(!disponible())throw new IllegalStateException("Falta la migración de contactos de clientes. No se guardaron datos.");}
    public List<Persona> cargar(List<Persona> personas){
        if(!disponible() || personas.isEmpty())return personas;
        var mapa=new HashMap<Long,Persona>();personas.forEach(p->mapa.put(p.getIdPersona(),p));
        var ids=new ArrayList<>(mapa.keySet());
        for(int inicio=0;inicio<ids.size();inicio+=1000){
            var parte=ids.subList(inicio,Math.min(ids.size(),inicio+1000));
            jdbc.query("SELECT ID_PERSONA,DIRECCION_PERSONA,TELEFONO_PERSONA,CORREO_PERSONA FROM dbo.Persona WHERE ID_PERSONA IN (:ids)",Map.of("ids",parte),(org.springframework.jdbc.core.RowCallbackHandler) r->{
                var p=mapa.get(r.getLong(1));p.setDireccionPersona(r.getString(2));p.setTelefonoPersona(r.getString(3));p.setCorreoPersona(r.getString(4));
            });
        }
        return personas;
    }
    public Persona cargar(Persona persona){cargar(List.of(persona));return persona;}
    public void guardar(Long id,PersonaForm form){
        exigirDisponible();
        var p=new MapSqlParameterSource().addValue("id",id).addValue("direccion",limpiar(form.getDireccionPersona())).addValue("telefono",limpiar(form.getTelefonoPersona())).addValue("correo",limpiar(form.getCorreoPersona()));
        if(jdbc.update("UPDATE dbo.Persona SET DIRECCION_PERSONA=:direccion,TELEFONO_PERSONA=:telefono,CORREO_PERSONA=:correo WHERE ID_PERSONA=:id",p)!=1)throw new IllegalStateException("No se pudo guardar el contacto del cliente.");
    }
    private String limpiar(String v){return v==null||v.isBlank()?null:v.strip();}
}
