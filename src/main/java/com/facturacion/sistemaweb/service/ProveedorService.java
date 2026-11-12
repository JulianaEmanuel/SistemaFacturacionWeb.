package com.facturacion.sistemaweb.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.facturacion.sistemaweb.model.Persona;
import com.facturacion.sistemaweb.model.Proveedor;
import com.facturacion.sistemaweb.model.ProveedorForm;
import com.facturacion.sistemaweb.repository.PersonaRepository;
import com.facturacion.sistemaweb.repository.ProveedorRepository;

@Service
@Profile("sqlserver")
public class ProveedorService {

	private final ProveedorRepository proveedorRepository;
	private final PersonaRepository personaRepository;

	public ProveedorService(ProveedorRepository proveedorRepository, PersonaRepository personaRepository) {
		this.proveedorRepository = proveedorRepository;
		this.personaRepository = personaRepository;
	}

	@Transactional(readOnly = true)
	public List<Proveedor> listar(String filtro) {
		if (filtro == null || filtro.isBlank()) {
			return proveedorRepository.listarOrdenados();
		}
		return proveedorRepository.buscar(filtro.strip());
	}

	@Transactional(readOnly = true)
	public List<Persona> listarPersonasDisponibles() {
		return proveedorRepository.listarPersonasDisponibles();
	}

	@Transactional(readOnly = true)
	public Proveedor buscarPorId(Long idProveedor) {
		return proveedorRepository.buscarPorIdConPersona(idProveedor)
				.orElseThrow(() -> new NoSuchElementException("No se encontró el proveedor solicitado."));
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public Proveedor registrar(ProveedorForm form) {
		Long idPersona = form.getIdPersona();
		Persona persona = personaRepository.findById(idPersona)
				.orElseThrow(() -> new PersonaNoEncontradaException("La persona seleccionada no existe."));
		if (proveedorRepository.existePorPersona(idPersona)) {
			throw new ProveedorDuplicadoException("La persona seleccionada ya está registrada como proveedor.");
		}

		Proveedor proveedor = new Proveedor();
		proveedor.setPersona(persona);
		proveedor.setEmail(normalizarEmail(form.getEmail()));
		return proveedorRepository.save(proveedor);
	}

	@Transactional
	public Proveedor actualizar(Long idProveedor, ProveedorForm form) {
		Proveedor proveedor = buscarPorId(idProveedor);
		proveedor.setEmail(normalizarEmail(form.getEmail()));
		return proveedorRepository.save(proveedor);
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public void eliminar(Long idProveedor) {
		Proveedor proveedor = buscarPorId(idProveedor);
		if (proveedorRepository.tieneProductos(idProveedor)) {
			throw new ProveedorRelacionadoException("No se puede eliminar: el proveedor tiene productos asociados.");
		}

		try {
			proveedorRepository.delete(proveedor);
			proveedorRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new ProveedorRelacionadoException(
					"No se puede eliminar porque existen registros relacionados con este proveedor.", exception);
		}
	}

	private String normalizarEmail(String email) {
		return email == null || email.isBlank() ? null : email.strip();
	}
}