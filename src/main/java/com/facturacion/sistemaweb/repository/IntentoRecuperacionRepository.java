package com.facturacion.sistemaweb.repository;

import java.sql.*;
import java.util.Arrays;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Aplica ventanas móviles por destino e IP sin conservar correo ni IP en claro. */
@Repository @Profile("sqlserver")
public class IntentoRecuperacionRepository {
    private final JdbcTemplate jdbc;
    public IntentoRecuperacionRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional
    public boolean permitir(byte tipo,byte[] huellaDestino,byte[] huellaIp) {
        byte[] ip=huellaIp.clone(),destino=huellaDestino.clone();
        try {
            asegurarTransaccionFisica();
            bloquear(tipo,destino);
            bloquear((byte)0,ip);
            jdbc.update("DELETE TOP (1000) FROM dbo.IntentoRecuperacion WHERE CREADO_EN<DATEADD(day,-1,SYSUTCDATETIME())");
            Long destinoPrevio=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.IntentoRecuperacion "
                +"WHERE TIPO=? AND HUELLA=? AND CREADO_EN>=DATEADD(hour,-1,SYSUTCDATETIME())",Long.class,tipo,destino);
            Long ipPrevio=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.IntentoRecuperacion "
                +"WHERE TIPO=0 AND HUELLA=? AND CREADO_EN>=DATEADD(minute,-15,SYSUTCDATETIME())",Long.class,ip);
            jdbc.update("INSERT dbo.IntentoRecuperacion(TIPO,HUELLA,CREADO_EN) VALUES(?,?,SYSUTCDATETIME()),(0,?,SYSUTCDATETIME())",
                tipo,destino,ip);
            return destinoPrevio!=null && destinoPrevio<3 && ipPrevio!=null && ipPrevio<10;
        } finally { Arrays.fill(ip,(byte)0);Arrays.fill(destino,(byte)0); }
    }

    private void asegurarTransaccionFisica() {
        Integer abierta=jdbc.execute((ConnectionCallback<Integer>) connection -> {
            if(connection.getAutoCommit())
                throw new InvalidDataAccessApiUsageException("El límite de recuperación requiere una conexión transaccional.");
            try(PreparedStatement statement=connection.prepareStatement(
                    "IF @@TRANCOUNT = 0 BEGIN DECLARE @ignored int; SELECT TOP (1) @ignored=object_id FROM sys.objects; END; SELECT @@TRANCOUNT");
                ResultSet result=statement.executeQuery()) {
                return result.next()?result.getInt(1):0;
            }
        });
        if(abierta==null || abierta<1)
            throw new DataAccessResourceFailureException("SQL Server no inició la transacción de límite de solicitudes.");
    }

    private void bloquear(byte tipo,byte[] huella) {
        Integer resultado=jdbc.execute((ConnectionCallback<Integer>) connection -> {
            try(CallableStatement call=connection.prepareCall("{? = call sys.sp_getapplock(?, ?, ?, ?)}")) {
                call.registerOutParameter(1,Types.INTEGER);
                call.setString(2,"SistemaFacturacionWeb.Recovery.rate."+tipo+"."+hex(huella));
                call.setString(3,"Exclusive");call.setString(4,"Transaction");call.setInt(5,10000);
                call.execute();int code=call.getInt(1);return call.wasNull()?null:code;
            }
        });
        if(resultado==null || resultado<0)
            throw new DataAccessResourceFailureException("No se pudo adquirir el bloqueo del límite de solicitudes.");
    }

    private static String hex(byte[] bytes) {
        var result=new StringBuilder(bytes.length*2);
        for(byte b:bytes) result.append(String.format("%02x",b));
        return result.toString();
    }
}
