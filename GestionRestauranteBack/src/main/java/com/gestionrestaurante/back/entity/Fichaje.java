package com.gestionrestaurante.back.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fichajes")
@Getter
@Setter
@NoArgsConstructor
public class Fichaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_entrada", nullable = false)
    private LocalDateTime horaEntrada;

    @Column(name = "hora_salida")
    private LocalDateTime horaSalida;

    @Column(name = "horas_calculadas", precision = 5, scale = 2)
    private BigDecimal horasCalculadas;

    @Column(name = "lat_entrada", precision = 9, scale = 6)
    private BigDecimal latEntrada;

    @Column(name = "lng_entrada", precision = 9, scale = 6)
    private BigDecimal lngEntrada;

    @Column(length = 255)
    private String observaciones;

    @Column(name = "corregido_por_admin", nullable = false)
    private boolean corregidoPorAdmin;

    @Column(name = "corregido_por_admin_id")
    private Long corregidoPorAdminId;

    @Column(name = "fecha_correccion")
    private LocalDateTime fechaCorreccion;
}
