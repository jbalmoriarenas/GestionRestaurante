package com.gestionrestaurante.back.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fila única (id siempre 1, forzado por CHECK en BBDD) con la ubicación y el radio de
 * tolerancia del restaurante contra los que se valida la geovalla al fichar entrada.
 * Editable en caliente por el Admin (ver ConfiguracionUbicacionService), nunca por
 * application.properties.
 */
@Entity
@Table(name = "configuracion_ubicacion")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionUbicacion {

    @Id
    private Long id;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(name = "radio_metros", nullable = false)
    private Integer radioMetros;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "actualizado_por_admin_id")
    private Long actualizadoPorAdminId;
}
