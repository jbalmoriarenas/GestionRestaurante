package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.entity.ConfiguracionUbicacion;
import com.gestionrestaurante.back.repository.ConfiguracionUbicacionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectura/escritura de la ubicacion y radio de la geovalla del restaurante. Es una fila
 * unica (id=1, sembrada por V13) para que el Admin pueda cambiarla sin redesplegar el
 * backend - ver FichajeService.iniciar, que la consulta en cada fichaje de entrada.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfiguracionUbicacionService {

    private static final Long ID_UNICO = 1L;

    private final ConfiguracionUbicacionRepository configuracionUbicacionRepository;

    public ConfiguracionUbicacion obtener() {
        return configuracionUbicacionRepository.findById(ID_UNICO)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta la fila de configuracion_ubicacion (id=1); revisar que la migracion V13 se aplico"));
    }

    /**
     * Sin uso todavia en esta fase (Fase 1.1): lo consumira el endpoint de edicion de
     * ubicacion del Admin, que se añade en Fase 1.3 (back/fase1-3-back.md). Se declara ya
     * aqui porque vive en el mismo servicio que {@link #obtener()}.
     */
    @Transactional
    public ConfiguracionUbicacion actualizar(BigDecimal lat, BigDecimal lng, Integer radioMetros, Long adminId) {
        ConfiguracionUbicacion configuracion = obtener();
        configuracion.setLat(lat);
        configuracion.setLng(lng);
        configuracion.setRadioMetros(radioMetros);
        configuracion.setUpdatedAt(LocalDateTime.now());
        configuracion.setActualizadoPorAdminId(adminId);
        return configuracionUbicacionRepository.save(configuracion);
    }
}
