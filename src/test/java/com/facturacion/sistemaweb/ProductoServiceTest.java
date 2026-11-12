package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facturacion.sistemaweb.model.Producto;
import com.facturacion.sistemaweb.model.ProductoForm;
import com.facturacion.sistemaweb.model.Proveedor;
import com.facturacion.sistemaweb.repository.ProductoRepository;
import com.facturacion.sistemaweb.repository.ProveedorRepository;
import com.facturacion.sistemaweb.service.ProductoRelacionadoException;
import com.facturacion.sistemaweb.service.ProductoService;
import com.facturacion.sistemaweb.service.ProveedorNoEncontradoException;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

	@Mock
	private ProductoRepository productoRepository;

	@Mock
	private ProveedorRepository proveedorRepository;

	@Mock
	private Proveedor proveedor;

	private ProductoService productoService;

	@BeforeEach
	void setUp() {
		productoService = new ProductoService(productoRepository, proveedorRepository);
	}

	@Test
	void rejectsProveedorThatDoesNotExistWithoutSaving() {
		when(proveedorRepository.findById(10L)).thenReturn(Optional.empty());

		assertThrows(ProveedorNoEncontradoException.class, () -> productoService.registrar(validForm()));
		verify(productoRepository, never()).save(any(Producto.class));
	}

	@Test
	void keepsDecimalValuesWhenRegisteringWithExistingProveedor() {
		when(proveedorRepository.findById(10L)).thenReturn(Optional.of(proveedor));
		when(productoRepository.save(any(Producto.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Producto saved = productoService.registrar(validForm());

		assertEquals(new BigDecimal("123.4567"), saved.getPrecioUnitario());
		assertEquals(new BigDecimal("12.34"), saved.getIvaProducto());
		assertEquals("Producto de prueba", saved.getNombreProducto());
		assertEquals(proveedor, saved.getProveedor());
	}

	@Test
	void preventsDeletingProductoReferencedByInvoiceDetailWithoutCallingDelete() {
		Producto producto = new Producto();
		producto.setIdProducto(7L);
		when(productoRepository.findById(7L)).thenReturn(Optional.of(producto));
		when(productoRepository.tieneDetallesFactura(7L)).thenReturn(true);

		assertThrows(ProductoRelacionadoException.class, () -> productoService.eliminar(7L));
		verify(productoRepository, never()).delete(any(Producto.class));
	}

	@Test
	void deletesProductoWithoutInvoiceDetails() {
		Producto producto = new Producto();
		producto.setIdProducto(8L);
		when(productoRepository.findById(8L)).thenReturn(Optional.of(producto));
		when(productoRepository.tieneDetallesFactura(8L)).thenReturn(false);

		productoService.eliminar(8L);

		verify(productoRepository).delete(producto);
		verify(productoRepository).flush();
	}

	private ProductoForm validForm() {
		ProductoForm form = new ProductoForm();
		form.setIdProveedor(10L);
		form.setNombreProducto("Producto de prueba");
		form.setPrecioUnitario(new BigDecimal("123.4567"));
		form.setIvaProducto(new BigDecimal("12.34"));
		return form;
	}
}