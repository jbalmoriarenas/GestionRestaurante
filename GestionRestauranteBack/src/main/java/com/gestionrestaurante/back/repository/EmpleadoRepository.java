package com.gestionrestaurante.back.repository;

import com.gestionrestaurante.back.entity.Empleado;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    @Query("""
            select e from Empleado e
            join fetch e.tipoUsuario
            where e.email = :email
            """)
    Optional<Empleado> findByEmailWithTipoUsuario(@Param("email") String email);

    /**
     * Bloqueo pessimista de la fila del empleado (SELECT ... FOR UPDATE). Usado por
     * VacacionesService.solicitar para serializar, por empleado, el chequeo de dias
     * disponibles seguido del INSERT de la solicitud: la regla de negocio es una suma
     * agregada (no una unicidad simple como en fichajes), asi que no hay un indice unico
     * que la proteja por si solo — se serializa a nivel de fila en su lugar.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Empleado e where e.id = :id")
    Optional<Empleado> findByIdConBloqueo(@Param("id") Long id);

    boolean existsByEmail(String email);

    boolean existsByDniNie(String dniNie);

    @Override
    @EntityGraph(attributePaths = "tipoUsuario")
    Optional<Empleado> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "tipoUsuario")
    Page<Empleado> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "tipoUsuario")
    Page<Empleado> findByActivo(boolean activo, Pageable pageable);

    /**
     * Usado por NominaService.listar para cruzar en memoria "todos los empleados activos"
     * con las nominas del periodo consultado (back/fase3-1-back.md): el estado "pendiente"
     * de un empleado se calcula por ausencia de fila, no se persiste.
     */
    List<Empleado> findByActivoTrueOrderByApellidosAscNombreAsc();

    /**
     * Usado por NominaService.procesarZip para matchear cada entrada del ZIP con su empleado
     * por convencion de nombre de fichero ({dniNie}_{periodoAnio}-{periodoMes}.pdf,
     * back/fase3-2-back.md). Solo empleados activos son candidatos.
     */
    Optional<Empleado> findByDniNieAndActivoTrue(String dniNie);

    /**
     * Usado por RecuperacionPasswordService.solicitar (back/fase6-1-back.md): un empleado dado
     * de baja no debe poder generarse un enlace de recuperacion nuevo, mismo criterio ya
     * aplicado en AuthService.login (.filter(Empleado::isActivo)).
     */
    Optional<Empleado> findByEmailAndActivoTrue(String email);
}
