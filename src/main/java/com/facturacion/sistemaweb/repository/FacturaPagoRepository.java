package com.facturacion.sistemaweb.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.facturacion.sistemaweb.model.FacturaPago;
public interface FacturaPagoRepository extends JpaRepository<FacturaPago,Long>{Optional<FacturaPago> findByIdFactura(Long id);List<FacturaPago> findByIdFacturaIn(Collection<Long> ids);}
