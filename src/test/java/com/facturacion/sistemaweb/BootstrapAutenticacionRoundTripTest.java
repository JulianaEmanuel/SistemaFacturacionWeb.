package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;
import com.facturacion.sistemaweb.model.TipoUsuario;
import com.facturacion.sistemaweb.repository.*;
import com.facturacion.sistemaweb.security.*;

/** Flujo real de codificación y autenticación; JDBC y bloqueo son simulados. */
class BootstrapAutenticacionRoundTripTest {
    @Test void administradorCreadoPorBootstrapPuedeAutenticarseConColumnasChar() throws Exception {
        comprobar("frase-ficticia-de-prueba");
    }
    @Test void utf8YEspaciosDeLaClaveSeConservanSinTruncar() throws Exception {
        comprobar("  frase-ficticia-áéíóú-ñ  ");
    }
    private void comprobar(String clave) throws Exception {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        var usuarios=spy(new AdministracionRepository(jdbc));
        doNothing().when(usuarios).bloquear();
        when(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Usuario",Long.class)).thenReturn(0L);
        var tipos=mock(TipoUsuarioRepository.class);
        var access=mock(AdminAccess.class);when(access.reservado()).thenReturn("ADMINISTRADOR");
        when(tipos.buscar("")).thenReturn(List.of());
        var tipo=mock(TipoUsuario.class);when(tipo.getId()).thenReturn(37);
        when(tipos.saveAndFlush(any(TipoUsuario.class))).thenReturn(tipo);
        var almacenado=new AtomicReference<byte[]>();var enviado=new AtomicReference<byte[]>();
        when(jdbc.update(startsWith("INSERT INTO dbo.Usuario "),any(),any(),any(),any())).thenAnswer(i -> {
            byte[] bytes=i.getArgument(4);enviado.set(bytes);almacenado.set(bytes.clone());return 1;
        });
        var encoder=new ClaveSegura();
        new BootstrapAdminService(usuarios,tipos,access,encoder).crear("  cuenta-simulada  ",clave,clave);
        assertNotNull(almacenado.get());
        assertTrue(almacenado.get().length<8000);
        assertTrue(todosCero(enviado.get()),"La copia temporal se limpia después de insertar.");
        assertFalse(todosCero(almacenado.get()),"La persistencia simulada conserva su propia copia.");
        verify(jdbc).update(startsWith("INSERT INTO dbo.Usuario "),eq("cuenta-simulada"),eq(37),eq(true),any());
        var leido=new AtomicReference<byte[]>();
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<UserDetails>>any(),any(Object.class))).thenAnswer(i -> {
            assertEquals("cuenta-simulada",i.getArgument(2));
            var rs=mock(ResultSet.class);
            when(rs.getString(1)).thenReturn("cuenta-simulada"+" ".repeat(85));
            when(rs.getBytes(2)).thenAnswer(unused -> {byte[] copy=almacenado.get().clone();leido.set(copy);return copy;});
            when(rs.getBoolean(3)).thenReturn(true);when(rs.getLong(4)).thenReturn(824L);
            RowMapper<UserDetails> mapper=i.getArgument(1);
            return List.of(mapper.mapRow(rs,0));
        });
        var service=new AutenticacionUsuarios(jdbc);
        var details=service.loadUserByUsername("  cuenta-simulada  ");
        assertEquals("cuenta-simulada",details.getUsername());assertTrue(details.isEnabled());
        assertTrue(todosCero(leido.get()),"Limpiar los bytes no debe alterar el String entregado al proveedor.");
        assertTrue(encoder.matches(clave,details.getPassword()));
        var provider=new DaoAuthenticationProvider(service);provider.setPasswordEncoder(encoder);
        assertTrue(provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("cuenta-simulada",clave)).isAuthenticated());
        assertThrows(BadCredentialsException.class,()->provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("cuenta-simulada",clave+"-distinta")));
        if(!clave.equals(clave.strip())) assertFalse(encoder.matches(clave.strip(),details.getPassword()));
        Arrays.fill(almacenado.get(),(byte)0);
    }
    private boolean todosCero(byte[] bytes) {
        for(byte b:bytes) if(b!=0) return false;
        return true;
    }
}
