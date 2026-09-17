package com.gestionrestaurante.back.repository;

import com.gestionrestaurante.back.entity.Empleado;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    @Query("""
            select e from Empleado e
            join fetch e.tipoUsuario
            where e.email = :email
            """)
    Optional<Empleado> findByEmailWithTipoUsuario(@Param("email") String email);

    boolean existsByEmail(String email);

    boolean existsByDniNie(String dniNie);

    @Override
    @EntityGraph(attributePaths = "tipoUsuario")
    Page<Empleado> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "tipoUsuario")
    Page<Empleado> findByActivo(boolean activo, Pageable pageable);
}
