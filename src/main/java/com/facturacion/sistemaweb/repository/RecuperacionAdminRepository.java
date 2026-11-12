package com.facturacion.sistemaweb.repository;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import com.facturacion.sistemaweb.service.AdministracionException;
import com.facturacion.sistemaweb.security.CredencialVersionRepository;

@Repository @Profile("sqlserver & recuperar-admin & !bootstrap-admin") @ConditionalOnNotWebApplication
public class RecuperacionAdminRepository {
    public record Cuenta(long id,int tipo,String nombre,boolean activa) {
        public String nombreVisible() { return nombre.stripTrailing(); }
    }
    private final JdbcTemplate jdbc;
    private final CredencialVersionRepository versiones;
    public RecuperacionAdminRepository(JdbcTemplate jdbc) { this(jdbc,null); }
    @Autowired public RecuperacionAdminRepository(JdbcTemplate jdbc,CredencialVersionRepository versiones) {
        this.jdbc=jdbc;this.versiones=versiones;
    }
    public void exigirPermisos() {
        Integer permitido=jdbc.queryForObject("SELECT CASE WHEN DB_NAME()=N'SistemaFacturacionWeb' "
            +"AND ORIGINAL_LOGIN()=SUSER_SNAME() "
            +"AND EXISTS(SELECT 1 FROM sys.login_token WHERE type IN ('WINDOWS LOGIN','WINDOWS GROUP')) "
            +"AND (IS_SRVROLEMEMBER('sysadmin')=1 OR IS_MEMBER('db_owner')=1) THEN 1 ELSE 0 END",Integer.class);
        if(!Integer.valueOf(1).equals(permitido)) throw new AdministracionException("Se requiere autenticación Windows sin suplantación y permisos sysadmin o db_owner en SistemaFacturacionWeb.");
    }
    public void exigirSinTriggers() {
        Long cantidad=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM sys.triggers WHERE parent_id=OBJECT_ID(N'dbo.Usuario') AND is_disabled=0",Long.class);
        if(cantidad==null || cantidad!=0) throw new AdministracionException("La recuperación se detuvo: existen triggers activos en Usuario o no se pudo comprobar su ausencia.");
    }
    private static final String SELECT="SELECT u.ID_USUARIO,u.ID_TIPO_USUARIO,u.NOMBRE_USUARIO,u.ACTIVO FROM dbo.Usuario u ";
    public List<Cuenta> candidatas(String reservado) {
        return jdbc.query(SELECT+"JOIN dbo.TipoUsuario t ON t.ID_TIPO_USUARIO=u.ID_TIPO_USUARIO WHERE t.TIPO_USUARIO=? AND u.ACTIVO=1 ORDER BY u.ID_USUARIO",
            (r,n)->new Cuenta(r.getLong(1),r.getInt(2),r.getString(3),r.getBoolean(4)),reservado);
    }
    public Cuenta bloquearCuenta(long id,String reservado) {
        var filas=jdbc.query(SELECT.replace("dbo.Usuario u ","dbo.Usuario u WITH (UPDLOCK,HOLDLOCK) ")
            +"JOIN dbo.TipoUsuario t WITH (UPDLOCK,HOLDLOCK) ON t.ID_TIPO_USUARIO=u.ID_TIPO_USUARIO "
            +"WHERE u.ID_USUARIO=? AND t.TIPO_USUARIO=? AND u.ACTIVO=1",
            (r,n)->new Cuenta(r.getLong(1),r.getInt(2),r.getString(3),r.getBoolean(4)),id,reservado);
        if(filas.size()!=1) throw new AdministracionException("Selecciona una única cuenta administradora existente y activa.");
        return filas.getFirst();
    }
    public int actualizarClave(long id,String reservado,byte[] bytes) {
        if(versiones!=null) versiones.bloquearCuenta(id);
        int actualizadas=jdbc.update("UPDATE u SET CONTRASENIA=? FROM dbo.Usuario u JOIN dbo.TipoUsuario t ON t.ID_TIPO_USUARIO=u.ID_TIPO_USUARIO "
            +"WHERE u.ID_USUARIO=? AND u.ACTIVO=1 AND t.TIPO_USUARIO=?",bytes,id,reservado);
        if(actualizadas==1 && versiones!=null) versiones.incrementarYRevocar(id);
        return actualizadas;
    }
}
