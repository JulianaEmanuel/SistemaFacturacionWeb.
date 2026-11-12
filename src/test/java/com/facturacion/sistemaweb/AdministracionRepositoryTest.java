package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import com.facturacion.sistemaweb.repository.AdministracionRepository;

class AdministracionRepositoryTest {
    JdbcTemplate jdbc;
    Connection connection;
    PreparedStatement state;
    ResultSet result;
    CallableStatement lock;
    AdministracionRepository repository;
    @BeforeEach void setup() throws Exception {
        jdbc=mock(JdbcTemplate.class);connection=mock(Connection.class);state=mock(PreparedStatement.class);
        result=mock(ResultSet.class);lock=mock(CallableStatement.class);repository=new AdministracionRepository(jdbc);
        when(jdbc.execute(any(ConnectionCallback.class))).thenAnswer(i -> ((ConnectionCallback<?>)i.getArgument(0)).doInConnection(connection));
        when(connection.prepareStatement(anyString())).thenReturn(state);when(state.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);when(result.getInt(1)).thenReturn(1);
        when(connection.prepareCall(anyString())).thenReturn(lock);
    }
    @Test void iniciaTransaccionAntesDeAdquirirBloqueoEnMismaConexion() throws Exception {
        repository.bloquear();
        var order=inOrder(connection,state,result,lock);
        order.verify(connection).getAutoCommit();
        order.verify(connection).prepareStatement(contains("IF @@TRANCOUNT = 0"));
        order.verify(state).executeQuery();order.verify(result).next();order.verify(result).getInt(1);
        order.verify(connection).prepareCall("{? = call sys.sp_getapplock(?, ?, ?, ?)}");
        verify(lock).registerOutParameter(1,Types.INTEGER);
        verify(lock).setString(2,"SistemaFacturacionWeb.Admin");verify(lock).setString(3,"Exclusive");
        verify(lock).setString(4,"Transaction");verify(lock).setInt(5,10000);
        verify(lock).execute();verify(lock).getInt(1);verify(lock).close();verify(state).close();verify(result).close();
        verify(connection,never()).commit();verify(connection,never()).rollback();verify(connection,never()).close();
    }
    @Test void aceptaConcesionInmediataYTrasEspera() throws Exception {
        when(lock.getInt(1)).thenReturn(0,1);
        assertDoesNotThrow(repository::bloquear);assertDoesNotThrow(repository::bloquear);
    }
    @Test void distingueTimeoutCancelacionInterbloqueoYErrorDeLlamada() throws Exception {
        when(lock.getInt(1)).thenReturn(-1,-2,-3,-999,17);
        assertTrue(assertThrows(CannotAcquireLockException.class,repository::bloquear).getMessage().contains("-1"));
        assertTrue(assertThrows(ConcurrencyFailureException.class,repository::bloquear).getMessage().contains("-2"));
        assertTrue(assertThrows(CannotAcquireLockException.class,repository::bloquear).getMessage().contains("-3"));
        var error=assertThrows(InvalidDataAccessResourceUsageException.class,repository::bloquear);
        assertTrue(error.getMessage().contains("-999"));
        assertThrows(DataAccessResourceFailureException.class,repository::bloquear);
    }
    @Test void rechazaAutocommitAntesDeTocarSql() throws Exception {
        when(connection.getAutoCommit()).thenReturn(true);
        assertThrows(InvalidDataAccessApiUsageException.class,repository::bloquear);verify(connection,never()).prepareStatement(anyString());
    }
    @Test void noSolicitaBloqueoSiNoSeInicioTransaccionFisica() throws Exception {
        when(result.getInt(1)).thenReturn(0);
        assertThrows(InvalidDataAccessResourceUsageException.class,repository::bloquear);verify(connection,never()).prepareCall(anyString());
    }
    @Test void retornoNuloNoSeInterpretaComoExito() throws Exception {
        when(lock.wasNull()).thenReturn(true);assertThrows(DataAccessResourceFailureException.class,repository::bloquear);
    }
}
