package com.gestionrestaurante.back.repository;

import com.gestionrestaurante.back.entity.Permiso;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermisoRepository extends JpaRepository<Permiso, Long> {

    @Query("""
            select p from Permiso p
            join fetch p.vista
            join fetch p.tipoUsuario
            where p.tipoUsuario.codigo = :tipoUsuarioCodigo
            """)
    List<Permiso> findByTipoUsuarioCodigo(@Param("tipoUsuarioCodigo") String tipoUsuarioCodigo);
}
