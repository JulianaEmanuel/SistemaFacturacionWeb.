package com.facturacion.sistemaweb.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ModuleController {

	private static final Map<String, String> MODULE_NAMES = Map.of(
			"clientes", "Clientes",
			"productos", "Productos",
			"proveedores", "Proveedores",
			"empleados", "Empleados",
			"facturas", "Facturas",
			"usuarios", "Usuarios",
			"reportes", "Reportes");

	@GetMapping("/modulos/{slug}")
	public String showModule(@PathVariable String slug, Model model) {
		String moduleName = MODULE_NAMES.get(slug);
		if (moduleName == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}

		model.addAttribute("moduleName", moduleName);
		model.addAttribute("activePage", slug);
		return "module-placeholder";
	}
}