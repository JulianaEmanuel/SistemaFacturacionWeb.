package com.facturacion.sistemaweb.controller;

import java.util.NoSuchElementException;

import org.springframework.context.annotation.Profile;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.facturacion.sistemaweb.model.Persona;
import com.facturacion.sistemaweb.model.PersonaForm;
import com.facturacion.sistemaweb.service.CedulaDuplicadaException;
import com.facturacion.sistemaweb.service.PersonaRelacionadaException;
import com.facturacion.sistemaweb.service.PersonaService;

import jakarta.validation.Valid;

@Controller
@Profile("sqlserver")
@RequestMapping("/modulos/clientes")
public class ClienteController {

	private final PersonaService personaService;

	public ClienteController(PersonaService personaService) {
		this.personaService = personaService;
	}

	@GetMapping
	public String listar(@RequestParam(required = false) String q, Model model) {
		model.addAttribute("clientes", personaService.listar(q));
		model.addAttribute("filtro", q == null ? "" : q);
		return "clientes/lista";
	}

	@GetMapping("/nuevo")
	public String nuevo(Model model) {
		prepararFormulario(model, new PersonaForm(), false, null);
		return "clientes/formulario";
	}

	@PostMapping
	public String registrar(@org.springframework.validation.annotation.Validated(PersonaForm.Nuevo.class) @ModelAttribute("personaForm") PersonaForm form,
			BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			prepararFormulario(model, form, false, null);
			return "clientes/formulario";
		}

        if(!personaService.contactosDisponibles()) {
            model.addAttribute("errorMessage","Falta aplicar la migración de contactos. No se guardaron datos.");
            prepararFormulario(model,form,false,null);return "clientes/formulario";
        }
		try {
			personaService.registrar(form);
		} catch (CedulaDuplicadaException exception) {
			bindingResult.rejectValue("cedulaPersona", "cedulaPersona.duplicada", exception.getMessage());
			prepararFormulario(model, form, false, null);
			return "clientes/formulario";
		}
		redirectAttributes.addFlashAttribute("successMessage", "Cliente registrado correctamente.");
		return "redirect:/modulos/clientes";
	}

	@GetMapping("/{idPersona}/editar")
	public String editar(@PathVariable Long idPersona, Model model) {
		Persona persona = buscarPersona(idPersona);
		PersonaForm form = new PersonaForm();
		form.setCedulaPersona(persona.getCedulaPersona().stripTrailing());
        form.setDireccionPersona(persona.getDireccionPersona());form.setTelefonoPersona(persona.getTelefonoPersona());form.setCorreoPersona(persona.getCorreoPersona());
		form.setNombresPersona(persona.getNombresPersona().stripTrailing());
		form.setApellidosPersona(persona.getApellidosPersona().stripTrailing());
		form.setFechaNacimientoPersona(persona.getFechaNacimientoPersona() == null
				? null
				: persona.getFechaNacimientoPersona().toLocalDate());
		prepararFormulario(model, form, true, idPersona);
		return "clientes/formulario";
	}

	@PostMapping("/{idPersona}/editar")
	public String actualizar(@PathVariable Long idPersona,
			@Valid @ModelAttribute("personaForm") PersonaForm form,
			BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			prepararFormulario(model, form, true, idPersona);
			return "clientes/formulario";
		}

		buscarPersona(idPersona);
		try {
			personaService.actualizar(idPersona, form);
		} catch (CedulaDuplicadaException exception) {
			bindingResult.rejectValue("cedulaPersona", "cedulaPersona.duplicada", exception.getMessage());
			prepararFormulario(model, form, true, idPersona);
			return "clientes/formulario";
		}
		redirectAttributes.addFlashAttribute("successMessage", "Cliente actualizado correctamente.");
		return "redirect:/modulos/clientes";
	}

	@PostMapping("/{idPersona}/eliminar")
	public String eliminar(@PathVariable Long idPersona, RedirectAttributes redirectAttributes) {
		try {
			personaService.eliminar(idPersona);
			redirectAttributes.addFlashAttribute("successMessage", "Cliente eliminado correctamente.");
		} catch (PersonaRelacionadaException exception) {
			redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
		}
		return "redirect:/modulos/clientes";
	}

	private Persona buscarPersona(Long idPersona) {
		try {
			return personaService.buscarPorId(idPersona);
		} catch (NoSuchElementException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
	}

	private void prepararFormulario(Model model, PersonaForm form, boolean modoEdicion, Long idPersona) {
		model.addAttribute("personaForm", form);
        model.addAttribute("contactosDisponibles",personaService.contactosDisponibles());
		model.addAttribute("modoEdicion", modoEdicion);
		model.addAttribute("personaId", idPersona);
		model.addAttribute("formAction", modoEdicion
				? "/modulos/clientes/" + idPersona + "/editar"
				: "/modulos/clientes");
	}
}