package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.entity.ConfiguracionVacaciones;
import com.gestionrestaurante.back.repository.ConfiguracionVacacionesRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectura/escritura de los dias de vacaciones anuales por defecto. Es una fila unica (id=1,
 * sembrada por V20) para que el Admin pueda cambiarla sin redesplegar el backend - ver
 * VacacionesService.obtenerDiasVacacionesAnuales, que la consulta cuando un empleado no
 * tiene un valor propio. Mismo patron que ConfiguracionUbicacionService.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfiguracionVacacionesService {

    private static final Long ID_UNICO = 1L;

    private final ConfiguracionVacacionesRepository configuracionVacacionesRepository;

    public ConfiguracionVacaciones obtener() {
        return configuracionVacacionesRepository.findById(ID_UNICO)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta la fila de configuracion_vacaciones (id=1); revisar que la migracion V20 se aplico"));
    }

    @Transactional
    public ConfiguracionVacaciones actualizar(Integer diasAnualesDefecto, Long adminId) {
        ConfiguracionVacaciones configuracion = obtener();
        configuracion.setDiasAnualesDefecto(diasAnualesDefecto);
        configuracion.setUpdatedAt(LocalDateTime.now());
        configuracion.setActualizadoPorAdminId(adminId);
        return configuracionVacacionesRepository.save(configuracion);
    }
}
