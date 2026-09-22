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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Patron Entidad-Atributo-Valor (bbdd/fase2-3-bbdd.md): campos que el OCR detecta en una
 * factura y no existen como columna fija de {@link Factura}. Asi es como "los campos nuevos
 * se crean automaticamente" sin necesitar ALTER TABLE en caliente ni redesplegar el backend.
 */
@Entity
@Table(name = "factura_campos_dinamicos")
@Getter
@Setter
@NoArgsConstructor
public class FacturaCampoDinamico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_id", nullable = false)
    private Factura factura;

    @Column(name = "nombre_campo", nullable = false, length = 100)
    private String nombreCampo;

    @Column(length = 500)
    private String valor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;
}
