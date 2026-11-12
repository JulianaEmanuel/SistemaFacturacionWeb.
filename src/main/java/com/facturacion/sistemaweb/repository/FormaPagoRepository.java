package com.facturacion.sistemaweb.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.facturacion.sistemaweb.model.FormaPago;
public interface FormaPagoRepository extends JpaRepository<FormaPago,Integer>{List<FormaPago> findByActivaTrueOrderByNombreAsc();}
