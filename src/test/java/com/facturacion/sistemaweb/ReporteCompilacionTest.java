package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import com.facturacion.sistemaweb.model.ReporteFiltro;
import com.facturacion.sistemaweb.model.ReporteTipo;

/** Reproduce la ausencia de ReporteRepository$1 sin borrar archivos ni abrir SQL Server. */
class ReporteCompilacionTest {
    @ParameterizedTest
    @EnumSource(ReporteTipo.class)
    @SuppressWarnings({"unchecked","rawtypes"})
    void consultaTodosLosReportesSinClaseAuxiliarDelSwitch(ReporteTipo tipo) throws Exception {
        String nombre="com.facturacion.sistemaweb.repository.ReporteRepository";
        ClassLoader aislado=new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(name.startsWith(nombre+"$")) throw new ClassNotFoundException("Clase auxiliar ausente en esta prueba");
                if(!name.equals(nombre)) return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)) {
                    Class<?> clase=findLoadedClass(name);
                    if(clase==null) {
                        try(InputStream entrada=getParent().getResourceAsStream(name.replace('.','/')+".class")) {
                            if(entrada==null) throw new ClassNotFoundException(name);
                            byte[] bytes=entrada.readAllBytes();clase=defineClass(name,bytes,0,bytes.length);
                        } catch(java.io.IOException ex) { throw new ClassNotFoundException(name,ex); }
                    }
                    if(resolve) resolveClass(clase);
                    return clase;
                }
            }
        };
        var jdbc=mock(NamedParameterJdbcTemplate.class);
        doReturn(List.of()).when(jdbc).query(anyString(),any(SqlParameterSource.class),any(RowMapper.class));
        Class<?> clase=aislado.loadClass(nombre);
        var repository=clase.getConstructor(NamedParameterJdbcTemplate.class).newInstance(jdbc);
        var filtro=new ReporteFiltro.Validado(LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),null,null,null,"dia","cantidad","",10);
        assertEquals(List.of(),clase.getMethod("filas",ReporteTipo.class,ReporteFiltro.Validado.class).invoke(repository,tipo,filtro));
        if(tipo==ReporteTipo.RESUMEN) verifyNoInteractions(jdbc);
        else verify(jdbc).query(anyString(),any(SqlParameterSource.class),any(RowMapper.class));
    }
}
