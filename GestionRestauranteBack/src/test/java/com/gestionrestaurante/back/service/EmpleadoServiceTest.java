package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gestionrestaurante.back.dto.EmpleadoResponseDTO;
import com.gestionrestaurante.back.entity.ConfiguracionVacaciones;
import com.gestionrestaurante.back.entity.Empleado;
import com.gestionrestaurante.back.entity.TipoUsuario;
import com.gestionrestaurante.back.exception.DiasVacacionesAnualesFueraDeRangoException;
import com.gestionrestaurante.back.exception.EmpleadoNotFoundException;
import com.gestionrestaurante.back.repository.EmpleadoRepository;
import com.gestionrestaurante.back.repository.TipoUsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class EmpleadoServiceTest {

    private static final Long EMPLEADO_ID = 1L;

    @Mock
    private EmpleadoRepository empleadoRepository;
    @Mock
    private TipoUsuarioRepository tipoUsuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ConfiguracionVacacionesService configuracionVacacionesService;

    private EmpleadoService empleadoService;

    @BeforeEach
    void setUp() {
        empleadoService = new EmpleadoService(
                empleadoRepository, tipoUsuarioRepository, passwordEncoder, configuracionVacacionesService);
    }

    @Test
    void actualizarHorarioTeorico_conValorValido_loPersisteYLoDevuelve() {
        Empleado empleado = empleadoDePrueba();
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(empleadoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EmpleadoResponseDTO respuesta = empleadoService.actualizarHorarioTeorico(EMPLEADO_ID, BigDecimal.valueOf(6.5));

        assertThat(empleado.getHorasJornadaDiaria()).isEqualByComparingTo("6.5");
        assertThat(respuesta.horasJornadaDiaria()).isEqualByComparingTo("6.5");
    }

    @Test
    void actualizarHorarioTeorico_conNull_vuelveAUsarElValorGlobal() {
        Empleado empleado = empleadoDePrueba();
        empleado.setHorasJornadaDiaria(BigDecimal.valueOf(6));
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(empleadoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EmpleadoResponseDTO respuesta = empleadoService.actualizarHorarioTeorico(EMPLEADO_ID, null);

        assertThat(empleado.getHorasJornadaDiaria()).isNull();
        assertThat(respuesta.horasJornadaDiaria()).isNull();
    }

    @Test
    void actualizarHorarioTeorico_empleadoInexistenteLanzaEmpleadoNotFound() {
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> empleadoService.actualizarHorarioTeorico(EMPLEADO_ID, BigDecimal.valueOf(6)))
                .isInstanceOf(EmpleadoNotFoundException.class);
    }

    @Test
    void actualizarDiasVacacionesAnuales_conValorValido_loPersisteYLoDevuelve() {
        Empleado empleado = empleadoDePrueba();
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(empleadoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(configuracionVacacionesService.obtener()).thenReturn(configuracionConDefecto(23));

        EmpleadoResponseDTO respuesta = empleadoService.actualizarDiasVacacionesAnuales(EMPLEADO_ID, 15);

        assertThat(empleado.getDiasVacacionesAnuales()).isEqualTo(15);
        assertThat(respuesta.diasVacacionesAnuales()).isEqualTo(15);
    }

    @Test
    void actualizarDiasVacacionesAnuales_conNull_vuelveAUsarElValorGlobal() {
        Empleado empleado = empleadoDePrueba();
        empleado.setDiasVacacionesAnuales(15);
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(empleadoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EmpleadoResponseDTO respuesta = empleadoService.actualizarDiasVacacionesAnuales(EMPLEADO_ID, null);

        assertThat(empleado.getDiasVacacionesAnuales()).isNull();
        assertThat(respuesta.diasVacacionesAnuales()).isNull();
    }

    @Test
    void actualizarDiasVacacionesAnuales_porEncimaDelGlobalLanzaFueraDeRango() {
        Empleado empleado = empleadoDePrueba();
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(empleado));
        when(configuracionVacacionesService.obtener()).thenReturn(configuracionConDefecto(23));

        assertThatThrownBy(() -> empleadoService.actualizarDiasVacacionesAnuales(EMPLEADO_ID, 30))
                .isInstanceOf(DiasVacacionesAnualesFueraDeRangoException.class);
    }

    @Test
    void actualizarDiasVacacionesAnuales_empleadoInexistenteLanzaEmpleadoNotFound() {
        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> empleadoService.actualizarDiasVacacionesAnuales(EMPLEADO_ID, 15))
                .isInstanceOf(EmpleadoNotFoundException.class);
    }

    private static ConfiguracionVacaciones configuracionConDefecto(int diasAnualesDefecto) {
        ConfiguracionVacaciones configuracion = new ConfiguracionVacaciones();
        configuracion.setDiasAnualesDefecto(diasAnualesDefecto);
        return configuracion;
    }

    private static Empleado empleadoDePrueba() {
        Empleado empleado = new Empleado();
        empleado.setId(EMPLEADO_ID);
        empleado.setNombre("Ana");
        empleado.setApellidos("Lopez");
        empleado.setEmail("ana@test.com");
        empleado.setDniNie("12345678A");
        empleado.setFechaAlta(LocalDate.of(2026, 1, 1));
        empleado.setActivo(true);

        TipoUsuario tipoUsuario = new TipoUsuario();
        tipoUsuario.setCodigo("EMPLEADO");
        tipoUsuario.setNombre("Empleado");
        empleado.setTipoUsuario(tipoUsuario);

        return empleado;
    }
}
