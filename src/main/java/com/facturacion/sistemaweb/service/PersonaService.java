package com.facturacion.sistemaweb.service;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.facturacion.sistemaweb.model.Persona;
import com.facturacion.sistemaweb.model.PersonaForm;
import com.facturacion.sistemaweb.repository.PersonaRepository;

@Service
@Profile("sqlserver")
public class PersonaService {

	private final PersonaRepository personaRepository;

	private final com.facturacion.sistemaweb.repository.PersonaContactosRepository contactos;
    public PersonaService(PersonaRepository personaRepository,com.facturacion.sistemaweb.repository.PersonaContactosRepository contactos) {
        this.contactos=contactos;
		this.personaRepository = personaRepository;
	}

	@Transactional(readOnly = true)
	public List<Persona> listar(String filtro) {
		if (filtro == null || filtro.isBlank()) {
			var lista=personaRepository.listarOrdenadas();contactos.cargar(lista);return lista;
		}
		var lista=personaRepository.buscar(filtro.strip());contactos.cargar(lista);return lista;
	}

	@Transactional(readOnly = true)
	public Persona buscarPorId(Long idPersona) {
		var persona=personaRepository.findById(idPersona)
				.orElseThrow(() -> new java.util.NoSuchElementException("No se encontró la persona solicitada."));
        contactos.cargar(persona);return persona;
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public Persona registrar(PersonaForm form) {
		validarCedula(form.getCedulaPersona(), null);
		validarContacto(form,true);contactos.exigirDisponible();
        Persona persona = new Persona();
		aplicarFormulario(persona, form);
        var guardada=personaRepository.saveAndFlush(persona);contactos.guardar(guardada.getIdPersona(),form);return guardada;
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public Persona actualizar(Long idPersona, PersonaForm form) {
		Persona persona = buscarPorId(idPersona);
		validarCedula(form.getCedulaPersona(), idPersona);
		validarContacto(form,false);
        if(!contactos.disponible() && (form.getDireccionPersona()!=null || form.getCorreoPersona()!=null || form.getTelefonoPersona()!=null)) contactos.exigirDisponible();
        aplicarFormulario(persona, form);var guardada=personaRepository.save(persona);
        if(contactos.disponible()) contactos.guardar(guardada.getIdPersona(),form);return guardada;
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public void eliminar(Long idPersona) {
		Persona persona = buscarPorId(idPersona);
		if (personaRepository.tieneFacturas(idPersona)) {
			throw new PersonaRelacionadaException("No se puede eliminar: la persona tiene facturas relacionadas.");
		}
		if (personaRepository.esEmpleado(idPersona)) {
			throw new PersonaRelacionadaException("No se puede eliminar: la persona también está registrada como empleado.");
		}
		if (personaRepository.esProveedor(idPersona)) {
			throw new PersonaRelacionadaException("No se puede eliminar: la persona también está registrada como proveedor.");
		}

		try {
			personaRepository.delete(persona);
			personaRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new PersonaRelacionadaException("No se puede eliminar porque existen registros relacionados.", exception);
		}
	}

    public boolean contactosDisponibles(){return contactos.disponible();}
    private void validarContacto(PersonaForm form,boolean nuevo) {
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var errores=factory.getValidator().validate(form,nuevo?PersonaForm.Nuevo.class:jakarta.validation.groups.Default.class);
            if(!errores.isEmpty()) throw new jakarta.validation.ConstraintViolationException(errores);
        }
    }
	private void aplicarFormulario(Persona persona, PersonaForm form) {
		persona.setCedulaPersona(form.getCedulaPersona().strip());
		persona.setNombresPersona(form.getNombresPersona().strip());
		persona.setApellidosPersona(form.getApellidosPersona().strip());
		var fecha = form.getFechaNacimientoPersona();
		var hora = persona.getFechaNacimientoPersona() == null
				? java.time.LocalTime.MIDNIGHT : persona.getFechaNacimientoPersona().toLocalTime();
		persona.setFechaNacimientoPersona(fecha == null ? null : fecha.atTime(hora));
	}

	private void validarCedula(String cedula, Long idPersona) {
		if (cedula == null || !cedula.matches("[0-9]{10}")) {
			throw new IllegalArgumentException("La cédula debe contener exactamente 10 dígitos numéricos.");
		}

		boolean duplicada = idPersona == null
				? personaRepository.existeCedula(cedula)
				: personaRepository.existeCedulaEnOtraPersona(cedula, idPersona);
		if (duplicada) {
			throw new CedulaDuplicadaException("Ya existe una persona registrada con esa cédula.");
		}
	}
}
