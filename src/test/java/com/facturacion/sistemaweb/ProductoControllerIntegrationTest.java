package com.facturacion.sistemaweb;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import com.facturacion.sistemaweb.controller.ProductoController;
import com.facturacion.sistemaweb.model.Persona;
import com.facturacion.sistemaweb.model.Producto;
import com.facturacion.sistemaweb.model.Proveedor;
import com.facturacion.sistemaweb.repository.ProveedorRepository;
import com.facturacion.sistemaweb.service.ProductoService;
import com.facturacion.sistemaweb.service.ProductoRelacionadoException;

@WebMvcTest(controllers = ProductoController.class)
@ActiveProfiles("sqlserver")
@org.springframework.security.test.context.support.WithMockUser
@org.springframework.context.annotation.Import({com.facturacion.sistemaweb.security.SecurityConfig.class,CapturaVistasConfig.class})
class ProductoControllerIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.springframework.security.core.userdetails.UserDetailsService autenticacionSimulada;
    // Estas pruebas cubren las acciones del administrador; la matriz de permisos se prueba en PermisosModulosSecurityTest.
    @org.springframework.test.context.bean.override.mockito.MockitoBean(name="adminAccess") com.facturacion.sistemaweb.security.AdminAccess adminAccess;
    @org.junit.jupiter.api.BeforeEach void comoAdministrador() { org.mockito.Mockito.when(adminAccess.allowed(org.mockito.ArgumentMatchers.any())).thenReturn(true); }

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductoService productoService;

	@MockitoBean
	private ProveedorRepository proveedorRepository;

	@Test
	void listaProductosShowsSupplierNameAndRealFields() throws Exception {
		Producto producto = producto();
		when(productoService.listar(null)).thenReturn(List.of(producto));
		when(proveedorRepository.count()).thenReturn(1L);

		mockMvc.perform(get("/modulos/productos"))
				.andExpect(status().isOk())
				.andExpect(view().name("productos/lista"))
				.andExpect(content().string(containsString("Nombre real proveedor")))
				.andExpect(content().string(containsString("12.3456")))
				.andExpect(content().string(containsString("12.34")));
	}

	@Test
	void createFormUsesRealSupplierNames() throws Exception {
		Proveedor proveedor = proveedor();
		when(proveedorRepository.listarParaSelector()).thenReturn(List.of(proveedor));

		mockMvc.perform(get("/modulos/productos/nuevo"))
				.andExpect(status().isOk())
				.andExpect(view().name("productos/formulario"))
				.andExpect(content().string(containsString("Nombre real proveedor")))
				.andExpect(content().string(containsString("IVA (%)")));
	}

	@Test
	void createIsDisabledWhenThereAreNoSuppliers() throws Exception {
		when(proveedorRepository.listarParaSelector()).thenReturn(List.of());

		mockMvc.perform(get("/modulos/productos/nuevo"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("No hay proveedores registrados")))
				.andExpect(content().string(containsString("disabled")));
	}

	@Test
	void searchRoutePassesNameFilterToService() throws Exception {
		when(productoService.listar("cafe")).thenReturn(List.of());
		when(proveedorRepository.count()).thenReturn(0L);

		mockMvc.perform(get("/modulos/productos").param("q", "cafe"))
				.andExpect(status().isOk())
				.andExpect(view().name("productos/lista"))
				.andExpect(content().string(containsString("Buscar por nombre")));
	}

	@Test
	void invalidPostReturnsFormWithoutCallingProductService() throws Exception {
		Proveedor proveedor = proveedor();
		when(proveedorRepository.listarParaSelector()).thenReturn(List.of(proveedor));

		mockMvc.perform(post("/modulos/productos").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
					.param("idProveedor", "1")
					.param("nombreProducto", "")
					.param("precioUnitario", "-1")
					.param("ivaProducto", "0"))
				.andExpect(status().isOk())
				.andExpect(view().name("productos/formulario"))
				.andExpect(content().string(containsString("El nombre del producto es obligatorio.")));
	}

	@Test
	void editRouteLoadsExistingProductIntoForm() throws Exception {
		Producto producto = producto();
		Proveedor proveedor = proveedor();
		when(productoService.buscarPorId(41L)).thenReturn(producto);
		when(proveedorRepository.listarParaSelector()).thenReturn(List.of(proveedor));

		mockMvc.perform(get("/modulos/productos/41/editar"))
				.andExpect(status().isOk())
				.andExpect(view().name("productos/formulario"))
				.andExpect(content().string(containsString("value=\"Nombre real producto\"")));
	}

	@Test
	void validRegistrationDelegatesToServiceWithoutRealDatabaseWrite() throws Exception {
		Proveedor proveedor = proveedor();
		when(proveedorRepository.listarParaSelector()).thenReturn(List.of(proveedor));
		Producto respuesta = producto();
		when(productoService.registrar(org.mockito.ArgumentMatchers.any())).thenReturn(respuesta);

		mockMvc.perform(post("/modulos/productos").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
					.param("idProveedor", "1")
					.param("nombreProducto", "Producto nuevo")
					.param("precioUnitario", "12.3456")
					.param("ivaProducto", "12.34"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/modulos/productos"));

		verify(productoService).registrar(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void validUpdateDelegatesToServiceWithoutRealDatabaseWrite() throws Exception {
		Producto existing = producto();
		Proveedor proveedor = proveedor();
		when(productoService.buscarPorId(41L)).thenReturn(existing);
		when(proveedorRepository.listarParaSelector()).thenReturn(List.of(proveedor));
		when(productoService.actualizar(org.mockito.ArgumentMatchers.eq(41L), org.mockito.ArgumentMatchers.any()))
				.thenReturn(existing);

		mockMvc.perform(post("/modulos/productos/41/editar").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
					.param("idProveedor", "1")
					.param("nombreProducto", "Producto editado")
					.param("precioUnitario", "12.3456")
					.param("ivaProducto", "12.34"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/modulos/productos"));

		verify(productoService).actualizar(org.mockito.ArgumentMatchers.eq(41L), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void deleteRouteShowsRelatedInvoiceMessageWithoutDeleting() throws Exception {
		doThrow(new ProductoRelacionadoException("No se puede eliminar: el producto tiene detalles de factura asociados."))
				.when(productoService).eliminar(41L);

		mockMvc.perform(post("/modulos/productos/41/eliminar").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/modulos/productos"))
				.andExpect(flash().attribute("errorMessage",
						"No se puede eliminar: el producto tiene detalles de factura asociados."));

		verify(productoService).eliminar(41L);
	}

	private Producto producto() {
		Producto producto = new Producto();
		producto.setIdProducto(41L);
		producto.setProveedor(proveedor());
		producto.setNombreProducto("Nombre real producto       ");
		producto.setPrecioUnitario(new BigDecimal("12.3456"));
		producto.setIvaProducto(new BigDecimal("12.34"));
		return producto;
	}

	private Proveedor proveedor() {
		Persona persona = mock(Persona.class);
		when(persona.getNombresPersona()).thenReturn("Nombre real proveedor         ");
		when(persona.getApellidosPersona()).thenReturn("Apellido real                ");
		Proveedor proveedor = mock(Proveedor.class);
		when(proveedor.getIdProveedor()).thenReturn(1L);
		when(proveedor.getPersona()).thenReturn(persona);
		return proveedor;
	}
}