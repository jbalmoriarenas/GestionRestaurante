package com.gestionrestaurante.back.repository;

import com.gestionrestaurante.back.entity.TipoUsuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoUsuarioRepository extends JpaRepository<TipoUsuario, Long> {

    Optional<TipoUsuario> findByCodigo(String codigo);

    List<TipoUsuario> findAllByOrderByNombreAsc();
}
