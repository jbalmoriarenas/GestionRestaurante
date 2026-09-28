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
 * Fila única (id siempre 1, forzado por CHECK en BBDD) con el nombre y el logo del negocio,
 * mostrados en la cabecera de la aplicación a cualquier usuario autenticado. Editable solo por
 * Admin (ver ConfiguracionEmpresaService), mismo patrón de fila única que ConfiguracionUbicacion.
 */
@Entity
@Table(name = "configuracion_empresa")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionEmpresa {

    @Id
    private Long id;

    @Column(name = "nombre_empresa", nullable = false, length = 150)
    private String nombreEmpresa;

    @Column(name = "logo_ruta")
    private String logoRuta;

    @Column(name = "logo_nombre_original")
    private String logoNombreOriginal;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "actualizado_por_admin_id")
    private Long actualizadoPorAdminId;
}
