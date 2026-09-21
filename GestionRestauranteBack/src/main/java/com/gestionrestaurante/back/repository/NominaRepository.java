package com.gestionrestaurante.back.repository;

import com.gestionrestaurante.back.entity.Nomina;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NominaRepository extends JpaRepository<Nomina, Long> {

    /**
     * Version con el empleado cargado en la misma consulta, para NominaService.enviar (que
     * necesita el email/nombre del empleado destinatario). El findById normal (sin
     * EntityGraph) basta para descargar(), que nunca toca nomina.getEmpleado() — no tiene
     * sentido pagar ese JOIN de mas en esa ruta.
     */
    @EntityGraph(attributePaths = "empleado")
    Optional<Nomina> findWithEmpleadoById(Long id);

    Optional<Nomina> findByEmpleado_IdAndPeriodoMesAndPeriodoAnio(Long empleadoId, Integer periodoMes, Integer periodoAnio);

    /**
     * @EntityGraph trae el empleado en la misma consulta: NominaService.listar cruza estas
     * filas en memoria con la lista de empleados activos del periodo (evita N+1 al construir
     * el DTO de listado, mismo patron que SolicitudVacacionesRepository).
     */
    @EntityGraph(attributePaths = "empleado")
    List<Nomina> findByPeriodoMesAndPeriodoAnio(Integer periodoMes, Integer periodoAnio);
}
