package com.gestionrestaurante.back.repository;

import com.gestionrestaurante.back.entity.FacturaCampoDinamico;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacturaCampoDinamicoRepository extends JpaRepository<FacturaCampoDinamico, Long> {

    List<FacturaCampoDinamico> findByFactura_Id(Long facturaId);

    Optional<FacturaCampoDinamico> findByFactura_IdAndNombreCampo(Long facturaId, String nombreCampo);
}
