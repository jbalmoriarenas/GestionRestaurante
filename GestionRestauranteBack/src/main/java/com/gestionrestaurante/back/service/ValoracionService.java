package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.dto.ResumenValoracionesDTO;
import com.gestionrestaurante.back.dto.ValoracionDTO;
import com.gestionrestaurante.back.entity.Valoracion;
import com.gestionrestaurante.back.repository.ValoracionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listado y resumen de solo lectura de las reseñas ya sincronizadas (back/fase4-2-back.md).
 * Nunca llama a la API de Google - lee exclusivamente de {@code valoraciones}, coherente con
 * la interpretacion de "tiempo real" fijada en back/fase4-1-back.md.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ValoracionService {

    private final ValoracionRepository valoracionRepository;

    public Page<ValoracionDTO> listar(Integer puntuacionOpcional, Pageable pageable) {
        Page<Valoracion> pagina = puntuacionOpcional != null
                ? valoracionRepository.findByPuntuacion(puntuacionOpcional, pageable)
                : valoracionRepository.findAll(pageable);
        return pagina.map(ValoracionService::toDTO);
    }

    public ResumenValoracionesDTO obtenerResumen() {
        BigDecimal media = valoracionRepository.mediaPuntuacion();
        BigDecimal mediaRedondeada = media != null ? media.setScale(2, RoundingMode.HALF_UP) : null;

        Map<Integer, Integer> distribucion = new LinkedHashMap<>();
        for (int puntuacion = 1; puntuacion <= 5; puntuacion++) {
            distribucion.put(puntuacion, 0);
        }
        int total = 0;
        for (Object[] fila : valoracionRepository.contarPorPuntuacion()) {
            int puntuacion = (Integer) fila[0];
            int cantidad = ((Long) fila[1]).intValue();
            distribucion.put(puntuacion, cantidad);
            total += cantidad;
        }

        return new ResumenValoracionesDTO(mediaRedondeada, total, distribucion);
    }

    private static ValoracionDTO toDTO(Valoracion valoracion) {
        return new ValoracionDTO(valoracion.getId(), valoracion.getAutorNombre(), valoracion.getAutorFotoUrl(),
                valoracion.getPuntuacion(), valoracion.getComentario(), valoracion.getFechaPublicacion(),
                valoracion.getFechaActualizacion());
    }
}
