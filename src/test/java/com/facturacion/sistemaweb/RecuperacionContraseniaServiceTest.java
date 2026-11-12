package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.facturacion.sistemaweb.repository.IntentoRecuperacionRepository;
import com.facturacion.sistemaweb.security.ClaveSegura;
import com.facturacion.sistemaweb.security.CredencialVersionRepository;
import com.facturacion.sistemaweb.security.TokenSeguro;
import com.facturacion.sistemaweb.service.AdministracionException;
import com.facturacion.sistemaweb.service.RecuperacionContraseniaService;

@ExtendWith(MockitoExtension.class)
class RecuperacionContraseniaServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock IntentoRecuperacionRepository intentos;
    @Mock ClaveSegura claves;
    @Mock CredencialVersionRepository versiones;
    RecuperacionContraseniaService service;
    TokenSeguro tokens;

    @BeforeEach void configurar() {
        tokens=new TokenSeguro("secreto-de-prueba-de-al-menos-32-bytes");
        service=new RecuperacionContraseniaService(jdbc,intentos,tokens,claves,versiones);
    }

    @Test void respuestaDeCuentaNoRegistradaNoCreaTokenNiEnviaCorreo() {
        when(intentos.permitir(eq((byte)1),any(),any())).thenReturn(true);
        when(jdbc.query(contains("CORREO_VERIFICADO_EN"),any(RowMapper.class),eq("nadie@example.test"))).thenReturn(List.of());
        assertNull(service.solicitarRestablecimiento("Nadie@example.test","127.0.0.1"));
        verify(jdbc,never()).update(startsWith("INSERT dbo.RecuperacionContrasenia"),any(),any());
    }

    @Test void limiteDeSolicitudesSeEvaluaAntesDeBuscarLaCuenta() {
        when(intentos.permitir(eq((byte)1),any(),any())).thenReturn(false);
        assertNull(service.solicitarRestablecimiento("usuario@example.test","127.0.0.1"));
        verifyNoInteractions(jdbc);
    }

    @Test void validaLaNuevaClaveAntesDeConsumirElToken() {
        when(intentos.permitir(eq((byte)1),any(),any())).thenReturn(true);
        doThrow(new AdministracionException("La contraseña debe tener entre 12 y 128 caracteres, sin recortar espacios."))
            .when(claves).validar("corta","corta");
        assertThrows(AdministracionException.class,()->service.restablecer(tokens.generar(),"corta","corta","127.0.0.1"));
        verifyNoInteractions(jdbc,versiones);
    }

    @Test void tokenSeConsumeUnaVezYElCambioIncrementaLaVersionDeCredencial() {
        when(intentos.permitir(eq((byte)1),any(),any())).thenReturn(true);
        AtomicBoolean disponible=new AtomicBoolean(true);
        when(jdbc.query(eq("SELECT ID_USUARIO FROM dbo.RecuperacionContrasenia WHERE HASH_TOKEN=?"),
                any(RowMapper.class),any(byte[].class))).thenReturn(List.of(44L));
        when(jdbc.query(contains("t.ID_RECUPERACION"),any(RowMapper.class),any(byte[].class)))
            .thenAnswer(invocation->disponible.getAndSet(false)?List.of(new long[]{44,91}):List.of());
        when(jdbc.update(startsWith("UPDATE dbo.RecuperacionContrasenia SET USADO_EN"),anyLong())).thenReturn(1);
        when(jdbc.update(startsWith("UPDATE dbo.Usuario SET CONTRASENIA"),any(byte[].class),anyLong())).thenReturn(1);
        when(claves.codificar("Una contraseña válida")).thenReturn(new byte[]{1,2,3});
        String token=tokens.generar();

        service.restablecer(token,"Una contraseña válida","Una contraseña válida","127.0.0.1");
        assertThrows(AdministracionException.class,()->service.restablecer(token,"Una contraseña válida","Una contraseña válida","127.0.0.1"));
        verify(jdbc,times(1)).update(startsWith("UPDATE dbo.Usuario SET CONTRASENIA"),any(byte[].class),eq(44L));
        verify(versiones).incrementarYRevocar(44L);
    }

    @Test void consultaRechazaTokensExpiradosYUsadosEnElServidor() {
        when(intentos.permitir(eq((byte)1),any(),any())).thenReturn(true);
        when(jdbc.query(eq("SELECT ID_USUARIO FROM dbo.RecuperacionContrasenia WHERE HASH_TOKEN=?"),
                any(RowMapper.class),any(byte[].class))).thenReturn(List.of(44L));
        when(jdbc.query(contains("t.ID_RECUPERACION"),any(RowMapper.class),any(byte[].class))).thenReturn(List.of());
        assertThrows(AdministracionException.class,()->service.restablecer(tokens.generar(),"Una contraseña válida","Una contraseña válida","127.0.0.1"));
        org.mockito.ArgumentCaptor<String> sql=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc,times(2)).query(sql.capture(),any(RowMapper.class),any(byte[].class));
        String consultaBloqueada=sql.getAllValues().stream().filter(s->s.contains("t.ID_RECUPERACION")).findFirst().orElseThrow();
        assertTrue(consultaBloqueada.contains("USADO_EN IS NULL"));
        assertTrue(consultaBloqueada.contains("REVOCADO_EN IS NULL"));
        assertTrue(consultaBloqueada.contains("EXPIRA_EN>SYSUTCDATETIME()"));
    }
}
