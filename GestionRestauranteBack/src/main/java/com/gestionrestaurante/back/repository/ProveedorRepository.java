package com.gestionrestaurante.back.repository;

import com.gestionrestaurante.back.entity.Proveedor;
import com.gestionrestaurante.back.entity.TipoProveedor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    List<Proveedor> findByTipoOrderByNombreAsc(TipoProveedor tipo);

    List<Proveedor> findAllByOrderByNombreAsc();

    Optional<Proveedor> findByCif(String cif);
}
