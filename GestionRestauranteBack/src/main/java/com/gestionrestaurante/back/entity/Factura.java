package com.gestionrestaurante.back.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Fase 2 (Facturas): datos de una factura de proveedor o de suministro, dada de alta a mano
 * (fase2-1) o via OCR (fase2-3). {@code estadoPago} (negocio: si ya se ha pagado) y
 * {@code estadoExtraccion} (pipeline de OCR) son conceptos independientes, no confundir
 * (bbdd/fase2-1-bbdd.md, bbdd/fase2-3-bbdd.md).
 */
@Entity
@Table(name = "facturas")
@Getter
@Setter
@NoArgsConstructor
public class Factura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Column(name = "numero_factura", length = 50)
    private String numeroFactura;

    @Column(nullable = false, length = 255)
    private String producto;

    @Column(name = "fecha_factura", nullable = false)
    private LocalDate fechaFactura;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "importe_base", precision = 10, scale = 2)
    private BigDecimal importeBase;

    @Column(precision = 10, scale = 2)
    private BigDecimal iva;

    @Column(name = "importe_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal importeTotal;

    @Column(nullable = false)
    private Integer mes;

    @Column(nullable = false)
    private Integer anio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago", nullable = false, length = 20)
    private EstadoPago estadoPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_extraccion", nullable = false, length = 20)
    private EstadoExtraccion estadoExtraccion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_extraidos_raw")
    private String datosExtraidosRaw;

    @Column(name = "ruta_archivo", nullable = false, length = 255)
    private String rutaArchivo;

    @Column(name = "nombre_archivo_original", nullable = false, length = 255)
    private String nombreArchivoOriginal;

    @Column(name = "subida_por_admin_id", nullable = false)
    private Long subidaPorAdminId;

    @Column(name = "fecha_subida", nullable = false)
    private LocalDateTime fechaSubida;
}
