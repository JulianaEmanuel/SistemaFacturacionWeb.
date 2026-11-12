package com.facturacion.sistemaweb.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facturacion.sistemaweb.model.Persona;
import com.facturacion.sistemaweb.model.Proveedor;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

	@EntityGraph(attributePaths = "persona")
	@Query("SELECT p FROM Proveedor p ORDER BY p.persona.apellidosPersona, p.persona.nombresPersona")
	List<Proveedor> listarParaSelector();

	@Query("SELECT p FROM Proveedor p JOIN FETCH p.persona "
			+ "WHERE LOWER(TRIM(p.persona.cedulaPersona)) LIKE LOWER(CONCAT('%', :filtro, '%')) "
			+ "OR LOWER(TRIM(p.persona.nombresPersona)) LIKE LOWER(CONCAT('%', :filtro, '%')) "
			+ "OR LOWER(TRIM(p.persona.apellidosPersona)) LIKE LOWER(CONCAT('%', :filtro, '%')) "
			+ "ORDER BY p.persona.apellidosPersona, p.persona.nombresPersona")
	List<Proveedor> buscar(@Param("filtro") String filtro);

	@Query("SELECT p FROM Proveedor p JOIN FETCH p.persona "
			+ "ORDER BY p.persona.apellidosPersona, p.persona.nombresPersona")
	List<Proveedor> listarOrdenados();

	@Query("SELECT p FROM Proveedor p JOIN FETCH p.persona WHERE p.idProveedor = :idProveedor")
	java.util.Optional<Proveedor> buscarPorIdConPersona(@Param("idProveedor") Long idProveedor);

	@Query("SELECT p FROM Persona p WHERE NOT EXISTS "
			+ "(SELECT pr.idProveedor FROM Proveedor pr WHERE pr.persona.idPersona = p.idPersona) "
			+ "ORDER BY p.apellidosPersona, p.nombresPersona")
	List<Persona> listarPersonasDisponibles();

	@Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Proveedor p "
			+ "WHERE p.persona.idPersona = :idPersona")
	boolean existePorPersona(@Param("idPersona") Long idPersona);

	@Query(value = "SELECT CAST(CASE WHEN EXISTS (SELECT 1 FROM dbo.Producto p "
			+ "WHERE p.ID_PROVEEDOR = :idProveedor) THEN 1 ELSE 0 END AS bit)", nativeQuery = true)
	boolean tieneProductos(@Param("idProveedor") Long idProveedor);
}