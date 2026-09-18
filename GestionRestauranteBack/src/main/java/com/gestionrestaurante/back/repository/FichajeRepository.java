package com.gestionrestaurante.back.repository;

import com.gestionrestaurante.back.entity.Fichaje;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FichajeRepository extends JpaRepository<Fichaje, Long> {

    Optional<Fichaje> findByEmpleado_IdAndHoraSalidaIsNull(Long empleadoId);

    Page<Fichaje> findByEmpleado_IdOrderByFechaDescHoraEntradaDesc(Long empleadoId, Pageable pageable);

    List<Fichaje> findByEmpleado_IdAndFechaOrderByHoraEntradaAsc(Long empleadoId, LocalDate fecha);
}
