package com.gestionrestaurante.back.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reseña de Google ya sincronizada (bbdd/fase4-1-bbdd.md). Una fila por reseña, identificada
 * por {@code googleReviewId} (clave de upsert en cada sincronizacion, back/fase4-1-back.md) -
 * el front nunca consulta la API de Google directamente, siempre lee esta tabla.
 */
@Entity
@Table(name = "valoraciones")
@Getter
@Setter
@NoArgsConstructor
public class Valoracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_review_id", nullable = false, length = 150)
    private String googleReviewId;

    @Column(name = "autor_nombre", nullable = false, length = 150)
    private String autorNombre;

    @Column(name = "autor_foto_url", length = 500)
    private String autorFotoUrl;

    @Column(nullable = false)
    private Integer puntuacion;

    @Column(columnDefinition = "TEXT")
    private String comentario;

    @Column(name = "fecha_publicacion", nullable = false)
    private LocalDateTime fechaPublicacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Column(name = "fecha_sincronizacion", nullable = false)
    private LocalDateTime fechaSincronizacion;
}
