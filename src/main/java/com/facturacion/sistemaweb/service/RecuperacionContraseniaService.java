package com.facturacion.sistemaweb.service;

import java.util.*;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.facturacion.sistemaweb.repository.IntentoRecuperacionRepository;
import com.facturacion.sistemaweb.security.ClaveSegura;
import com.facturacion.sistemaweb.security.CredencialVersionRepository;
import com.facturacion.sistemaweb.security.TokenSeguro;

@Service @Profile("sqlserver")
public class RecuperacionContraseniaService {
    public record Enlace(String correo,String token) { }
    private record CuentaCorreo(long id,String correo) { }
    private record Token(long idUsuario,long idToken,String correo) { }
    private final JdbcTemplate jdbc;
    private final IntentoRecuperacionRepository intentos;
    private final TokenSeguro tokens;
    private final ClaveSegura claves;
    private final CredencialVersionRepository versiones;

    public RecuperacionContraseniaService(JdbcTemplate jdbc,IntentoRecuperacionRepository intentos,
            TokenSeguro tokens,ClaveSegura claves,CredencialVersionRepository versiones) {
        this.jdbc=jdbc;this.intentos=intentos;this.tokens=tokens;this.claves=claves;this.versiones=versiones;
    }

    @Transactional
    public Enlace solicitarRestablecimiento(String correo,String ip) {
        String normalizado=normalizar(correo);
        boolean formatoValido=correoValido(normalizado);
        if(!intentos.permitir((byte)1,tokens.huella("recuperacion-correo",normalizado),
                tokens.huella("ip",ipSeguro(ip)))) return null;
        if(!formatoValido) return null;
        var cuentas=jdbc.query("SELECT ID_USUARIO,CORREO_USUARIO FROM dbo.Usuario "
                +"WHERE CORREO_USUARIO=? AND CORREO_VERIFICADO_EN IS NOT NULL AND ACTIVO=1",
            (r,n)->new CuentaCorreo(r.getLong(1),r.getString(2)),normalizado);
        if(cuentas.size()!=1) return null;
        versiones.bloquearCuenta(cuentas.getFirst().id());
        var actualizadas=jdbc.query("SELECT ID_USUARIO,CORREO_USUARIO FROM dbo.Usuario WITH (UPDLOCK,HOLDLOCK) "
                +"WHERE ID_USUARIO=? AND CORREO_USUARIO=? AND CORREO_VERIFICADO_EN IS NOT NULL AND ACTIVO=1",
            (r,n)->new CuentaCorreo(r.getLong(1),r.getString(2)),cuentas.getFirst().id(),normalizado);
        if(actualizadas.size()!=1) return null;
        CuentaCorreo cuenta=actualizadas.getFirst();
        long id=cuenta.id();
        String token=tokens.generar();
        byte[] hash=tokens.hashToken(token);
        try {
            jdbc.update("UPDATE dbo.RecuperacionContrasenia SET REVOCADO_EN=SYSUTCDATETIME() "
                +"WHERE ID_USUARIO=? AND USADO_EN IS NULL AND REVOCADO_EN IS NULL",id);
            jdbc.update("INSERT dbo.RecuperacionContrasenia(ID_USUARIO,HASH_TOKEN,CREADO_EN,EXPIRA_EN) "
                +"VALUES(?,?,SYSUTCDATETIME(),DATEADD(minute,15,SYSUTCDATETIME()))",id,hash);
        } finally { Arrays.fill(hash,(byte)0); }
        return new Enlace(cuenta.correo(),token);
    }

