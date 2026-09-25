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
 * Conexion OAuth con la cuenta de Google Business Profile del restaurante
 * (bbdd/fase4-1-bbdd.md). Fila unica ({@code id = 1}, mismo patron que
 * {@code configuracion_ubicacion}) - a diferencia de esa tabla, sin fila significa
 * "Desconectado" (no se siembra por defecto), mismo criterio de "ausencia de fila = estado
 * por defecto" que {@code nominas} (estado PENDIENTE sin fila).
 */
@Entity
@Table(name = "conexion_google_business")
@Getter
@Setter
@NoArgsConstructor
public class ConexionGoogleBusiness {

    @Id
    private Long id;

    @Column(name = "location_resource_name", nullable = false)
    private String locationResourceName;

    @Column(name = "nombre_ubicacion")
    private String nombreUbicacion;

    /** Refresh token de Google cifrado con {@link com.gestionrestaurante.back.service.TokenEncryptionService}
     * - nunca se guarda en claro, ver back/fase4-1-back.md. */
    @Column(name = "refresh_token_cifrado", nullable = false, length = 1000)
    private String refreshTokenCifrado;

    @Column(name = "fecha_conexion", nullable = false)
    private LocalDateTime fechaConexion;

    @Column(name = "conectado_por_admin_id", nullable = false)
    private Long conectadoPorAdminId;

    @Column(name = "fecha_ultima_sincronizacion")
    private LocalDateTime fechaUltimaSincronizacion;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;
}
