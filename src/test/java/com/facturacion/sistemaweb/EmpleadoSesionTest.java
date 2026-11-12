package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.security.AdminAccess;
import com.facturacion.sistemaweb.service.*;

class EmpleadoSesionTest {
    EntityManager em=mock(EntityManager.class);AdminAccess access=mock(AdminAccess.class);
    EmpleadoSesionService service=new EmpleadoSesionService(em,access);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void exigeSesionEIdentidadEstable() {
        assertThrows(AccessDeniedException.class,()->service.actual(false));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("ana","",List.of(new SimpleGrantedAuthority("USER"))));
        assertThrows(AccessDeniedException.class,()->service.actual(false));verifyNoInteractions(em);
    }
    @Test void usuarioYEmpleadoNoSeConfunden() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("ana","",List.of(new SimpleGrantedAuthority("ACCOUNT_ID_7"))));
        Usuario user=mock(Usuario.class);when(user.isActivo()).thenReturn(true);when(user.getNombreUsuario()).thenReturn("ana  ");when(em.find(Usuario.class,7L,LockModeType.PESSIMISTIC_READ)).thenReturn(user);
        assertEquals(7L,service.actual(true).id());
        var query=mock(TypedQuery.class);when(em.createQuery(anyString(),eq(Empleado.class))).thenReturn(query);when(query.setParameter("usuario",7L)).thenReturn(query);when(query.setLockMode(any())).thenReturn(query);
        Empleado employee=mock(Empleado.class);when(employee.getIdEmpleado()).thenReturn(55L);when(query.getResultList()).thenReturn(List.of(employee));
        assertEquals(55L,service.empleado(7L,true).getIdEmpleado());
    }
    @Test void rechazaCeroYVariosEmpleados() {
        var query=mock(TypedQuery.class);when(em.createQuery(anyString(),eq(Empleado.class))).thenReturn(query);when(query.setParameter("usuario",7L)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(),List.of(new Empleado(),new Empleado()));
        assertThrows(FacturaException.class,()->service.empleado(7L,false));assertThrows(FacturaException.class,()->service.empleado(7L,false));
    }
}
