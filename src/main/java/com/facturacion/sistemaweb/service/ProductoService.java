package com.facturacion.sistemaweb.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.facturacion.sistemaweb.model.Producto;
import com.facturacion.sistemaweb.model.ProductoForm;
import com.facturacion.sistemaweb.model.Proveedor;
import com.facturacion.sistemaweb.repository.ProductoRepository;
import com.facturacion.sistemaweb.repository.ProveedorRepository;

@Service
@Profile("sqlserver")
public class ProductoService {

	private final ProductoRepository productoRepository;
	private final ProveedorRepository proveedorRepository;

	public ProductoService(ProductoRepository productoRepository, ProveedorRepository proveedorRepository) {
		this.productoRepository = productoRepository;
		this.proveedorRepository = proveedorRepository;
	}

	@Transactional(readOnly = true)
	public List<Producto> listar(String filtro) {
		if (filtro == null || filtro.isBlank()) {
			return productoRepository.listarOrdenados();
		}
		return productoRepository.buscarPorNombre(filtro.strip());
	}

	@Transactional(readOnly = true)
	public Producto buscarPorId(Long idProducto) {
		return productoRepository.findById(idProducto)
				.orElseThrow(() -> new NoSuchElementException("No se encontró el producto solicitado."));
	}

	@Transactional
	public Producto registrar(ProductoForm form) {
		Producto producto = new Producto();
		aplicarFormulario(producto, form);
		return productoRepository.save(producto);
	}

	@Transactional
	public Producto actualizar(Long idProducto, ProductoForm form) {
		Producto producto = buscarPorId(idProducto);
		aplicarFormulario(producto, form);
		return productoRepository.save(producto);
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public void eliminar(Long idProducto) {
		Producto producto = buscarPorId(idProducto);
		if (productoRepository.tieneDetallesFactura(idProducto)) {
			throw new ProductoRelacionadoException("No se puede eliminar: el producto tiene detalles de factura asociados.");
		}

		try {
			productoRepository.delete(producto);
			productoRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new ProductoRelacionadoException(
					"No se puede eliminar porque existen registros relacionados con este producto.", exception);
		}
	}

	private void aplicarFormulario(Producto producto, ProductoForm form) {
		Proveedor proveedor = proveedorRepository.findById(form.getIdProveedor())
				.orElseThrow(() -> new ProveedorNoEncontradoException("El proveedor seleccionado ya no existe."));
		producto.setProveedor(proveedor);
		producto.setNombreProducto(form.getNombreProducto().strip());
		producto.setPrecioUnitario(form.getPrecioUnitario());
		producto.setIvaProducto(form.getIvaProducto());
	}
}