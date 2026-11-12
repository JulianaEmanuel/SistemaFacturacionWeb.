package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.facturacion.sistemaweb.repository.RecuperacionAdminRepository;
import com.facturacion.sistemaweb.repository.RecuperacionAdminRepository.Cuenta;
import com.facturacion.sistemaweb.service.AdministracionException;

class RecuperacionAdminRepositoryTest {
    @Test void compruebaWindowsPermisosBaseYAusenciaDeSuplantacion() {
        var jdbc=mock(JdbcTemplate.class);var repo=new RecuperacionAdminRepository(jdbc);
        when(jdbc.queryForObject(anyString(),eq(Integer.class))).thenReturn(1);assertDoesNotThrow(repo::exigirPermisos);
        verify(jdbc).queryForObject(argThat(s->s.contains("SistemaFacturacionWeb")&&s.contains("sys.login_token")&&s.contains("WINDOWS LOGIN")&&s.contains("WINDOWS GROUP")&&s.contains("ORIGINAL_LOGIN()=SUSER_SNAME()")&&s.contains("IS_SRVROLEMEMBER('sysadmin')=1 OR IS_MEMBER('db_owner')=1")),eq(Integer.class));
        when(jdbc.queryForObject(anyString(),eq(Integer.class))).thenReturn(0);assertThrows(AdministracionException.class,repo::exigirPermisos);
        when(jdbc.queryForObject(anyString(),eq(Integer.class))).thenReturn((Integer)null);assertThrows(AdministracionException.class,repo::exigirPermisos);
    }
    @Test void triggersONoPoderComprobarlosSeRechaza() {
        var jdbc=mock(JdbcTemplate.class);var repo=new RecuperacionAdminRepository(jdbc);
        when(jdbc.queryForObject(anyString(),eq(Long.class))).thenReturn(0L,1L,null);
        assertDoesNotThrow(repo::exigirSinTriggers);assertThrows(AdministracionException.class,repo::exigirSinTriggers);assertThrows(AdministracionException.class,repo::exigirSinTriggers);
    }
    @Test void updateEstaRestringidoAUnaCuentaAdministradoraActivaYSoloUnaColumna() {
        var jdbc=mock(JdbcTemplate.class);var repo=new RecuperacionAdminRepository(jdbc);byte[] datos={1,2,3};
        when(jdbc.update(anyString(),any(),any(),any())).thenReturn(1);assertEquals(1,repo.actualizarClave(824,"ADMINISTRADOR",datos));
        verify(jdbc).update(eq("UPDATE u SET CONTRASENIA=? FROM dbo.Usuario u JOIN dbo.TipoUsuario t ON t.ID_TIPO_USUARIO=u.ID_TIPO_USUARIO WHERE u.ID_USUARIO=? AND u.ACTIVO=1 AND t.TIPO_USUARIO=?"),same(datos),eq(824L),eq("ADMINISTRADOR"));verifyNoMoreInteractions(jdbc);
    }
    @Test void seleccionRevalidaRolEstadoYBloqueoSinLeerSecretos() {
        var jdbc=mock(JdbcTemplate.class);var repo=new RecuperacionAdminRepository(jdbc);
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<Cuenta>>any(),any(),any())).thenReturn(List.of());
        assertThrows(AdministracionException.class,()->repo.bloquearCuenta(824,"ADMINISTRADOR"));
        verify(jdbc).query(argThat(s->s.contains("UPDLOCK,HOLDLOCK")&&s.contains("u.ID_USUARIO=?")&&s.contains("t.TIPO_USUARIO=? AND u.ACTIVO=1")&&!s.contains("CONTRASENIA")),org.mockito.ArgumentMatchers.<RowMapper<Cuenta>>any(),eq(824L),eq("ADMINISTRADOR"));
    }
}
