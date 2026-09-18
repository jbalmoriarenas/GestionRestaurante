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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "empleados")
@Getter
@Setter
@NoArgsConstructor
public class Empleado {

    /**
     * Mismo valor que el DEFAULT de la columna en BBDD (V14). Hibernate siempre envia un
     * valor explicito en el INSERT, por lo que el DEFAULT de la columna nunca se aplica al
     * crear un empleado desde Java; hay que fijarlo aqui a mano.
     */
    public static final short DIAS_VACACIONES_ANUALES_DEFECTO = 23;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 150)
    private String apellidos;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "dni_nie", nullable = false, unique = true, length = 15)
    private String dniNie;

    @Column(length = 20)
    private String telefono;

    @Column(length = 100)
    private String puesto;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDate fechaAlta;

    @Column(name = "fecha_baja")
    private LocalDate fechaBaja;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "dias_vacaciones_anuales", nullable = false)
    private short diasVacacionesAnuales;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_usuario_id", nullable = false)
    private TipoUsuario tipoUsuario;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;
}
