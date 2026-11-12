package com.facturacion.sistemaweb.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facturacion.sistemaweb.model.Persona;

public interface PersonaRepository extends JpaRepository<Persona, Long> {

	@Query("SELECT p FROM Persona p "
			+ "WHERE LOWER(TRIM(p.cedulaPersona)) LIKE LOWER(CONCAT('%', :filtro, '%')) "
			+ "OR LOWER(TRIM(p.nombresPersona)) LIKE LOWER(CONCAT('%', :filtro, '%')) "
			+ "OR LOWER(TRIM(p.apellidosPersona)) LIKE LOWER(CONCAT('%', :filtro, '%')) "
			+ "ORDER BY p.apellidosPersona, p.nombresPersona")
	List<Persona> buscar(@Param("filtro") String filtro);

	@Query("SELECT p FROM Persona p ORDER BY p.apellidosPersona, p.nombresPersona")
	List<Persona> listarOrdenadas();

	@Query(value = "SELECT CAST(CASE WHEN EXISTS (SELECT 1 FROM dbo.Persona p "
			+ "WHERE LTRIM(RTRIM(p.CEDULA_PERSONA)) = :cedula) THEN 1 ELSE 0 END AS bit)", nativeQuery = true)
	boolean existeCedula(@Param("cedula") String cedula);

	@Query(value = "SELECT CAST(CASE WHEN EXISTS (SELECT 1 FROM dbo.Persona p "
			+ "WHERE LTRIM(RTRIM(p.CEDULA_PERSONA)) = :cedula AND p.ID_PERSONA <> :idPersona) "
			+ "THEN 1 ELSE 0 END AS bit)", nativeQuery = true)
	boolean existeCedulaEnOtraPersona(@Param("cedula") String cedula, @Param("idPersona") Long idPersona);

	@Query(value = "SELECT CAST(CASE WHEN EXISTS (SELECT 1 FROM dbo.Factura f WHERE f.ID_PERSONA = :idPersona) "
			+ "THEN 1 ELSE 0 END AS bit)", nativeQuery = true)
	boolean tieneFacturas(@Param("idPersona") Long idPersona);

	@Query(value = "SELECT CAST(CASE WHEN EXISTS (SELECT 1 FROM dbo.Empleado e "
			+ "WHERE e.ID_PERSONA_EMPLEADO = :idPersona) THEN 1 ELSE 0 END AS bit)", nativeQuery = true)
	boolean esEmpleado(@Param("idPersona") Long idPersona);

	@Query(value = "SELECT CAST(CASE WHEN EXISTS (SELECT 1 FROM dbo.Proveedor p "
			+ "WHERE p.ID_PERSONA_PROVEEDOR = :idPersona) THEN 1 ELSE 0 END AS bit)", nativeQuery = true)
	boolean esProveedor(@Param("idPersona") Long idPersona);
}