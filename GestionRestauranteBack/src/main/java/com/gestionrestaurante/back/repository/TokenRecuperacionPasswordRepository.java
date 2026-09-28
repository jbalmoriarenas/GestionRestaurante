package com.gestionrestaurante.back.repository;

import com.gestionrestaurante.back.entity.TokenRecuperacionPassword;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenRecuperacionPasswordRepository extends JpaRepository<TokenRecuperacionPassword, Long> {

    Optional<TokenRecuperacionPassword> findByTokenHash(String tokenHash);

    /**
     * Usado por RecuperacionPasswordService.solicitar para invalidar (borrar) los tokens
     * anteriores no usados del empleado antes de generar uno nuevo (back/fase6-1-back.md).
     */
    List<TokenRecuperacionPassword> findByEmpleado_IdAndFechaUsoIsNull(Long empleadoId);
}
