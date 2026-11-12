package com.facturacion.sistemaweb.repository;

import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.facturacion.sistemaweb.model.TipoUsuario;

public interface TipoUsuarioRepository extends JpaRepository<TipoUsuario, Integer> {
    @Query("SELECT t FROM TipoUsuario t WHERE LOWER(t.nombre) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(t.descripcion) LIKE LOWER(CONCAT('%',:q,'%')) ORDER BY t.nombre")
    List<TipoUsuario> buscar(@Param("q") String q);
    @Query(value="SELECT COUNT_BIG(*) FROM dbo.TipoUsuario WHERE TIPO_USUARIO = :nombre AND (:id IS NULL OR ID_TIPO_USUARIO <> :id)", nativeQuery=true)
    long duplicados(@Param("nombre") String nombre, @Param("id") Integer id);
}
