package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.dto.FichajeActualDTO;
import com.gestionrestaurante.back.dto.FichajeResponseDTO;
import com.gestionrestaurante.back.dto.FichajeResumenDiaDTO;
import com.gestionrestaurante.back.dto.FichajeResumenHoyDTO;
import com.gestionrestaurante.back.dto.FichajeResumenMesDTO;
import com.gestionrestaurante.back.dto.FichajeResumenSemanaDTO;
import com.gestionrestaurante.back.entity.ConfiguracionUbicacion;
import com.gestionrestaurante.back.entity.Empleado;
import com.gestionrestaurante.back.entity.Fichaje;
import com.gestionrestaurante.back.exception.EmpleadoNotFoundException;
import com.gestionrestaurante.back.exception.FichajeNoAbiertoException;
import com.gestionrestaurante.back.exception.FichajeNotFoundException;
import com.gestionrestaurante.back.exception.FichajeYaAbiertoException;
import com.gestionrestaurante.back.exception.RangoFechasInvalidoException;
import com.gestionrestaurante.back.exception.UbicacionFueraDeRangoException;
import com.gestionrestaurante.back.repository.EmpleadoRepository;
import com.gestionrestaurante.back.repository.FichajeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FichajeService {

    private static final double RADIO_TIERRA_METROS = 6_371_000;
    private static final BigDecimal DIAS_SEMANA_LABORAL = BigDecimal.valueOf(5);

    private final FichajeRepository fichajeRepository;
    private final EmpleadoRepository empleadoRepository;
    private final ConfiguracionUbicacionService configuracionUbicacionService;
    private final VacacionesService vacacionesService;

    @Value("${app.fichajes.horas-jornada-default}")
    private BigDecimal horasJornadaDefault;

    @Transactional
    public FichajeResponseDTO iniciar(Long empleadoId, double lat, double lng) {
        ConfiguracionUbicacion configuracion = configuracionUbicacionService.obtener();
        double distancia = calcularDistanciaMetros(
                lat, lng, configuracion.getLat().doubleValue(), configuracion.getLng().doubleValue());
        if (distancia > configuracion.getRadioMetros()) {
            throw new UbicacionFueraDeRangoException();
        }

        if (fichajeRepository.findByEmpleado_IdAndHoraSalidaIsNull(empleadoId).isPresent()) {
            throw new FichajeYaAbiertoException();
        }

        Fichaje fichaje = new Fichaje();
        fichaje.setEmpleado(empleadoRepository.getReferenceById(empleadoId));
        fichaje.setFecha(LocalDate.now());
        fichaje.setHoraEntrada(LocalDateTime.now());
        fichaje.setLatEntrada(BigDecimal.valueOf(lat));
        fichaje.setLngEntrada(BigDecimal.valueOf(lng));
        try {
            return toResponse(fichajeRepository.save(fichaje));
        } catch (DataIntegrityViolationException ex) {
            // Segundo cinturón de seguridad (bbdd/fase1-1-bbdd.md): si dos peticiones casi
            // simultaneas pasan la comprobacion de arriba a la vez, el indice unico
            // uk_fichajes_abierto rechaza el segundo insert. Se traduce a la misma
            // excepcion de negocio en vez de dejar escapar la DataIntegrityViolationException
            // como un 500 generico.
            throw new FichajeYaAbiertoException();
        }
    }

    @Transactional
    public FichajeResponseDTO finalizar(Long empleadoId) {
        Fichaje fichaje = fichajeRepository.findByEmpleado_IdAndHoraSalidaIsNull(empleadoId)
                .orElseThrow(FichajeNoAbiertoException::new);

        LocalDateTime horaSalida = LocalDateTime.now();
        fichaje.setHoraSalida(horaSalida);
        fichaje.setHorasCalculadas(calcularHoras(fichaje.getHoraEntrada(), horaSalida));
        return toResponse(fichajeRepository.save(fichaje));
    }

    public FichajeActualDTO obtenerActual(Long empleadoId) {
        return fichajeRepository.findByEmpleado_IdAndHoraSalidaIsNull(empleadoId)
                .map(fichaje -> new FichajeActualDTO(true, fichaje.getHoraEntrada()))
                .orElseGet(() -> new FichajeActualDTO(false, null));
    }

    public Page<FichajeResponseDTO> listarMios(Long empleadoId, Pageable pageable) {
        return fichajeRepository.findByEmpleado_IdOrderByFechaDescHoraEntradaDesc(empleadoId, pageable)
                .map(this::toResponse);
    }

    public FichajeResumenHoyDTO obtenerResumenHoy(Long empleadoId) {
        List<Fichaje> fichajesHoy =
                fichajeRepository.findByEmpleado_IdAndFechaOrderByHoraEntradaAsc(empleadoId, LocalDate.now());

        BigDecimal horasImputadas = fichajesHoy.stream()
                .map(Fichaje::getHorasCalculadas)
                .filter(horas -> horas != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FichajeResponseDTO> respuesta = fichajesHoy.stream().map(this::toResponse).toList();
        return new FichajeResumenHoyDTO(respuesta, horasImputadas, horasJornadaDefault);
    }

    /**
     * empleado.horasJornadaDiaria manda si tiene valor; si es null, se usa el umbral global
     * (app.fichajes.horas-jornada-default). Unico metodo fuente de verdad, reutilizado por
     * los tres niveles de resumen para que no puedan divergir entre si.
     */
    public BigDecimal obtenerUmbralDiario(Empleado empleado) {
        return empleado.getHorasJornadaDiaria() != null ? empleado.getHorasJornadaDiaria() : horasJornadaDefault;
    }

    public Page<FichajeResponseDTO> listarComoAdmin(Long empleadoId, LocalDate desde, LocalDate hasta, Pageable pageable) {
        return fichajeRepository.findByFiltros(empleadoId, desde, hasta, pageable).map(this::toResponse);
    }

    public List<FichajeResumenDiaDTO> obtenerResumenDiario(Long empleadoId, LocalDate desde, LocalDate hasta) {
        BigDecimal umbralDiario = obtenerUmbralDiario(buscarEmpleadoOrThrow(empleadoId));

        Map<LocalDate, BigDecimal> horasPorDia = new TreeMap<>();
        for (Fichaje fichaje : fichajeRepository.findByEmpleado_IdAndFechaBetween(empleadoId, desde, hasta)) {
            if (fichaje.getHorasCalculadas() != null) {
                horasPorDia.merge(fichaje.getFecha(), fichaje.getHorasCalculadas(), BigDecimal::add);
            }
        }

        return horasPorDia.entrySet().stream()
                .map(entry -> new FichajeResumenDiaDTO(
                        entry.getKey(), entry.getValue(), umbralDiario, entry.getValue().compareTo(umbralDiario) >= 0))
                .toList();
    }

    public List<FichajeResumenSemanaDTO> obtenerResumenSemanal(Long empleadoId, LocalDate desde, LocalDate hasta) {
        BigDecimal umbralSemanal = obtenerUmbralDiario(buscarEmpleadoOrThrow(empleadoId)).multiply(DIAS_SEMANA_LABORAL);

        Map<LocalDate, BigDecimal> horasPorSemana = new TreeMap<>();
        for (Fichaje fichaje : fichajeRepository.findByEmpleado_IdAndFechaBetween(empleadoId, desde, hasta)) {
            if (fichaje.getHorasCalculadas() != null) {
                LocalDate inicioSemana = fichaje.getFecha().with(DayOfWeek.MONDAY);
                horasPorSemana.merge(inicioSemana, fichaje.getHorasCalculadas(), BigDecimal::add);
            }
        }

        return horasPorSemana.entrySet().stream()
                .map(entry -> new FichajeResumenSemanaDTO(
                        entry.getKey(),
                        entry.getKey().plusDays(6),
                        entry.getValue(),
                        umbralSemanal,
                        entry.getValue().compareTo(umbralSemanal) >= 0))
                .toList();
    }

    public List<FichajeResumenMesDTO> obtenerResumenMensual(Long empleadoId, int anio) {
        BigDecimal umbralDiario = obtenerUmbralDiario(buscarEmpleadoOrThrow(empleadoId));

        LocalDate desde = LocalDate.of(anio, 1, 1);
        LocalDate hasta = LocalDate.of(anio, 12, 31);

        Map<YearMonth, BigDecimal> horasPorMes = new TreeMap<>();
        for (Fichaje fichaje : fichajeRepository.findByEmpleado_IdAndFechaBetween(empleadoId, desde, hasta)) {
            if (fichaje.getHorasCalculadas() != null) {
                horasPorMes.merge(YearMonth.from(fichaje.getFecha()), fichaje.getHorasCalculadas(), BigDecimal::add);
            }
        }

        return horasPorMes.entrySet().stream()
                .map(entry -> {
                    YearMonth mes = entry.getKey();
                    int diasLaborables = vacacionesService.calcularDiasLaborables(mes.atDay(1), mes.atEndOfMonth());
                    BigDecimal umbralMes = umbralDiario.multiply(BigDecimal.valueOf(diasLaborables));
                    return new FichajeResumenMesDTO(
                            mes.getMonthValue(), mes.getYear(), entry.getValue(), umbralMes,
                            entry.getValue().compareTo(umbralMes) >= 0);
                })
                .toList();
    }

    @Transactional
    public FichajeResponseDTO corregir(
            Long fichajeId, Long adminId, LocalDateTime horaEntrada, LocalDateTime horaSalida, String observaciones) {
        if (horaSalida.isBefore(horaEntrada)) {
            throw new RangoFechasInvalidoException("La hora de salida debe ser igual o posterior a la hora de entrada");
        }

        Fichaje fichaje = fichajeRepository.findById(fichajeId)
                .orElseThrow(() -> new FichajeNotFoundException(fichajeId));

        fichaje.setHoraEntrada(horaEntrada);
        fichaje.setHoraSalida(horaSalida);
        fichaje.setHorasCalculadas(calcularHoras(horaEntrada, horaSalida));
        fichaje.setObservaciones(observaciones);
        fichaje.setCorregidoPorAdmin(true);
        fichaje.setCorregidoPorAdminId(adminId);
        fichaje.setFechaCorreccion(LocalDateTime.now());

        return toResponse(fichajeRepository.save(fichaje));
    }

    /**
     * Formula de Haversine sobre la esfericidad terrestre. Estatico y sin dependencias de
     * Spring a proposito: se puede testear con un assertEquals simple, sin mocks ni
     * contexto de Spring.
     */
    static double calcularDistanciaMetros(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return RADIO_TIERRA_METROS * c;
    }

    private static BigDecimal calcularHoras(LocalDateTime entrada, LocalDateTime salida) {
        long segundos = Duration.between(entrada, salida).getSeconds();
        return BigDecimal.valueOf(segundos).divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    }

    private Empleado buscarEmpleadoOrThrow(Long empleadoId) {
        return empleadoRepository.findById(empleadoId).orElseThrow(() -> new EmpleadoNotFoundException(empleadoId));
    }

    private FichajeResponseDTO toResponse(Fichaje fichaje) {
        return new FichajeResponseDTO(
                fichaje.getId(),
                fichaje.getFecha(),
                fichaje.getHoraEntrada(),
                fichaje.getHoraSalida(),
                fichaje.getHorasCalculadas(),
                fichaje.getObservaciones());
    }
}
