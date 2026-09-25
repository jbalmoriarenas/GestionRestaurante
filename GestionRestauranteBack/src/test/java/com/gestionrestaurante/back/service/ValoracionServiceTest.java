package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.gestionrestaurante.back.dto.ResumenValoracionesDTO;
import com.gestionrestaurante.back.repository.ValoracionRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** back/fase4-2-back.md: el resumen siempre trae las 5 puntuaciones, a 0 las que no tengan
 * ninguna reseña, y media null (no un error) sin filas. */
@ExtendWith(MockitoExtension.class)
class ValoracionServiceTest {

    @Mock
    private ValoracionRepository valoracionRepository;

    private ValoracionService service;

    @BeforeEach
    void setUp() {
        service = new ValoracionService(valoracionRepository);
    }

    @Test
    void obtenerResumen_sinResenasDevuelveMediaNulaYDistribucionACero() {
        when(valoracionRepository.mediaPuntuacion()).thenReturn(null);
        when(valoracionRepository.contarPorPuntuacion()).thenReturn(List.of());

        ResumenValoracionesDTO resumen = service.obtenerResumen();

        assertThat(resumen.media()).isNull();
        assertThat(resumen.total()).isZero();
        assertThat(resumen.distribucion()).hasSize(5).containsValues(0, 0, 0, 0, 0);
    }

    @Test
    void obtenerResumen_conResenasCalculaMediaTotalYDistribucion() {
        when(valoracionRepository.mediaPuntuacion()).thenReturn(new BigDecimal("4.3333333"));
        when(valoracionRepository.contarPorPuntuacion())
                .thenReturn(List.of(new Object[] {5, 2L}, new Object[] {3, 1L}));

        ResumenValoracionesDTO resumen = service.obtenerResumen();

        assertThat(resumen.media()).isEqualByComparingTo("4.33");
        assertThat(resumen.total()).isEqualTo(3);
        assertThat(resumen.distribucion().get(5)).isEqualTo(2);
        assertThat(resumen.distribucion().get(3)).isEqualTo(1);
        assertThat(resumen.distribucion().get(1)).isZero();
    }
}
