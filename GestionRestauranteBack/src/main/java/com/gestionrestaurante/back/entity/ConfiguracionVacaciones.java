package com.gestionrestaurante.back.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fila única (id siempre 1, forzado por CHECK en BBDD) con los días de vacaciones anuales
 * por defecto. Editable en caliente por el Admin (ver ConfiguracionVacacionesService), nunca
 * por application.properties. Mismo patrón que ConfiguracionUbicacion.
 */
@Entity
@Table(name = "configuracion_vacaciones")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionVacaciones {

    @Id
    private Long id;

    @Column(name = "dias_anuales_defecto", nullable = false)
    private Integer diasAnualesDefecto;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "actualizado_por_admin_id")
    private Long actualizadoPorAdminId;
}
