package com.facturacion.sistemaweb.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.facturacion.sistemaweb.model.Factura;

public interface FacturaRepository extends JpaRepository<Factura,Long> {
    String FILTRO=" FROM Factura f JOIN f.empleado e LEFT JOIN Persona c ON c.idPersona=f.idPersona "
        + "WHERE (:empleado IS NULL OR e.idEmpleado=:empleado) "
        + "AND (:desde IS NULL OR f.fecha>=:desde) AND (:hasta IS NULL OR f.fecha<:hasta) "
        + "AND (:q='' OR str(f.idFactura) LIKE CONCAT('%',:q,'%') "
        + "OR c.cedulaPersona LIKE CONCAT('%',:q,'%') OR LOWER(c.nombresPersona) LIKE LOWER(CONCAT('%',:q,'%')) "
        + "OR LOWER(c.apellidosPersona) LIKE LOWER(CONCAT('%',:q,'%')))";
    @EntityGraph(attributePaths={"empleado","empleado.persona"})
    @Query(value="SELECT f"+FILTRO+" ORDER BY f.fecha DESC,f.idFactura DESC",countQuery="SELECT COUNT(f)"+FILTRO)
    Page<Factura> buscar(@Param("empleado") Long empleado,@Param("q") String q,
        @Param("desde") LocalDateTime desde,@Param("hasta") LocalDateTime hasta,Pageable page);
    @EntityGraph(attributePaths={"empleado","empleado.persona"})
    @Query("SELECT f FROM Factura f WHERE f.idFactura=:id") Optional<Factura> completo(@Param("id") Long id);
}
