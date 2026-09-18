package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.dto.FichajeActualDTO;
import com.gestionrestaurante.back.dto.FichajeResponseDTO;
import com.gestionrestaurante.back.dto.FichajeResumenHoyDTO;
import com.gestionrestaurante.back.entity.ConfiguracionUbicacion;
import com.gestionrestaurante.back.entity.Fichaje;
import com.gestionrestaurante.back.exception.FichajeNoAbiertoException;
import com.gestionrestaurante.back.exception.FichajeYaAbiertoException;
import com.gestionrestaurante.back.exception.UbicacionFueraDeRangoException;
import com.gestionrestaurante.back.repository.EmpleadoRepository;
import com.gestionrestaurante.back.repository.FichajeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FichajeService {

    private static final double RADIO_TIERRA_METROS = 6_371_000;

    private final FichajeRepository fichajeRepository;
    private final EmpleadoRepository empleadoRepository;
    private final ConfiguracionUbicacionService configuracionUbicacionService;

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
        return toResponse(fichajeRepository.save(fichaje));
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
