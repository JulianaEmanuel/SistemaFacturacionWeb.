package com.facturacion.sistemaweb.repository;

import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.facturacion.sistemaweb.model.DetalleFactura;

public interface DetalleFacturaRepository extends JpaRepository<DetalleFactura,Long> {
    @Query("SELECT d FROM DetalleFactura d JOIN FETCH d.producto WHERE d.factura.idFactura=:id ORDER BY d.idDetalle")
    List<DetalleFactura> deFactura(@Param("id") Long id);
}
