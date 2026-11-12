package com.facturacion.sistemaweb.security;

import java.util.Collection;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;

/** Detecta de forma compatible la migración de versionado y revoca sesiones/tokenes. */
@Repository @Profile("sqlserver")
public class CredencialVersionRepository {
    private final JdbcTemplate jdbc;
    private volatile Boolean disponible;
    private volatile Boolean tablaRecuperacionDisponible;

    public CredencialVersionRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public boolean disponible() {
        Boolean cache=disponible;
        if(cache==null) synchronized(this) {
            cache=disponible;
            if(cache==null) disponible=cache=Integer.valueOf(1).equals(jdbc.queryForObject(
                "SELECT CASE WHEN COL_LENGTH(N'dbo.Usuario',N'VERSION_CREDENCIAL') IS NULL THEN 0 ELSE 1 END",Integer.class));
        }
        return cache;
    }

    public long version(long id) {
        if(!disponible()) return 0L;
        Long valor=jdbc.queryForObject("SELECT VERSION_CREDENCIAL FROM dbo.Usuario WHERE ID_USUARIO=?",Long.class,id);
        return valor==null?0L:valor;
    }

    public void bloquearCuenta(long id) {
        Integer resultado=jdbc.execute((ConnectionCallback<Integer>) connection -> {
            if(connection.getAutoCommit())
                throw new InvalidDataAccessApiUsageException("El bloqueo de credenciales requiere una conexión transaccional.");
            try(PreparedStatement state=connection.prepareStatement(
                    "IF @@TRANCOUNT = 0 BEGIN DECLARE @ignored int; SELECT TOP (1) @ignored=object_id FROM sys.objects; END; SELECT @@TRANCOUNT");
                ResultSet result=state.executeQuery()) {
                if(!result.next() || result.getInt(1)<1)
                    throw new InvalidDataAccessApiUsageException("SQL Server no inició la transacción requerida para bloquear credenciales.");
            }
            try(CallableStatement call=connection.prepareCall("{? = call sys.sp_getapplock(?, ?, ?, ?)}")) {
                call.registerOutParameter(1,Types.INTEGER);
                call.setString(2,"SistemaFacturacionWeb.Credencial."+id);
                call.setString(3,"Exclusive");call.setString(4,"Transaction");call.setInt(5,10000);
                call.execute();int code=call.getInt(1);return call.wasNull()?null:code;
            }
        });
        if(resultado==null || resultado<0)
            throw new DataAccessResourceFailureException("No se pudo adquirir el bloqueo de credenciales de la cuenta.");
    }

    public boolean sesionVigente(long id,Collection<String> autoridades) {
        if(!disponible()) return true;
        String prefijo="CREDENTIAL_VERSION_";
        String version=autoridades.stream().filter(a->a.startsWith(prefijo)).map(a->a.substring(prefijo.length()))
            .findFirst().orElse(null);
        return version!=null && version.equals(Long.toString(version(id)));
    }

    public void incrementarYRevocar(long id) {
        if(disponible() && jdbc.update("UPDATE dbo.Usuario SET VERSION_CREDENCIAL=ISNULL(VERSION_CREDENCIAL,0)+1 WHERE ID_USUARIO=?",id)!=1)
            throw new DataAccessResourceFailureException("No se pudo incrementar la versión de credencial de la cuenta.");
        revocarRecuperaciones(id);
    }

    public void revocarRecuperaciones(long id) {
        if(tablaRecuperacionDisponible()) jdbc.update("UPDATE dbo.RecuperacionContrasenia SET REVOCADO_EN=SYSUTCDATETIME() "
            +"WHERE ID_USUARIO=? AND USADO_EN IS NULL AND REVOCADO_EN IS NULL",id);
    }

    private boolean tablaRecuperacionDisponible() {
        Boolean cache=tablaRecuperacionDisponible;
        if(cache==null) synchronized(this) {
            cache=tablaRecuperacionDisponible;
            if(cache==null) tablaRecuperacionDisponible=cache=Integer.valueOf(1).equals(jdbc.queryForObject(
                "SELECT CASE WHEN OBJECT_ID(N'dbo.RecuperacionContrasenia',N'U') IS NULL THEN 0 ELSE 1 END",Integer.class));
        }
        return cache;
    }
}
