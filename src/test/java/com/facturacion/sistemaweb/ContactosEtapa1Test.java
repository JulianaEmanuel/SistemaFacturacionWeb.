package com.facturacion.sistemaweb;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.repository.PersonaContactosRepository;
import com.facturacion.sistemaweb.service.FacturacionSchema;

class ContactosEtapa1Test {
    PersonaForm form(){var f=new PersonaForm();f.setCedulaPersona("0912345678");f.setNombresPersona("Ana");f.setApellidosPersona("Paz");return f;}
    @Test void exigeDireccionYCorreoSoloAlCrearYTelefonoEsOpcional(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var validator=factory.getValidator();var f=form();assertTrue(validator.validate(f).isEmpty());
            var errores=validator.validate(f,PersonaForm.Nuevo.class);assertEquals(Set.of("direccionPersona","correoPersona"),errores.stream().map(e->e.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet()));
            f.setDireccionPersona("Av. de prueba");f.setCorreoPersona("cliente@example.invalid");assertTrue(validator.validate(f,PersonaForm.Nuevo.class).isEmpty());
            f.setCorreoPersona("correo inválido");assertFalse(validator.validate(f).isEmpty());
        }
    }
    @Test void validaLongitudesYConservaCedulaDeDiezDigitos(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var f=form();f.setDireccionPersona("a".repeat(251));f.setTelefonoPersona("1".repeat(26));f.setCedulaPersona("1234567890123");
            var campos=factory.getValidator().validate(f).stream().map(e->e.getPropertyPath().toString()).toList();assertTrue(campos.containsAll(List.of("direccionPersona","telefonoPersona","cedulaPersona")));
        }
    }
    @Test void esquemaAnteriorNoConsultaColumnasInexistentesNiLasEscribe(){
        var jdbc=mock(NamedParameterJdbcTemplate.class);var schema=mock(FacturacionSchema.class);var repo=new PersonaContactosRepository(jdbc,schema);
        var p=new Persona();repo.cargar(p);assertNull(p.getDireccionPersona());verifyNoInteractions(jdbc);
        assertThrows(IllegalStateException.class,()->repo.guardar(1L,form()));verifyNoInteractions(jdbc);
    }
    @Test void deteccionDeMigracionEsSoloLecturaYNoEjecutaDdl(){
        var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(Integer.class))).thenReturn(0);
        var schema=new FacturacionSchema(jdbc);assertFalse(schema.disponible());assertFalse(schema.contactos());assertFalse(schema.disponible());
        verify(jdbc,times(1)).queryForObject(argThat(sql->sql.startsWith("SELECT")&&sql.contains("COL_LENGTH")),eq(Integer.class));verifyNoMoreInteractions(jdbc);
    }
}
