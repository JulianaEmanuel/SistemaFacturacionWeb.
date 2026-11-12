package com.facturacion.sistemaweb.repository;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.facturacion.sistemaweb.model.*;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {
    @Query("SELECT e FROM Empleado e JOIN FETCH e.persona p JOIN FETCH e.usuario u "
        + "WHERE LOWER(TRIM(p.cedulaPersona)) LIKE LOWER(CONCAT('%', :q, '%')) "
        + "OR LOWER(TRIM(p.nombresPersona)) LIKE LOWER(CONCAT('%', :q, '%')) "
        + "OR LOWER(TRIM(p.apellidosPersona)) LIKE LOWER(CONCAT('%', :q, '%')) "
        + "OR LOWER(TRIM(u.nombreUsuario)) LIKE LOWER(CONCAT('%', :q, '%')) "
        + "ORDER BY p.apellidosPersona, p.nombresPersona, e.idEmpleado")
    List<Empleado> buscar(@Param("q") String q);

    @Query("SELECT e FROM Empleado e JOIN FETCH e.persona JOIN FETCH e.usuario WHERE e.idEmpleado = :id")
    Optional<Empleado> buscarCompleto(@Param("id") Long id);

    @Query("SELECT p FROM Persona p WHERE NOT EXISTS (SELECT e.idEmpleado FROM Empleado e "
        + "WHERE e.persona = p AND (:id IS NULL OR e.idEmpleado <> :id)) ORDER BY p.apellidosPersona, p.nombresPersona")
    List<Persona> personasDisponibles(@Param("id") Long id);

    @Query("SELECT u FROM Usuario u WHERE (u.activo = true OR EXISTS "
        + "(SELECT e.idEmpleado FROM Empleado e WHERE e.idEmpleado = :id AND e.usuario = u)) "
        + "AND NOT EXISTS (SELECT e.idEmpleado FROM Empleado e WHERE e.usuario = u "
        + "AND (:id IS NULL OR e.idEmpleado <> :id)) ORDER BY u.nombreUsuario")
    List<Usuario> usuariosDisponibles(@Param("id") Long id);

    @Query("SELECT COUNT(e) > 0 FROM Empleado e WHERE e.persona.idPersona = :persona "
        + "AND (:id IS NULL OR e.idEmpleado <> :id)")
    boolean personaAsignada(@Param("persona") Long persona, @Param("id") Long id);

    @Query("SELECT COUNT(e) > 0 FROM Empleado e WHERE e.usuario.idUsuario = :usuario "
        + "AND (:id IS NULL OR e.idEmpleado <> :id)")
    boolean usuarioAsignado(@Param("usuario") Long usuario, @Param("id") Long id);

    @Query(value = "SELECT CAST(CASE WHEN EXISTS (SELECT 1 FROM dbo.Factura WHERE ID_EMPLEADO = :id) THEN 1 ELSE 0 END AS bit)", nativeQuery = true)
    boolean tieneFacturas(@Param("id") Long id);
}
