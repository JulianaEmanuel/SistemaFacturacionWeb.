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

import com.facturacion.sistemaweb.model.Producto;
import com.facturacion.sistemaweb.model.ProductoForm;
import com.facturacion.sistemaweb.model.Proveedor;
import com.facturacion.sistemaweb.repository.ProveedorRepository;
import com.facturacion.sistemaweb.service.ProductoRelacionadoException;
import com.facturacion.sistemaweb.service.ProductoService;
import com.facturacion.sistemaweb.service.ProveedorNoEncontradoException;

import jakarta.validation.Valid;

@Controller
@Profile("sqlserver")
@RequestMapping("/modulos/productos")
public class ProductoController {

	private final ProductoService productoService;
	private final ProveedorRepository proveedorRepository;

	public ProductoController(ProductoService productoService, ProveedorRepository proveedorRepository) {
		this.productoService = productoService;
		this.proveedorRepository = proveedorRepository;
	}

	@GetMapping
	public String listar(@RequestParam(required = false) String q, Model model) {
		model.addAttribute("productos", productoService.listar(q));
		model.addAttribute("filtro", q == null ? "" : q);
		model.addAttribute("hayProveedores", proveedorRepository.count() > 0);
		return "productos/lista";
	}

	@GetMapping("/nuevo")
	public String nuevo(Model model) {
		prepararFormulario(model, new ProductoForm(), false, null);
		return "productos/formulario";
	}

	@PostMapping
	public String registrar(@Valid @ModelAttribute("productoForm") ProductoForm form,
			BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
		List<Proveedor> proveedores = cargarProveedores(model);
		if (proveedores.isEmpty()) {
			bindingResult.rejectValue("idProveedor", "idProveedor.sinProveedores",
					"No hay proveedores registrados. Registra un proveedor antes de crear productos.");
		}
		if (bindingResult.hasErrors()) {
			prepararFormulario(model, form, false, null, proveedores);
			return "productos/formulario";
		}

		try {
			productoService.registrar(form);
		} catch (ProveedorNoEncontradoException exception) {
			bindingResult.rejectValue("idProveedor", "idProveedor.invalido", exception.getMessage());
			prepararFormulario(model, form, false, null, proveedores);
			return "productos/formulario";
		}

		redirectAttributes.addFlashAttribute("successMessage", "Producto registrado correctamente.");
		return "redirect:/modulos/productos";
	}

	@GetMapping("/{idProducto}/editar")
	public String editar(@PathVariable Long idProducto, Model model) {
		Producto producto = buscarProducto(idProducto);
		ProductoForm form = convertirFormulario(producto);
		prepararFormulario(model, form, true, idProducto);
		return "productos/formulario";
	}

	@PostMapping("/{idProducto}/editar")
	public String actualizar(@PathVariable Long idProducto,
			@Valid @ModelAttribute("productoForm") ProductoForm form,
			BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
		buscarProducto(idProducto);
		List<Proveedor> proveedores = cargarProveedores(model);
		if (proveedores.isEmpty()) {
			bindingResult.rejectValue("idProveedor", "idProveedor.sinProveedores",
					"No hay proveedores registrados para asignar al producto.");
		}
		if (bindingResult.hasErrors()) {
			prepararFormulario(model, form, true, idProducto, proveedores);
			return "productos/formulario";
		}

		try {
			productoService.actualizar(idProducto, form);
		} catch (ProveedorNoEncontradoException exception) {
			bindingResult.rejectValue("idProveedor", "idProveedor.invalido", exception.getMessage());
			prepararFormulario(model, form, true, idProducto, proveedores);
			return "productos/formulario";
		}

		redirectAttributes.addFlashAttribute("successMessage", "Producto actualizado correctamente.");
		return "redirect:/modulos/productos";
	}

	@PostMapping("/{idProducto}/eliminar")
	public String eliminar(@PathVariable Long idProducto, RedirectAttributes redirectAttributes) {
		try {
			productoService.eliminar(idProducto);
			redirectAttributes.addFlashAttribute("successMessage", "Producto eliminado correctamente.");
		} catch (ProductoRelacionadoException exception) {
			redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
		}
		return "redirect:/modulos/productos";
	}

	private Producto buscarProducto(Long idProducto) {
		try {
			return productoService.buscarPorId(idProducto);
		} catch (NoSuchElementException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
	}

	private ProductoForm convertirFormulario(Producto producto) {
		ProductoForm form = new ProductoForm();
		form.setIdProveedor(producto.getProveedor().getIdProveedor());
		form.setNombreProducto(producto.getNombreProducto().stripTrailing());
		form.setPrecioUnitario(numeroParaFormulario(producto.getPrecioUnitario()));
		form.setIvaProducto(numeroParaFormulario(producto.getIvaProducto()));
		return form;
	}

	private java.math.BigDecimal numeroParaFormulario(java.math.BigDecimal valor) {
		var numero = valor.stripTrailingZeros();
		return numero.setScale(Math.max(0, numero.scale()));
	}

	private List<Proveedor> cargarProveedores(Model model) {
		List<Proveedor> proveedores = proveedorRepository.listarParaSelector();
		model.addAttribute("proveedores", proveedores);
		return proveedores;
	}

	private void prepararFormulario(Model model, ProductoForm form, boolean modoEdicion, Long idProducto) {
		prepararFormulario(model, form, modoEdicion, idProducto, cargarProveedores(model));
	}

	private void prepararFormulario(Model model, ProductoForm form, boolean modoEdicion,
			Long idProducto, List<Proveedor> proveedores) {
		model.addAttribute("productoForm", form);
		model.addAttribute("modoEdicion", modoEdicion);
		model.addAttribute("productoId", idProducto);
		model.addAttribute("proveedores", proveedores);
		model.addAttribute("hayProveedores", !proveedores.isEmpty());
		model.addAttribute("formAction", modoEdicion
				? "/modulos/productos/" + idProducto + "/editar"
				: "/modulos/productos");
	}
}