    @Transactional
    public Enlace solicitarVerificacion(long idUsuario,String correo,String ip) {
        String normalizado=normalizar(correo);
        boolean formatoValido=correoValido(normalizado);
        if(!intentos.permitir((byte)2,tokens.huella("verificacion-correo",normalizado),
                tokens.huella("ip",ipSeguro(ip))) || !formatoValido) return null;
        versiones.bloquearCuenta(idUsuario);
        Integer cuenta=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Usuario WITH (UPDLOCK,HOLDLOCK) "
            +"WHERE ID_USUARIO=? AND ACTIVO=1",Integer.class,idUsuario);
        if(!Integer.valueOf(1).equals(cuenta)) return null;
        Integer ocupado=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Usuario WHERE CORREO_USUARIO=? "
            +"AND CORREO_VERIFICADO_EN IS NOT NULL AND ID_USUARIO<>?",Integer.class,normalizado,idUsuario);
        if(ocupado!=null && ocupado>0) return null;
        String token=tokens.generar();
        byte[] hash=tokens.hashToken(token);
        try {
            jdbc.update("UPDATE dbo.VerificacionCorreo SET REVOCADO_EN=SYSUTCDATETIME() "
                +"WHERE ID_USUARIO=? AND USADO_EN IS NULL AND REVOCADO_EN IS NULL",idUsuario);
            jdbc.update("INSERT dbo.VerificacionCorreo(ID_USUARIO,CORREO_DESTINO,HASH_TOKEN,CREADO_EN,EXPIRA_EN) "
                +"VALUES(?,?,?,SYSUTCDATETIME(),DATEADD(minute,15,SYSUTCDATETIME()))",idUsuario,normalizado,hash);
        } finally { Arrays.fill(hash,(byte)0); }
        return new Enlace(normalizado,token);
    }

    @Transactional
    public boolean confirmarCorreo(String token,String ip) {
        limitarPruebaToken((byte)2,"verificacion-token",token,ip);
        byte[] hash=tokens.hashToken(token);
        try {
            var preseleccion=jdbc.query("SELECT ID_USUARIO FROM dbo.VerificacionCorreo "
                +"WHERE HASH_TOKEN=?", (r,n)->r.getLong(1),hash);
            if(preseleccion.size()!=1) return false;
            versiones.bloquearCuenta(preseleccion.getFirst());
            var encontrados=jdbc.query("SELECT t.ID_VERIFICACION,t.ID_USUARIO,t.CORREO_DESTINO FROM dbo.VerificacionCorreo t "
                +"WITH (UPDLOCK,HOLDLOCK) JOIN dbo.Usuario u WITH (UPDLOCK,HOLDLOCK) ON u.ID_USUARIO=t.ID_USUARIO "
                +"WHERE t.HASH_TOKEN=? AND t.USADO_EN IS NULL AND t.REVOCADO_EN IS NULL "
                +"AND t.EXPIRA_EN>SYSUTCDATETIME() AND u.ACTIVO=1",
                (r,n)->new Token(r.getLong(2),r.getLong(1),r.getString(3)),hash);
            if(encontrados.size()!=1) return false;
            Token enlace=encontrados.getFirst();
            if(jdbc.update("UPDATE dbo.VerificacionCorreo SET USADO_EN=SYSUTCDATETIME() WHERE ID_VERIFICACION=? "
                +"AND USADO_EN IS NULL AND REVOCADO_EN IS NULL AND EXPIRA_EN>SYSUTCDATETIME()",enlace.idToken())!=1) return false;
            if(jdbc.update("UPDATE dbo.Usuario SET CORREO_USUARIO=?,CORREO_VERIFICADO_EN=SYSUTCDATETIME() "
                +"WHERE ID_USUARIO=? AND ACTIVO=1",enlace.correo(),enlace.idUsuario())!=1)
                throw new AdministracionException("La cuenta ya no está disponible para verificar el correo.");
            versiones.revocarRecuperaciones(enlace.idUsuario());
            return true;
        } finally { Arrays.fill(hash,(byte)0); }
    }

    @Transactional
    public void restablecer(String token,String clave,String confirmacion,String ip) {
        limitarPruebaToken((byte)1,"recuperacion-token",token,ip);
        claves.validar(clave,confirmacion);
        byte[] hash=tokens.hashToken(token);
        byte[] codificada=null;
        try {
            var preseleccion=jdbc.query("SELECT ID_USUARIO FROM dbo.RecuperacionContrasenia WHERE HASH_TOKEN=?",
                (r,n)->r.getLong(1),hash);
            if(preseleccion.size()!=1) throw new AdministracionException("El enlace de seguridad no es válido o expiró.");
            versiones.bloquearCuenta(preseleccion.getFirst());
            var encontrados=jdbc.query("SELECT t.ID_RECUPERACION,t.ID_USUARIO FROM dbo.RecuperacionContrasenia t "
                +"WITH (UPDLOCK,HOLDLOCK) JOIN dbo.Usuario u WITH (UPDLOCK,HOLDLOCK) ON u.ID_USUARIO=t.ID_USUARIO "
                +"WHERE t.HASH_TOKEN=? AND t.USADO_EN IS NULL AND t.REVOCADO_EN IS NULL "
                +"AND t.EXPIRA_EN>SYSUTCDATETIME() AND u.ACTIVO=1",
                (r,n)->new long[]{r.getLong(2),r.getLong(1)},hash);
            if(encontrados.size()!=1) throw new AdministracionException("El enlace de seguridad no es válido o expiró.");
            long[] registro=encontrados.getFirst();
            long idUsuario=registro[0],idToken=registro[1];
            if(jdbc.update("UPDATE dbo.RecuperacionContrasenia SET USADO_EN=SYSUTCDATETIME() "
                +"WHERE ID_RECUPERACION=? AND USADO_EN IS NULL AND REVOCADO_EN IS NULL AND EXPIRA_EN>SYSUTCDATETIME()",
                idToken)!=1) throw new AdministracionException("El enlace de seguridad no es válido o expiró.");
            codificada=claves.codificar(clave);
            if(jdbc.update("UPDATE dbo.Usuario SET CONTRASENIA=? WHERE ID_USUARIO=? AND ACTIVO=1",codificada,idUsuario)!=1)
                throw new AdministracionException("El enlace de seguridad no es válido o expiró.");
            versiones.incrementarYRevocar(idUsuario);
        } finally {
            Arrays.fill(hash,(byte)0);
            if(codificada!=null) Arrays.fill(codificada,(byte)0);
        }
    }

    private static String normalizar(String correo) {
        if(correo==null) return "";
        String limpio=correo.strip();
        return limpio.length()<=254?limpio.toLowerCase(Locale.ROOT):"correo-demasiado-largo";
    }

    private static boolean correoValido(String correo) {
        if(correo.isBlank() || correo.length()>254) return false;
        try {
            InternetAddress address=new InternetAddress(correo,true);
            address.validate();
            return correo.equalsIgnoreCase(address.getAddress());
        } catch(AddressException ex) { return false; }
    }

    private static String ipSeguro(String ip) {
        return ip==null || ip.isBlank()?"desconocida":ip;
    }

    private void limitarPruebaToken(byte tipo,String contexto,String token,String ip) {
        String acotado=token==null || token.length()>64?"token-no-valido":token;
        if(!intentos.permitir(tipo,tokens.huella(contexto,acotado),tokens.huella("ip",ipSeguro(ip))))
            throw new AdministracionException("El enlace de seguridad no es válido o expiró.");
    }
}
