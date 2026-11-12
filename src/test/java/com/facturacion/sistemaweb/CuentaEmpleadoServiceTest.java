package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.service.*;

class CuentaEmpleadoServiceTest {
    PersonaService personas=mock(PersonaService.class);
    AdministracionService administracion=mock(AdministracionService.class);
    EmpleadoService empleados=mock(EmpleadoService.class);
    CuentaEmpleadoService servicio=new CuentaEmpleadoService(personas,administracion,empleados);

    CuentaEmpleadoForm form() {
        var f=new CuentaEmpleadoForm();
        f.setCedulaPersona("0912345678");f.setNombresPersona("Ana");f.setApellidosPersona("Paz");
        f.setFechaNacimientoPersona(LocalDate.of(1995,4,20));f.setDireccionPersona("Av. Principal 123");
        f.setTelefonoPersona("");f.setCorreoPersona("ana@example.invalid");f.setNombre("ana.paz");
        return f;
    }

    @Test void creaCuentaPersonaYEmpleadoEnOrden() {
        when(administracion.registrarCuentaDeEmpleado(any(),eq("clave-segura-123"),eq("clave-segura-123"),eq(false))).thenReturn(15L);
        var persona=new Persona();persona.setIdPersona(40L);
        when(personas.registrar(any())).thenReturn(persona);
        var empleado=mock(Empleado.class);when(empleados.registrar(any())).thenReturn(empleado);

        assertSame(empleado,servicio.registrar(form(),"clave-segura-123","clave-segura-123"));

        var orden=inOrder(administracion,personas,empleados);
        orden.verify(administracion).registrarCuentaDeEmpleado(argThat(u -> u.getNombre().equals("ana.paz") && u.getIdTipo()==null && u.isActivo()),
            eq("clave-segura-123"),eq("clave-segura-123"),eq(false));
        orden.verify(personas).registrar(argThat(p -> p.getCedulaPersona().equals("0912345678") && p.getNombresPersona().equals("Ana")
            && p.getApellidosPersona().equals("Paz") && p.getFechaNacimientoPersona().equals(LocalDate.of(1995,4,20))
            && p.getCorreoPersona().equals("ana@example.invalid") && p.getDireccionPersona().equals("Av. Principal 123")
            && p.getTelefonoPersona()==null));
        orden.verify(empleados).registrar(argThat(e -> e.getIdPersona()==40L && e.getIdUsuario()==15L));
    }

    @Test void rolAdministradorSePasaAlCrearLaCuenta() {
        when(administracion.registrarCuentaDeEmpleado(any(),any(),any(),eq(true))).thenReturn(16L);
        var persona=new Persona();persona.setIdPersona(41L);when(personas.registrar(any())).thenReturn(persona);
        var f=form();f.setAdministrador(true);
        servicio.registrar(f,"clave-segura-123","clave-segura-123");
        verify(administracion).registrarCuentaDeEmpleado(any(),eq("clave-segura-123"),eq("clave-segura-123"),eq(true));
        verify(empleados).registrar(argThat(e -> e.getIdPersona()==41L && e.getIdUsuario()==16L));
    }

    @Test void siLaCuentaFallaNoSeRegistraLaPersona() {
        when(administracion.registrarCuentaDeEmpleado(any(),any(),any(),anyBoolean())).thenThrow(new AdministracionException("Ya existe un usuario con ese nombre."));
        assertThrows(AdministracionException.class,()->servicio.registrar(form(),"clave-segura-123","clave-segura-123"));
        verifyNoInteractions(personas,empleados);
    }

    @Test void cedulaDuplicadaDetieneElAltaAntesDelEmpleado() {
        when(administracion.registrarCuentaDeEmpleado(any(),any(),any(),anyBoolean())).thenReturn(15L);
        when(personas.registrar(any())).thenThrow(new CedulaDuplicadaException("Ya existe una persona registrada con esa cédula."));
        assertThrows(CedulaDuplicadaException.class,()->servicio.registrar(form(),"clave-segura-123","clave-segura-123"));
        verifyNoInteractions(empleados);
    }
}
