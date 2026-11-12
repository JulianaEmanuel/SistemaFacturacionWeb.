package com.facturacion.sistemaweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ui.Model;
import com.facturacion.sistemaweb.service.InicioService;

@Controller
public class HomeController {
	private final ObjectProvider<InicioService> inicio;
	public HomeController(ObjectProvider<InicioService> inicio) {this.inicio=inicio;}

	@GetMapping("/")
	public String home(Model model) {
		var servicio=inicio.getIfAvailable();
		if(servicio!=null) {
			try {model.addAttribute("panel",servicio.consultar());}
			catch(org.springframework.dao.DataAccessException ex) {model.addAttribute("panelAviso","No se pudieron consultar los indicadores. Vuelve a intentarlo.");}
		} else model.addAttribute("panelAviso","Los indicadores estarán disponibles al conectar SQL Server.");
		return "index";
	}
}
