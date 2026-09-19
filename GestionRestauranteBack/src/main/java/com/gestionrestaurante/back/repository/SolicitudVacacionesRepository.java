package com.gestionrestaurante.back.repository;

import com.gestionrestaurante.back.entity.EstadoSolicitud;
import com.gestionrestaurante.back.entity.SolicitudVacaciones;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudVacacionesRepository extends JpaRepository<SolicitudVacaciones, Long> {

    List<SolicitudVacaciones> findByEmpleado_IdAndEstadoInAndFechaInicioBetween(
            Long empleadoId, List<EstadoSolicitud> estados, LocalDate desde, LocalDate hasta);

    Page<SolicitudVacaciones> findByEmpleado_IdOrderByFechaSolicitudDesc(Long empleadoId, Pageable pageable);

    Page<SolicitudVacaciones> findByEmpleado_IdAndEstadoOrderByFechaSolicitudDesc(
            Long empleadoId, EstadoSolicitud estado, Pageable pageable);

    /**
     * @EntityGraph trae el empleado en la misma consulta (evita N+1 al construir el nombre
     * del empleado en cada fila de la bandeja global, ver back/fase1-4-back.md).
     */
    @EntityGraph(attributePaths = "empleado")
    Page<SolicitudVacaciones> findByEstadoOrderByFechaSolicitudAsc(EstadoSolicitud estado, Pageable pageable);

    /** Solapamiento clasico de rangos: [fechaInicio, fechaFin] se cruza con [:inicio, :fin] si ninguno termina antes de que empiece el otro. */
    @Query("""
            select s from SolicitudVacaciones s
            where s.empleado.id = :empleadoId
              and s.estado in :estados
              and s.fechaInicio <= :fin
              and s.fechaFin >= :inicio
            """)
    List<SolicitudVacaciones> findSolapadas(
            @Param("empleadoId") Long empleadoId,
            @Param("estados") List<EstadoSolicitud> estados,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);
}
