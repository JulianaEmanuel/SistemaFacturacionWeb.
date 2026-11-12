package com.facturacion.sistemaweb.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.facturacion.sistemaweb.model.FacturaCliente;
public interface FacturaClienteRepository extends JpaRepository<FacturaCliente,Long>{}
