package com.gestionrestaurante.back.repository;

import com.gestionrestaurante.back.entity.DiaFestivo;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaFestivoRepository extends JpaRepository<DiaFestivo, Long> {

    List<DiaFestivo> findByFechaBetween(LocalDate desde, LocalDate hasta);
}
