package com.gestionrestaurante.back.repository;

import com.gestionrestaurante.back.entity.ConexionGoogleBusiness;
import org.springframework.data.jpa.repository.JpaRepository;

/** Fila unica ({@code id = 1}); en la practica el unico acceso real es
 * {@code findById(1L)} (convencion del propio servicio, no una restriccion de este
 * repositorio) - ver back/fase4-1-back.md. */
public interface ConexionGoogleBusinessRepository extends JpaRepository<ConexionGoogleBusiness, Long> {
}
