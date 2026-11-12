package com.facturacion.sistemaweb.controller;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.facturacion.sistemaweb.model.Persona;
import com.facturacion.sistemaweb.model.Proveedor;
import com.facturacion.sistemaweb.model.ProveedorForm;
import com.facturacion.sistemaweb.service.PersonaNoEncontradaException;
import com.facturacion.sistemaweb.service.ProveedorDuplicadoException;
import com.facturacion.sistemaweb.service.ProveedorRelacionadoException;
import com.facturacion.sistemaweb.service.ProveedorService;

import jakarta.validation.Valid;

@Controller
@Profile("sqlserver")
@RequestMapping("/modulos/proveedores")
public class ProveedorController {

	private final ProveedorService proveedorService;

	public ProveedorController(ProveedorService proveedorService) {
		this.proveedorService = proveedorService;
	}

	@GetMapping
	public String listar(@RequestParam(required = false) String q, Model model) {
		model.addAttribute("proveedores", proveedorService.listar(q));
		model.addAttribute("filtro", q == null ? "" : q);
		return "proveedores/lista";
	}

	@GetMapping("/nuevo")
	public String nuevo(Model model) {
		prepararFormulario(model, new ProveedorForm(), false, null, null,
				proveedorService.listarPersonasDisponibles());
		return "proveedores/formulario";
	}

	@PostMapping
	public String registrar(@Valid @ModelAttribute("proveedorForm") ProveedorForm form,
			BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
		List<Persona> personas = proveedorService.listarPersonasDisponibles();
		if (personas.isEmpty()) {
			bindingResult.rejectValue("idPersona", "idPersona.sinPersonas",
					"No hay personas disponibles para registrar como proveedor.");
		}
		if (bindingResult.hasErrors()) {
			prepararFormulario(model, form, false, null, null, personas);
			return "proveedores/formulario";
		}

		try {
			proveedorService.registrar(form);
		} catch (PersonaNoEncontradaException | ProveedorDuplicadoException exception) {
			bindingResult.rejectValue("idPersona", "idPersona.invalida", exception.getMessage());
			prepararFormulario(model, form, false, null, null, personas);
			return "proveedores/formulario";
		}

		redirectAttributes.addFlashAttribute("successMessage", "Proveedor registrado correctamente.");
		return "redirect:/modulos/proveedores";
	}

	@GetMapping("/{idProveedor}/editar")
	public String editar(@PathVariable Long idProveedor, Model model) {
		Proveedor proveedor = buscarProveedor(idProveedor);
		ProveedorForm form = new ProveedorForm();
		form.setIdPersona(proveedor.getIdPersonaProveedor());
		form.setEmail(proveedor.getEmail() == null ? null : proveedor.getEmail().stripTrailing());
		prepararFormulario(model, form, true, idProveedor, proveedor, List.of());
		return "proveedores/formulario";
	}

	@PostMapping("/{idProveedor}/editar")
	public String actualizar(@PathVariable Long idProveedor,
			@Valid @ModelAttribute("proveedorForm") ProveedorForm form,
			BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
		Proveedor proveedor = buscarProveedor(idProveedor);
		if (bindingResult.hasErrors()) {
			prepararFormulario(model, form, true, idProveedor, proveedor, List.of());
			return "proveedores/formulario";
		}

		proveedorService.actualizar(idProveedor, form);
		redirectAttributes.addFlashAttribute("successMessage", "Correo del proveedor actualizado correctamente.");
		return "redirect:/modulos/proveedores";
	}

	@PostMapping("/{idProveedor}/eliminar")
	public String eliminar(@PathVariable Long idProveedor, RedirectAttributes redirectAttributes) {
		try {
			proveedorService.eliminar(idProveedor);
			redirectAttributes.addFlashAttribute("successMessage", "Proveedor eliminado correctamente.");
		} catch (ProveedorRelacionadoException exception) {
			redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
		} catch (NoSuchElementException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return "redirect:/modulos/proveedores";
	}

	private Proveedor buscarProveedor(Long idProveedor) {
		try {
			return proveedorService.buscarPorId(idProveedor);
		} catch (NoSuchElementException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
	}

	private void prepararFormulario(Model model, ProveedorForm form, boolean modoEdicion,
			Long idProveedor, Proveedor proveedor, List<Persona> personas) {
		model.addAttribute("proveedorForm", form);
		model.addAttribute("modoEdicion", modoEdicion);
		model.addAttribute("proveedorId", idProveedor);
		model.addAttribute("proveedor", proveedor);
		model.addAttribute("personas", personas);
		model.addAttribute("formAction", modoEdicion
				? "/modulos/proveedores/" + idProveedor + "/editar"
				: "/modulos/proveedores");
	}
}