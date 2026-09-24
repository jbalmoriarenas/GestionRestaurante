package com.gestionrestaurante.back.repository;

import com.gestionrestaurante.back.entity.EstadoPago;
import com.gestionrestaurante.back.entity.Factura;
import com.gestionrestaurante.back.entity.TipoProveedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * A diferencia de NominaRepository (cruce en memoria, fase3-1-back.md), las facturas se
 * paginan a nivel de BBDD desde el principio: no estan acotadas por una entidad finita
 * (como los empleados), se acumulan indefinidamente con los anios (back/fase2-1-back.md).
 */
public interface FacturaRepository extends JpaRepository<Factura, Long> {

    /**
     * {@code tipo} es obligatorio (identifica la pestana activa del front); el resto de
     * parametros son opcionales. Reutilizada tambien por la exportacion
     * (back/fase2-2-back.md) pasando {@link Pageable#unpaged()}, para no duplicar la logica
     * de filtros.
     */
    @Query("""
            select f from Factura f
            join f.proveedor p
            where p.tipo = :tipo
              and (:proveedorId is null or p.id = :proveedorId)
              and (:estadoPago is null or f.estadoPago = :estadoPago)
              and (:mes is null or f.mes = :mes)
              and (:anio is null or f.anio = :anio)
            order by f.fechaImpresion desc, f.id desc
            """)
    Page<Factura> findByFiltros(
            @Param("tipo") TipoProveedor tipo,
            @Param("proveedorId") Long proveedorId,
            @Param("estadoPago") EstadoPago estadoPago,
            @Param("mes") Integer mes,
            @Param("anio") Integer anio,
            Pageable pageable);
}
