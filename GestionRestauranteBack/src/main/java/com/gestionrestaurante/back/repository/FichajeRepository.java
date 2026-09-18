package com.gestionrestaurante.back.repository;

import com.gestionrestaurante.back.entity.Fichaje;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FichajeRepository extends JpaRepository<Fichaje, Long> {

    Optional<Fichaje> findByEmpleado_IdAndHoraSalidaIsNull(Long empleadoId);

    Page<Fichaje> findByEmpleado_IdOrderByFechaDescHoraEntradaDesc(Long empleadoId, Pageable pageable);

    List<Fichaje> findByEmpleado_IdAndFechaOrderByHoraEntradaAsc(Long empleadoId, LocalDate fecha);

    List<Fichaje> findByEmpleado_IdAndFechaBetween(Long empleadoId, LocalDate desde, LocalDate hasta);

    /** empleadoId nulo = todos los empleados (vista Admin sin filtrar por uno concreto). */
    @Query("""
            select f from Fichaje f
            where (:empleadoId is null or f.empleado.id = :empleadoId)
              and f.fecha between :desde and :hasta
            order by f.fecha desc, f.horaEntrada desc
            """)
    Page<Fichaje> findByFiltros(
            @Param("empleadoId") Long empleadoId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            Pageable pageable);
}
