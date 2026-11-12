package com.facturacion.sistemaweb.repository;

import java.util.*;
import java.sql.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.facturacion.sistemaweb.model.UsuarioResumen;
import com.facturacion.sistemaweb.security.CredencialVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;

/** Las lecturas seleccionan columnas explícitas y nunca recuperan CONTRASENIA. */
@Repository @Profile("sqlserver")
public class AdministracionRepository {
    private final JdbcTemplate jdbc;
    private final CredencialVersionRepository versiones;
    private static final String SELECT = "SELECT u.ID_USUARIO,u.ID_TIPO_USUARIO,u.NOMBRE_USUARIO,t.TIPO_USUARIO,u.ACTIVO FROM dbo.Usuario u JOIN dbo.TipoUsuario t ON t.ID_TIPO_USUARIO=u.ID_TIPO_USUARIO ";
    private static final RowMapper<UsuarioResumen> MAP = (r,n) -> new UsuarioResumen(r.getLong(1),r.getInt(2),r.getString(3),r.getString(4),r.getBoolean(5));
    public AdministracionRepository(JdbcTemplate jdbc) { this(jdbc,null); }
    @Autowired public AdministracionRepository(JdbcTemplate jdbc,CredencialVersionRepository versiones) {
        this.jdbc=jdbc;this.versiones=versiones;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void bloquear() {
        Integer result = jdbc.execute((ConnectionCallback<Integer>) connection -> {
            if(connection.getAutoCommit())
                throw new InvalidDataAccessApiUsageException("El bloqueo administrativo requiere una conexión transaccional.");
            // El driver SQL Server usa IMPLICIT_TRANSACTIONS: autoCommit=false no
            // implica @@TRANCOUNT>0. Una lectura de catálogo inicia la transacción
            // física sin escribir datos ni agregar BEGIN/COMMIT anidados.
            try (PreparedStatement statement=connection.prepareStatement(
                    "IF @@TRANCOUNT = 0 BEGIN DECLARE @ignored int; "
                    + "SELECT TOP (1) @ignored=object_id FROM sys.objects; END; SELECT @@TRANCOUNT" );
                    ResultSet state=statement.executeQuery()) {
                if(!state.next() || state.getInt(1)<1)
                    throw new InvalidDataAccessResourceUsageException("SQL Server no inició la transacción requerida por el bloqueo administrativo.");
            }
            // Leer el RETURN del procedimiento explícitamente, sin depender de
            // los posibles resultados intermedios de un batch EXEC + SELECT.
            try (CallableStatement statement=connection.prepareCall("{? = call sys.sp_getapplock(?, ?, ?, ?)}")) {
                statement.registerOutParameter(1,Types.INTEGER);
                statement.setString(2,"SistemaFacturacionWeb.Admin");
                statement.setString(3,"Exclusive");
                statement.setString(4,"Transaction");
                statement.setInt(5,10000);
                statement.execute();
                int code=statement.getInt(1);
                return statement.wasNull()?null:code;
            }
        });
        if(result==null) throw new DataAccessResourceFailureException("sp_getapplock no devolvió un código de resultado.");
        switch(result) {
            case 0,1 -> { /* Se libera exclusivamente al terminar la transacción externa. */ }
            case -1 -> throw new CannotAcquireLockException("Administración ocupada: venció la espera del bloqueo (código -1). Inténtalo de nuevo.");
            case -2 -> throw new ConcurrencyFailureException("Se canceló la espera del bloqueo administrativo (código -2).");
            case -3 -> throw new CannotAcquireLockException("SQL Server detectó un interbloqueo administrativo (código -3). Inténtalo de nuevo.");
            case -999 -> throw new InvalidDataAccessResourceUsageException("SQL Server rechazó sp_getapplock (código -999). Revisa parámetros, permisos y estado de la transacción.");
            default -> throw new DataAccessResourceFailureException("sp_getapplock devolvió un código inesperado: "+result);
        }
    }
    public List<UsuarioResumen> buscar(String q, Integer tipo, Boolean activo) {
        return jdbc.query(SELECT + "WHERE (u.NOMBRE_USUARIO LIKE ? OR t.TIPO_USUARIO LIKE ?) AND (? IS NULL OR u.ID_TIPO_USUARIO=?) AND (? IS NULL OR u.ACTIVO=?) ORDER BY u.NOMBRE_USUARIO", MAP,
            "%"+q+"%", "%"+q+"%", tipo,tipo,activo,activo);
    }
    public Optional<UsuarioResumen> buscarId(Long id) { return jdbc.query(SELECT+"WHERE u.ID_USUARIO=?", MAP,id).stream().findFirst(); }
    public Optional<UsuarioResumen> buscarNombre(String nombre) { return jdbc.query(SELECT+"WHERE u.NOMBRE_USUARIO=?", MAP,nombre).stream().findFirst(); }
    public boolean esAdmin(String nombre, String reservado) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Usuario u JOIN dbo.TipoUsuario t ON t.ID_TIPO_USUARIO=u.ID_TIPO_USUARIO WHERE u.NOMBRE_USUARIO=? AND u.ACTIVO=1 AND t.TIPO_USUARIO=?", Long.class,nombre,reservado) == 1;
    }
    public boolean tipoReservado(Integer id, String reservado) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.TipoUsuario WHERE ID_TIPO_USUARIO=? AND TIPO_USUARIO=?", Long.class,id,reservado) > 0;
    }
    public long administradoresActivos(String reservado) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Usuario u JOIN dbo.TipoUsuario t ON t.ID_TIPO_USUARIO=u.ID_TIPO_USUARIO WHERE u.ACTIVO=1 AND t.TIPO_USUARIO=?", Long.class,reservado);
    }
    public boolean nombreDuplicado(String nombre, Long id) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Usuario WHERE NOMBRE_USUARIO=? AND (? IS NULL OR ID_USUARIO<>?)", Long.class,nombre,id,id)>0;
    }
    public long totalUsuarios() { return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Usuario", Long.class); }
    public boolean tipoEnUso(Integer id) { return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Usuario WHERE ID_TIPO_USUARIO=?",Long.class,id)>0; }
    public void insertar(String nombre, Integer tipo, boolean activo, byte[] codificada) {
        jdbc.update("INSERT INTO dbo.Usuario (NOMBRE_USUARIO,ID_TIPO_USUARIO,ACTIVO,CONTRASENIA) VALUES (?,?,?,?)",nombre,tipo,activo,codificada);
    }
    public void editar(Long id,String nombre,Integer tipo) { jdbc.update("UPDATE dbo.Usuario SET NOMBRE_USUARIO=?,ID_TIPO_USUARIO=? WHERE ID_USUARIO=?",nombre,tipo,id); }
    public void estado(Long id,boolean activo) { jdbc.update("UPDATE dbo.Usuario SET ACTIVO=? WHERE ID_USUARIO=?",activo,id); }
    /** Inserta la cuenta y devuelve su ID_USUARIO (IDENTITY) mediante getGeneratedKeys. */
    public Long insertarDevolviendoId(String nombre, Integer tipo, boolean activo, byte[] codificada) {
        var claves=new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement=connection.prepareStatement(
                "INSERT INTO dbo.Usuario (NOMBRE_USUARIO,ID_TIPO_USUARIO,ACTIVO,CONTRASENIA) VALUES (?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            statement.setString(1,nombre);statement.setInt(2,tipo);statement.setBoolean(3,activo);statement.setBytes(4,codificada);
            return statement;
        },claves);
        Number id=claves.getKey();
        if(id==null) throw new DataAccessResourceFailureException("SQL Server no devolvió el identificador del usuario creado.");
        return id.longValue();
    }
    public void clave(Long id,byte[] codificada) {
        if(versiones!=null) versiones.bloquearCuenta(id);
        if(jdbc.update("UPDATE dbo.Usuario SET CONTRASENIA=? WHERE ID_USUARIO=?",codificada,id)==1 && versiones!=null)
            versiones.incrementarYRevocar(id);
    }
}
