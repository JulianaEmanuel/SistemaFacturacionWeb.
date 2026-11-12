package com.facturacion.sistemaweb.security;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/** Único componente que lee el hash; solo lo entrega al proveedor de autenticación. */
@Service @Profile("sqlserver")
public class AutenticacionUsuarios implements UserDetailsService {
    private final JdbcTemplate jdbc;
    private final CredencialVersionRepository versiones;
    public AutenticacionUsuarios(JdbcTemplate jdbc) { this(jdbc,null); }
    @Autowired public AutenticacionUsuarios(JdbcTemplate jdbc,CredencialVersionRepository versiones) {
        this.jdbc=jdbc;this.versiones=versiones;
    }
    @Override public UserDetails loadUserByUsername(String username) {
        if (username == null || username.isBlank() || username.length()>100) throw new UsernameNotFoundException("Credenciales incorrectas.");
        var result=jdbc.query("SELECT NOMBRE_USUARIO,CONTRASENIA,ACTIVO,ID_USUARIO FROM dbo.Usuario WHERE NOMBRE_USUARIO=?", (r,n) -> {
            byte[] bytes=r.getBytes(2);
            try {
                long id=r.getLong(4);
                var builder=User.withUsername(r.getString(1).stripTrailing())
                    .password(new String(bytes,StandardCharsets.UTF_8)).authorities("USER","ACCOUNT_ID_"+id);
                if(versiones!=null && versiones.disponible())
                    builder.authorities("USER","ACCOUNT_ID_"+id,"CREDENTIAL_VERSION_"+versiones.version(id));
                return builder.disabled(!r.getBoolean(3)).build();
            } finally { if(bytes!=null) Arrays.fill(bytes,(byte)0); }
        }, username.strip());
        if(result.size()!=1) throw new UsernameNotFoundException("Credenciales incorrectas.");
        return result.getFirst();
    }
}
