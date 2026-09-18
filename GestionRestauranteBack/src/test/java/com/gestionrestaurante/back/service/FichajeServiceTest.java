package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gestionrestaurante.back.dto.FichajeResumenDiaDTO;
import com.gestionrestaurante.back.dto.FichajeResumenMesDTO;
import com.gestionrestaurante.back.dto.FichajeResumenSemanaDTO;
import com.gestionrestaurante.back.entity.Empleado;
import com.gestionrestaurante.back.entity.Fichaje;
import com.gestionrestaurante.back.exception.EmpleadoNotFoundException;
import com.gestionrestaurante.back.exception.FichajeNotFoundException;
import com.gestionrestaurante.back.exception.RangoFechasInvalidoException;
import com.gestionrestaurante.back.repository.EmpleadoRepository;
import com.gestionrestaurante.back.repository.FichajeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FichajeServiceTest {

    private static final Long EMPLEADO_ID = 1L;

    @Mock
    private FichajeRepository fichajeRepository;
    @Mock
    private EmpleadoRepository empleadoRepository;
    @Mock
    private ConfiguracionUbicacionService configuracionUbicacionService;
    @Mock
    private VacacionesService vacacionesService;

    private FichajeService fichajeService;

    @BeforeEach
    void setUp() {
        fichajeService = new FichajeService(
                fichajeRepository, empleadoRepository, configuracionUbicacionService, vacacionesService);
        ReflectionTestUtils.setField(fichajeService, "horasJornadaDefault", BigDecimal.valueOf(8));
    }

    @Test
    void obtenerUmbralDiario_sinJornadaPropiaUsaElUmbralGlobal() {
        Empleado empleado = new Empleado();

        assertThat(fichajeService.obtenerUmbralDiario(empleado)).isEqualByComparingTo("8");
    }

    @Test
    void obtenerUmbralDiario_conJornadaPropiaLaUsaEnVezDelGlobal() {
        Empleado empleado = new Empleado();
        empleado.setHorasJornadaDiaria(BigDecimal.valueOf(6));

        assertThat(fichajeService.obtenerUmbralDiario(empleado)).isEqualByComparingTo("6");
    }

    @Test
    void obtenerResumenDiario_marcaCumpleMinimoSegunElUmbralDelEmpleado() {
        Empleado empleado = empleadoConJornada(6);
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));

        LocalDate desde = LocalDate.of(2026, 1, 5);
        LocalDate hasta = LocalDate.of(2026, 1, 6);
        Fichaje diaCorto = fichajeConFechaYHoras(desde, "5.00");
        Fichaje diaCompleto = fichajeConFechaYHoras(hasta, "6.00");
        when(fichajeRepository.findByEmpleado_IdAndFechaBetween(EMPLEADO_ID, desde, hasta))
                .thenReturn(List.of(diaCorto, diaCompleto));

        List<FichajeResumenDiaDTO> resumen = fichajeService.obtenerResumenDiario(EMPLEADO_ID, desde, hasta);

        assertThat(resumen).hasSize(2);
        assertThat(resumen.get(0).fecha()).isEqualTo(desde);
        assertThat(resumen.get(0).cumpleMinimo()).isFalse();
        assertThat(resumen.get(1).fecha()).isEqualTo(hasta);
        assertThat(resumen.get(1).cumpleMinimo()).isTrue();
    }

    @Test
    void obtenerResumenSemanal_marcaCumpleMinimoConElUmbralSemanalDelEmpleado() {
        Empleado empleado = empleadoConJornada(6); // umbral semanal = 6 * 5 = 30
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));

        // lunes 2026-01-05 a viernes 2026-01-09, misma semana ISO
        LocalDate desde = LocalDate.of(2026, 1, 5);
        LocalDate hasta = LocalDate.of(2026, 1, 9);
        List<Fichaje> fichajes = List.of(
                fichajeConFechaYHoras(LocalDate.of(2026, 1, 5), "6.00"),
                fichajeConFechaYHoras(LocalDate.of(2026, 1, 6), "6.00"),
                fichajeConFechaYHoras(LocalDate.of(2026, 1, 7), "6.00"),
                fichajeConFechaYHoras(LocalDate.of(2026, 1, 8), "6.00"),
                fichajeConFechaYHoras(LocalDate.of(2026, 1, 9), "5.00"));
        when(fichajeRepository.findByEmpleado_IdAndFechaBetween(EMPLEADO_ID, desde, hasta)).thenReturn(fichajes);

        List<FichajeResumenSemanaDTO> resumen = fichajeService.obtenerResumenSemanal(EMPLEADO_ID, desde, hasta);

        assertThat(resumen).hasSize(1);
        assertThat(resumen.get(0).inicioSemana()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(resumen.get(0).finSemana()).isEqualTo(LocalDate.of(2026, 1, 11));
        assertThat(resumen.get(0).horasCalculadas()).isEqualByComparingTo("29.00");
        assertThat(resumen.get(0).cumpleMinimo()).isFalse();
    }

    @Test
    void obtenerResumenMensual_usaDiasLaborablesDelMesParaElUmbral() {
        Empleado empleado = empleadoConJornada(6);
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));

        LocalDate desdeAnio = LocalDate.of(2026, 1, 1);
        LocalDate hastaAnio = LocalDate.of(2026, 12, 31);
        when(fichajeRepository.findByEmpleado_IdAndFechaBetween(EMPLEADO_ID, desdeAnio, hastaAnio))
                .thenReturn(List.of(fichajeConFechaYHoras(LocalDate.of(2026, 1, 15), "120.00")));
        when(vacacionesService.calcularDiasLaborables(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(20);

        List<FichajeResumenMesDTO> resumen = fichajeService.obtenerResumenMensual(EMPLEADO_ID, 2026);

        assertThat(resumen).hasSize(1);
        assertThat(resumen.get(0).mes()).isEqualTo(1);
        assertThat(resumen.get(0).anio()).isEqualTo(2026);
        // umbral mensual = 6 * 20 = 120 -> 120 horas cumple justo el minimo
        assertThat(resumen.get(0).cumpleMinimo()).isTrue();
    }

    @Test
    void obtenerResumenDiario_empleadoInexistenteLanzaEmpleadoNotFound() {
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fichajeService.obtenerResumenDiario(
                EMPLEADO_ID, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .isInstanceOf(EmpleadoNotFoundException.class);
    }

    @Test
    void corregir_recalculaHorasYDejaTraza() {
        Fichaje fichaje = new Fichaje();
        fichaje.setId(10L);
        when(fichajeRepository.findById(10L)).thenReturn(Optional.of(fichaje));
        when(fichajeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime entrada = LocalDateTime.of(2026, 1, 5, 9, 0);
        LocalDateTime salida = LocalDateTime.of(2026, 1, 5, 17, 0);

        fichajeService.corregir(10L, 99L, entrada, salida, "corregido a mano");

        assertThat(fichaje.getHoraEntrada()).isEqualTo(entrada);
        assertThat(fichaje.getHoraSalida()).isEqualTo(salida);
        assertThat(fichaje.getHorasCalculadas()).isEqualByComparingTo("8.00");
        assertThat(fichaje.isCorregidoPorAdmin()).isTrue();
        assertThat(fichaje.getCorregidoPorAdminId()).isEqualTo(99L);
        assertThat(fichaje.getFechaCorreccion()).isNotNull();
        assertThat(fichaje.getObservaciones()).isEqualTo("corregido a mano");
    }

    @Test
    void corregir_horaSalidaAntesDeEntradaLanzaRangoInvalido() {
        LocalDateTime entrada = LocalDateTime.of(2026, 1, 5, 17, 0);
        LocalDateTime salida = LocalDateTime.of(2026, 1, 5, 9, 0);

        assertThatThrownBy(() -> fichajeService.corregir(10L, 99L, entrada, salida, null))
                .isInstanceOf(RangoFechasInvalidoException.class);
    }

    @Test
    void corregir_fichajeInexistenteLanzaFichajeNotFound() {
        when(fichajeRepository.findById(10L)).thenReturn(Optional.empty());
        LocalDateTime entrada = LocalDateTime.of(2026, 1, 5, 9, 0);
        LocalDateTime salida = LocalDateTime.of(2026, 1, 5, 17, 0);

        assertThatThrownBy(() -> fichajeService.corregir(10L, 99L, entrada, salida, null))
                .isInstanceOf(FichajeNotFoundException.class);
    }

    private static Empleado empleadoConJornada(int horas) {
        Empleado empleado = new Empleado();
        empleado.setId(EMPLEADO_ID);
        empleado.setHorasJornadaDiaria(BigDecimal.valueOf(horas));
        return empleado;
    }

    private static Fichaje fichajeConFechaYHoras(LocalDate fecha, String horas) {
        Fichaje fichaje = new Fichaje();
        fichaje.setFecha(fecha);
        fichaje.setHorasCalculadas(new BigDecimal(horas));
        return fichaje;
    }
}
