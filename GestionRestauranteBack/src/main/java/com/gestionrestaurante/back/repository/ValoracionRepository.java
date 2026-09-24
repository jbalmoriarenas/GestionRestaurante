package com.gestionrestaurante.back.repository;

import com.gestionrestaurante.back.entity.Valoracion;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ValoracionRepository extends JpaRepository<Valoracion, Long> {

    /** Clave de upsert en cada sincronizacion (back/fase4-1-back.md): evita duplicar una
     * reseña ya sincronizada en ejecuciones sucesivas del job periodico. */
    Optional<Valoracion> findByGoogleReviewId(String googleReviewId);

    Page<Valoracion> findByPuntuacion(Integer puntuacion, Pageable pageable);

    /** {@code null} si no hay ninguna reseña sincronizada todavia (AVG sobre tabla vacia),
     * nunca un error - back/fase4-2-back.md. */
    @Query("SELECT AVG(v.puntuacion) FROM Valoracion v")
    BigDecimal mediaPuntuacion();

    /** Fila {@code [puntuacion, cantidad]} por cada puntuacion con al menos una reseña; las
     * puntuaciones sin ninguna reseña no aparecen (el servicio las completa a 0). */
    @Query("SELECT v.puntuacion, COUNT(v) FROM Valoracion v GROUP BY v.puntuacion")
    List<Object[]> contarPorPuntuacion();
}
