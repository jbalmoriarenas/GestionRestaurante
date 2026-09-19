package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gestionrestaurante.back.dto.DiaCalendarioDTO;
import com.gestionrestaurante.back.dto.ResumenVacacionesDTO;
import com.gestionrestaurante.back.dto.SolicitudVacacionesAdminResponseDTO;
import com.gestionrestaurante.back.dto.SolicitudVacacionesResponseDTO;
import com.gestionrestaurante.back.dto.TipoDiaCalendario;
import com.gestionrestaurante.back.entity.ConfiguracionVacaciones;
import com.gestionrestaurante.back.entity.DiaFestivo;
import com.gestionrestaurante.back.entity.Empleado;
import com.gestionrestaurante.back.entity.EstadoSolicitud;
import com.gestionrestaurante.back.entity.SolicitudVacaciones;
import com.gestionrestaurante.back.exception.DiasVacacionesInsuficientesException;
import com.gestionrestaurante.back.exception.RangoFechasInvalidoException;
import com.gestionrestaurante.back.exception.SolicitudVacacionesNoEditableException;
import com.gestionrestaurante.back.exception.SolicitudVacacionesNoPropiaException;
import com.gestionrestaurante.back.exception.SolicitudVacacionesNotFoundException;
import com.gestionrestaurante.back.exception.SolicitudVacacionesSolapadaException;
import com.gestionrestaurante.back.repository.DiaFestivoRepository;
import com.gestionrestaurante.back.repository.EmpleadoRepository;
import com.gestionrestaurante.back.repository.SolicitudVacacionesRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class VacacionesServiceTest {

    @Mock
    private SolicitudVacacionesRepository solicitudVacacionesRepository;
    @Mock
    private DiaFestivoRepository diaFestivoRepository;
    @Mock
    private EmpleadoRepository empleadoRepository;
    @Mock
    private ConfiguracionVacacionesService configuracionVacacionesService;

    private VacacionesService vacacionesService;

    @BeforeEach
    void setUp() {
        vacacionesService = new VacacionesService(
                solicitudVacacionesRepository, diaFestivoRepository, empleadoRepository, configuracionVacacionesService);
    }

    @Test
    void calcularDiasLaborables_excluyeFinesDeSemanaYFestivos() {
        // lunes 2026-01-05 a domingo 2026-01-11: 5 laborables, menos el festivo del miercoles 2026-01-07
        LocalDate inicio = LocalDate.of(2026, 1, 5);
        LocalDate fin = LocalDate.of(2026, 1, 11);
        DiaFestivo festivo = new DiaFestivo();
        festivo.setFecha(LocalDate.of(2026, 1, 7));
        when(diaFestivoRepository.findByFechaBetween(inicio, fin)).thenReturn(List.of(festivo));

        int dias = vacacionesService.calcularDiasLaborables(inicio, fin);

        assertThat(dias).isEqualTo(4);
    }

    @Test
    void calcularCupoAnual_altaEnEneroDevuelveCupoCompleto() {
        Empleado empleado = empleadoConAltaYDias(LocalDate.of(2020, 1, 15), 23);

        assertThat(vacacionesService.calcularCupoAnual(empleado, 2026)).isEqualTo(23);
    }

    @Test
    void calcularCupoAnual_altaEnJulioSinOverridePropioProrrateaElGlobal() {
        Empleado empleado = new Empleado();
        empleado.setFechaAlta(LocalDate.of(2026, 7, 1));
        when(configuracionVacacionesService.obtener()).thenReturn(configuracionConDefecto(23));

        // meses restantes: julio a diciembre inclusive = 6 -> round(23 * 6/12) = round(11.5) = 12
        assertThat(vacacionesService.calcularCupoAnual(empleado, 2026)).isEqualTo(12);
    }

    @Test
    void calcularCupoAnual_altaEnJulioConOverridePropioNoSeProrratea() {
        // Un override explicito del Admin (fase1-4-back.md) es el total exacto de ese
        // empleado para este año, incluso en su propio año de alta: no se recalcula con la
        // formula de prorrateo.
        Empleado empleado = empleadoConAltaYDias(LocalDate.of(2026, 7, 1), 10);

        assertThat(vacacionesService.calcularCupoAnual(empleado, 2026)).isEqualTo(10);
    }

    @Test
    void calcularCupoAnual_anioAnteriorAlAltaDevuelveCero() {
        Empleado empleado = empleadoConAltaYDias(LocalDate.of(2027, 3, 1), 23);

        assertThat(vacacionesService.calcularCupoAnual(empleado, 2026)).isZero();
    }

    @Test
    void solicitar_rangoEnDistintosAniosLanzaRangoInvalidoSinBloquearAlEmpleado() {
        int anioActual = LocalDate.now().getYear();
        LocalDate inicio = LocalDate.of(anioActual, 12, 29);
        LocalDate fin = LocalDate.of(anioActual + 1, 1, 3);

        assertThatThrownBy(() -> vacacionesService.solicitar(1L, inicio, fin))
                .isInstanceOf(RangoFechasInvalidoException.class);

        verify(empleadoRepository, never()).findByIdConBloqueo(any());
    }

    @Test
    void solicitar_rangoEnUnAnioDistintoAlActualLanzaRangoInvalidoAunqueInicioYFinCoincidan() {
        int anioPasado = LocalDate.now().getYear() - 1;
        LocalDate inicio = LocalDate.of(anioPasado, 6, 1);
        LocalDate fin = LocalDate.of(anioPasado, 6, 5);

        assertThatThrownBy(() -> vacacionesService.solicitar(1L, inicio, fin))
                .isInstanceOf(RangoFechasInvalidoException.class);

        verify(empleadoRepository, never()).findByIdConBloqueo(any());
    }

    @Test
    void solicitar_rangoSinDiasLaborablesLanzaRangoInvalido() {
        Empleado empleado = empleadoConAltaYDias(LocalDate.of(2020, 1, 1), 23);
        when(empleadoRepository.findByIdConBloqueo(1L)).thenReturn(Optional.of(empleado));
        // sabado 2026-01-10 a domingo 2026-01-11: 0 dias laborables
        LocalDate inicio = LocalDate.of(2026, 1, 10);
        LocalDate fin = LocalDate.of(2026, 1, 11);
        when(diaFestivoRepository.findByFechaBetween(inicio, fin)).thenReturn(List.of());

        assertThatThrownBy(() -> vacacionesService.solicitar(1L, inicio, fin))
                .isInstanceOf(RangoFechasInvalidoException.class);

        verify(solicitudVacacionesRepository, never()).save(any());
    }

    @Test
    void solicitar_solicitudSolapadaLanza409SinCrearNada() {
        Empleado empleado = empleadoConAltaYDias(LocalDate.of(2020, 1, 1), 23);
        when(empleadoRepository.findByIdConBloqueo(1L)).thenReturn(Optional.of(empleado));
        LocalDate inicio = LocalDate.of(2026, 1, 5);
        LocalDate fin = LocalDate.of(2026, 1, 9);
        when(diaFestivoRepository.findByFechaBetween(inicio, fin)).thenReturn(List.of());
        when(solicitudVacacionesRepository.findSolapadas(eq(1L), any(), eq(inicio), eq(fin)))
                .thenReturn(List.of(new SolicitudVacaciones()));

        assertThatThrownBy(() -> vacacionesService.solicitar(1L, inicio, fin))
                .isInstanceOf(SolicitudVacacionesSolapadaException.class);

        verify(solicitudVacacionesRepository, never()).save(any());
    }

    @Test
    void solicitar_masDiasQueLosDisponiblesLanza409SinCrearNada() {
        Empleado empleado = empleadoConAltaYDias(LocalDate.of(2020, 1, 1), 5);
        when(empleadoRepository.findByIdConBloqueo(1L)).thenReturn(Optional.of(empleado));
        when(diaFestivoRepository.findByFechaBetween(any(), any())).thenReturn(List.of());
        when(solicitudVacacionesRepository.findSolapadas(eq(1L), any(), any(), any())).thenReturn(List.of());
        when(solicitudVacacionesRepository.findByEmpleado_IdAndEstadoInAndFechaInicioBetween(
                eq(1L), any(), any(), any())).thenReturn(List.of());

        LocalDate inicio = LocalDate.of(2026, 1, 5);
        LocalDate fin = LocalDate.of(2026, 1, 12); // 6 dias laborables > 5 disponibles

        assertThatThrownBy(() -> vacacionesService.solicitar(1L, inicio, fin))
                .isInstanceOf(DiasVacacionesInsuficientesException.class);

        verify(solicitudVacacionesRepository, never()).save(any());
    }

    @Test
    void solicitar_conDiasDisponiblesCreaLaSolicitudPendiente() {
        Empleado empleado = empleadoConAltaYDias(LocalDate.of(2020, 1, 1), 23);
        when(empleadoRepository.findByIdConBloqueo(1L)).thenReturn(Optional.of(empleado));
        LocalDate inicio = LocalDate.of(2026, 1, 5);
        LocalDate fin = LocalDate.of(2026, 1, 9); // lunes a viernes: 5 dias laborables
        when(diaFestivoRepository.findByFechaBetween(any(), any())).thenReturn(List.of());
        when(solicitudVacacionesRepository.findSolapadas(eq(1L), any(), any(), any())).thenReturn(List.of());
        when(solicitudVacacionesRepository.findByEmpleado_IdAndEstadoInAndFechaInicioBetween(
                eq(1L), any(), any(), any())).thenReturn(List.of());
        when(solicitudVacacionesRepository.save(any())).thenAnswer(invocation -> {
            SolicitudVacaciones guardada = invocation.getArgument(0);
            guardada.setId(99L);
            return guardada;
        });

        SolicitudVacacionesResponseDTO respuesta = vacacionesService.solicitar(1L, inicio, fin);

        assertThat(respuesta.id()).isEqualTo(99L);
        assertThat(respuesta.fechaInicio()).isEqualTo(inicio);
        assertThat(respuesta.fechaFin()).isEqualTo(fin);
        assertThat(respuesta.diasSolicitados()).isEqualTo((short) 5);
        assertThat(respuesta.estado()).isEqualTo(EstadoSolicitud.PENDIENTE);
    }

    @Test
    void cancelar_solicitudYaAprobadaLanza409() {
        SolicitudVacaciones solicitud = new SolicitudVacaciones();
        Empleado empleado = new Empleado();
        empleado.setId(1L);
        solicitud.setEmpleado(empleado);
        solicitud.setEstado(EstadoSolicitud.APROBADA);
        when(solicitudVacacionesRepository.findById(10L)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() -> vacacionesService.cancelar(1L, 10L))
                .isInstanceOf(SolicitudVacacionesNoEditableException.class);

        verify(solicitudVacacionesRepository, never()).delete(any());
    }

    @Test
    void cancelar_solicitudDeOtroEmpleadoLanzaNoPropia() {
        SolicitudVacaciones solicitud = new SolicitudVacaciones();
        Empleado otroEmpleado = new Empleado();
        otroEmpleado.setId(2L);
        solicitud.setEmpleado(otroEmpleado);
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        when(solicitudVacacionesRepository.findById(10L)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() -> vacacionesService.cancelar(1L, 10L))
                .isInstanceOf(SolicitudVacacionesNoPropiaException.class);

        verify(solicitudVacacionesRepository, never()).delete(any());
    }

    @Test
    void cancelar_solicitudInexistenteLanzaNoPropia() {
        when(solicitudVacacionesRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vacacionesService.cancelar(1L, 10L))
                .isInstanceOf(SolicitudVacacionesNoPropiaException.class);
    }

    @Test
    void obtenerResumenAnual_calculaDisponiblesRestandoAprobadosYPendientes() {
        Empleado empleado = empleadoConAltaYDias(LocalDate.of(2020, 1, 1), 23);
        when(empleadoRepository.getReferenceById(1L)).thenReturn(empleado);
        SolicitudVacaciones aprobada = new SolicitudVacaciones();
        aprobada.setEstado(EstadoSolicitud.APROBADA);
        aprobada.setDiasSolicitados((short) 5);
        SolicitudVacaciones pendiente = new SolicitudVacaciones();
        pendiente.setEstado(EstadoSolicitud.PENDIENTE);
        pendiente.setDiasSolicitados((short) 3);
        when(solicitudVacacionesRepository.findByEmpleado_IdAndEstadoInAndFechaInicioBetween(
                eq(1L), any(), any(), any())).thenReturn(List.of(aprobada, pendiente));

        ResumenVacacionesDTO resumen = vacacionesService.obtenerResumenAnual(1L, 2026);

        assertThat(resumen.total()).isEqualTo(23);
        assertThat(resumen.aprobados()).isEqualTo(5);
        assertThat(resumen.pendientes()).isEqualTo(3);
        assertThat(resumen.disponibles()).isEqualTo(15);
    }

    @Test
    void obtenerCalendario_combinaFestivosYSolicitudesPropias() {
        DiaFestivo festivo = new DiaFestivo();
        festivo.setFecha(LocalDate.of(2026, 1, 1));
        when(diaFestivoRepository.findByFechaBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of(festivo));

        SolicitudVacaciones aprobada = new SolicitudVacaciones();
        aprobada.setEstado(EstadoSolicitud.APROBADA);
        aprobada.setFechaInicio(LocalDate.of(2026, 6, 1));
        aprobada.setFechaFin(LocalDate.of(2026, 6, 2));
        when(solicitudVacacionesRepository.findByEmpleado_IdAndEstadoInAndFechaInicioBetween(
                eq(1L), any(), any(), any())).thenReturn(List.of(aprobada));

        List<DiaCalendarioDTO> dias = vacacionesService.obtenerCalendario(1L, 2026);

        assertThat(dias).contains(
                new DiaCalendarioDTO(LocalDate.of(2026, 1, 1), TipoDiaCalendario.FESTIVO),
                new DiaCalendarioDTO(LocalDate.of(2026, 6, 1), TipoDiaCalendario.APROBADO),
                new DiaCalendarioDTO(LocalDate.of(2026, 6, 2), TipoDiaCalendario.APROBADO));
    }

    @Test
    void obtenerDiasVacacionesAnuales_sinValorPropioUsaElGlobal() {
        Empleado empleado = new Empleado();
        when(configuracionVacacionesService.obtener()).thenReturn(configuracionConDefecto(25));

        assertThat(vacacionesService.obtenerDiasVacacionesAnuales(empleado)).isEqualTo(25);
    }

    @Test
    void obtenerDiasVacacionesAnuales_conValorPropioLoUsaEnVezDelGlobal() {
        Empleado empleado = new Empleado();
        empleado.setDiasVacacionesAnuales(15);

        assertThat(vacacionesService.obtenerDiasVacacionesAnuales(empleado)).isEqualTo(15);
    }

    @Test
    void aprobar_solicitudPendienteLaDejaAprobadaConTraza() {
        SolicitudVacaciones solicitud = new SolicitudVacaciones();
        solicitud.setId(10L);
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        when(solicitudVacacionesRepository.findById(10L)).thenReturn(Optional.of(solicitud));
        when(solicitudVacacionesRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SolicitudVacacionesResponseDTO respuesta = vacacionesService.aprobar(10L, 99L);

        assertThat(respuesta.estado()).isEqualTo(EstadoSolicitud.APROBADA);
        assertThat(solicitud.getFechaResolucion()).isNotNull();
        assertThat(solicitud.getResueltaPorAdminId()).isEqualTo(99L);
    }

    @Test
    void aprobar_solicitudYaResueltaLanza409() {
        SolicitudVacaciones solicitud = new SolicitudVacaciones();
        solicitud.setEstado(EstadoSolicitud.RECHAZADA);
        when(solicitudVacacionesRepository.findById(10L)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() -> vacacionesService.aprobar(10L, 99L))
                .isInstanceOf(SolicitudVacacionesNoEditableException.class);

        verify(solicitudVacacionesRepository, never()).save(any());
    }

    @Test
    void aprobar_solicitudInexistenteLanzaNotFound() {
        when(solicitudVacacionesRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vacacionesService.aprobar(10L, 99L))
                .isInstanceOf(SolicitudVacacionesNotFoundException.class);
    }

    @Test
    void rechazar_solicitudPendienteLaDejaRechazadaConMotivo() {
        SolicitudVacaciones solicitud = new SolicitudVacaciones();
        solicitud.setId(10L);
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        when(solicitudVacacionesRepository.findById(10L)).thenReturn(Optional.of(solicitud));
        when(solicitudVacacionesRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SolicitudVacacionesResponseDTO respuesta = vacacionesService.rechazar(10L, 99L, "no queda cupo");

        assertThat(respuesta.estado()).isEqualTo(EstadoSolicitud.RECHAZADA);
        assertThat(respuesta.motivoRechazo()).isEqualTo("no queda cupo");
        assertThat(solicitud.getResueltaPorAdminId()).isEqualTo(99L);
    }

    @Test
    void rechazar_solicitudYaResueltaLanza409() {
        SolicitudVacaciones solicitud = new SolicitudVacaciones();
        solicitud.setEstado(EstadoSolicitud.APROBADA);
        when(solicitudVacacionesRepository.findById(10L)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() -> vacacionesService.rechazar(10L, 99L, "motivo"))
                .isInstanceOf(SolicitudVacacionesNoEditableException.class);
    }

    @Test
    void listarTodasPendientes_mapeaElEmpleadoDeCadaSolicitud() {
        Empleado empleado = new Empleado();
        empleado.setId(5L);
        empleado.setNombre("Ana");
        empleado.setApellidos("Lopez");
        SolicitudVacaciones solicitud = new SolicitudVacaciones();
        solicitud.setId(1L);
        solicitud.setEmpleado(empleado);
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        when(solicitudVacacionesRepository.findByEstadoOrderByFechaSolicitudAsc(eq(EstadoSolicitud.PENDIENTE), any()))
                .thenReturn(new PageImpl<>(List.of(solicitud)));

        List<SolicitudVacacionesAdminResponseDTO> resultado =
                vacacionesService.listarTodasPendientes(PageRequest.of(0, 20)).getContent();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).empleadoId()).isEqualTo(5L);
        assertThat(resultado.get(0).empleadoNombre()).isEqualTo("Ana Lopez");
    }

    @Test
    void asignarDeOficio_conCupoSuficienteCreaLaSolicitudYaAprobada() {
        Empleado empleado = empleadoConAltaYDias(LocalDate.of(2020, 1, 1), 23);
        when(empleadoRepository.findByIdConBloqueo(1L)).thenReturn(Optional.of(empleado));
        LocalDate inicio = LocalDate.of(2026, 1, 5);
        LocalDate fin = LocalDate.of(2026, 1, 9);
        when(diaFestivoRepository.findByFechaBetween(any(), any())).thenReturn(List.of());
        when(solicitudVacacionesRepository.findSolapadas(eq(1L), any(), any(), any())).thenReturn(List.of());
        when(solicitudVacacionesRepository.findByEmpleado_IdAndEstadoInAndFechaInicioBetween(
                eq(1L), any(), any(), any())).thenReturn(List.of());
        when(solicitudVacacionesRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SolicitudVacacionesResponseDTO respuesta = vacacionesService.asignarDeOficio(1L, inicio, fin, 99L);

        assertThat(respuesta.estado()).isEqualTo(EstadoSolicitud.APROBADA);
    }

    @Test
    void asignarDeOficio_sinCupoSuficienteLanza409SinCrearNada() {
        Empleado empleado = empleadoConAltaYDias(LocalDate.of(2020, 1, 1), 5);
        when(empleadoRepository.findByIdConBloqueo(1L)).thenReturn(Optional.of(empleado));
        when(diaFestivoRepository.findByFechaBetween(any(), any())).thenReturn(List.of());
        when(solicitudVacacionesRepository.findSolapadas(eq(1L), any(), any(), any())).thenReturn(List.of());
        when(solicitudVacacionesRepository.findByEmpleado_IdAndEstadoInAndFechaInicioBetween(
                eq(1L), any(), any(), any())).thenReturn(List.of());

        LocalDate inicio = LocalDate.of(2026, 1, 5);
        LocalDate fin = LocalDate.of(2026, 1, 12); // 6 dias laborables > 5 disponibles

        assertThatThrownBy(() -> vacacionesService.asignarDeOficio(1L, inicio, fin, 99L))
                .isInstanceOf(DiasVacacionesInsuficientesException.class);

        verify(solicitudVacacionesRepository, never()).save(any());
    }

    private static Empleado empleadoConAltaYDias(LocalDate fechaAlta, int diasVacacionesAnuales) {
        Empleado empleado = new Empleado();
        empleado.setId(1L);
        empleado.setFechaAlta(fechaAlta);
        empleado.setDiasVacacionesAnuales(diasVacacionesAnuales);
        return empleado;
    }

    private static ConfiguracionVacaciones configuracionConDefecto(int diasAnualesDefecto) {
        ConfiguracionVacaciones configuracion = new ConfiguracionVacaciones();
        configuracion.setDiasAnualesDefecto(diasAnualesDefecto);
        return configuracion;
    }
}
