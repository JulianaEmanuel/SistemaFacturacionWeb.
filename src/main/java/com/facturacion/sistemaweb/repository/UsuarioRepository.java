package com.facturacion.sistemaweb.repository;

import java.util.Optional;
import org.springframework.data.repository.Repository;
import com.facturacion.sistemaweb.model.Usuario;

/** No expone operaciones de escritura de usuarios. */
public interface UsuarioRepository extends Repository<Usuario, Long> {
    Optional<Usuario> findById(Long id);
}
