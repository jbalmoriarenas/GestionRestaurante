package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.dto.DiaCalendarioDTO;
import com.gestionrestaurante.back.dto.ResumenVacacionesDTO;
import com.gestionrestaurante.back.dto.SolicitudVacacionesResponseDTO;
import com.gestionrestaurante.back.dto.TipoDiaCalendario;
import com.gestionrestaurante.back.entity.DiaFestivo;
import com.gestionrestaurante.back.entity.Empleado;
import com.gestionrestaurante.back.entity.EstadoSolicitud;
import com.gestionrestaurante.back.entity.SolicitudVacaciones;
import com.gestionrestaurante.back.exception.DiasVacacionesInsuficientesException;
import com.gestionrestaurante.back.exception.EmpleadoNotFoundException;
import com.gestionrestaurante.back.exception.RangoFechasInvalidoException;
import com.gestionrestaurante.back.exception.SolicitudVacacionesNoEditableException;
import com.gestionrestaurante.back.exception.SolicitudVacacionesNoPropiaException;
import com.gestionrestaurante.back.exception.SolicitudVacacionesSolapadaException;
import com.gestionrestaurante.back.repository.DiaFestivoRepository;
import com.gestionrestaurante.back.repository.EmpleadoRepository;
import com.gestionrestaurante.back.repository.SolicitudVacacionesRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VacacionesService {

    private static final List<EstadoSolicitud> ESTADOS_QUE_CONSUMEN_CUPO =
            List.of(EstadoSolicitud.PENDIENTE, EstadoSolicitud.APROBADA);

    private final SolicitudVacacionesRepository solicitudVacacionesRepository;
    private final DiaFestivoRepository diaFestivoRepository;
    private final EmpleadoRepository empleadoRepository;

    @Transactional
    public SolicitudVacacionesResponseDTO solicitar(Long empleadoId, LocalDate inicio, LocalDate fin) {
        if (fin.isBefore(inicio)) {
            throw new RangoFechasInvalidoException();
        }
        // Solo se puede solicitar dentro del año en curso (confirmado con el usuario): no
        // basta con que inicio y fin coincidan entre si, ambos deben caer en el año de "hoy".
        int anioActual = LocalDate.now().getYear();
        if (inicio.getYear() != anioActual || fin.getYear() != anioActual) {
            throw new RangoFechasInvalidoException("El rango de vacaciones debe estar dentro del año en curso");
        }

        // Bloqueo pessimista de la fila del empleado (EmpleadoRepository.findByIdConBloqueo):
        // serializa, por empleado, el chequeo de dias disponibles seguido del INSERT de la
        // solicitud, para que dos peticiones casi simultaneas (doble clic, dos pestañas) no
        // puedan pasar ambas la comprobacion de "disponibles" y crear entre las dos mas dias
        // de los que el empleado tiene realmente. La regla es una suma agregada, no una
        // unicidad simple, asi que a diferencia de fichajes no hay un indice unico que la
        // proteja por si solo.
        Empleado empleado = empleadoRepository.findByIdConBloqueo(empleadoId)
                .orElseThrow(() -> new EmpleadoNotFoundException(empleadoId));

        int diasSolicitados = calcularDiasLaborables(inicio, fin);
        if (diasSolicitados == 0) {
            throw new RangoFechasInvalidoException("El rango seleccionado no incluye ningun dia laborable");
        }

        if (!solicitudVacacionesRepository.findSolapadas(empleadoId, ESTADOS_QUE_CONSUMEN_CUPO, inicio, fin).isEmpty()) {
            throw new SolicitudVacacionesSolapadaException();
        }

        ResumenVacacionesDTO resumen = obtenerResumenAnual(empleado, inicio.getYear());
        if (diasSolicitados > resumen.disponibles()) {
            throw new DiasVacacionesInsuficientesException();
        }

        SolicitudVacaciones solicitud = new SolicitudVacaciones();
        solicitud.setEmpleado(empleado);
        solicitud.setFechaInicio(inicio);
        solicitud.setFechaFin(fin);
        solicitud.setDiasSolicitados((short) diasSolicitados);
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        solicitud.setFechaSolicitud(LocalDateTime.now());

        return toResponse(solicitudVacacionesRepository.save(solicitud));
    }

    @Transactional
    public void cancelar(Long empleadoId, Long solicitudId) {
        SolicitudVacaciones solicitud = solicitudVacacionesRepository.findById(solicitudId)
                .filter(s -> s.getEmpleado().getId().equals(empleadoId))
                .orElseThrow(SolicitudVacacionesNoPropiaException::new);

        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new SolicitudVacacionesNoEditableException();
        }

        solicitudVacacionesRepository.delete(solicitud);
    }

    public ResumenVacacionesDTO obtenerResumenAnual(Long empleadoId, int anio) {
        return obtenerResumenAnual(empleadoRepository.getReferenceById(empleadoId), anio);
    }

    public List<DiaCalendarioDTO> obtenerCalendario(Long empleadoId, int anio) {
        LocalDate desde = LocalDate.of(anio, 1, 1);
        LocalDate hasta = LocalDate.of(anio, 12, 31);

        Map<LocalDate, TipoDiaCalendario> dias = new HashMap<>();
        diaFestivoRepository.findByFechaBetween(desde, hasta)
                .forEach(festivo -> dias.put(festivo.getFecha(), TipoDiaCalendario.FESTIVO));

        for (SolicitudVacaciones solicitud : solicitudesActivasDelAnio(empleadoId, anio)) {
            TipoDiaCalendario tipo = solicitud.getEstado() == EstadoSolicitud.APROBADA
                    ? TipoDiaCalendario.APROBADO
                    : TipoDiaCalendario.PENDIENTE;
            for (LocalDate fecha = solicitud.getFechaInicio(); !fecha.isAfter(solicitud.getFechaFin());
                    fecha = fecha.plusDays(1)) {
                dias.putIfAbsent(fecha, tipo);
            }
        }

        return dias.entrySet().stream()
                .map(entry -> new DiaCalendarioDTO(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> a.fecha().compareTo(b.fecha()))
                .toList();
    }

    public Page<SolicitudVacacionesResponseDTO> listarMisSolicitudes(Long empleadoId, Pageable pageable) {
        return solicitudVacacionesRepository.findByEmpleado_IdOrderByFechaSolicitudDesc(empleadoId, pageable)
                .map(this::toResponse);
    }

    /**
     * Unica fuente de verdad del calculo de dias laborables (lunes-viernes, sin festivos
     * sembrados en dias_festivos). Reutilizado tambien por la vista Admin (fase1-4-back.md)
     * para que el numero de dias que ve el empleado al solicitar sea siempre el mismo que
     * ve el Admin al aprobar.
     */
    int calcularDiasLaborables(LocalDate inicio, LocalDate fin) {
        Set<LocalDate> festivos = diaFestivoRepository.findByFechaBetween(inicio, fin).stream()
                .map(DiaFestivo::getFecha)
                .collect(Collectors.toSet());

        int dias = 0;
        for (LocalDate fecha = inicio; !fecha.isAfter(fin); fecha = fecha.plusDays(1)) {
            DayOfWeek diaSemana = fecha.getDayOfWeek();
            if (diaSemana != DayOfWeek.SATURDAY && diaSemana != DayOfWeek.SUNDAY && !festivos.contains(fecha)) {
                dias++;
            }
        }
        return dias;
    }

    /**
     * Prorratea el cupo del año de alta del empleado; en cualquier otro año devuelve el
     * valor completo (o 0 si el año consultado es anterior al de alta). Redondeo al entero
     * mas cercano, coherente con la decision de "solo dias completos" (ver
     * back/fase1-2-back.md).
     */
    int calcularCupoAnual(Empleado empleado, int anio) {
        int anioAlta = empleado.getFechaAlta().getYear();
        if (anioAlta > anio) {
            return 0;
        }
        if (anioAlta < anio) {
            return empleado.getDiasVacacionesAnuales();
        }
        int mesesRestantes = 12 - empleado.getFechaAlta().getMonthValue() + 1;
        return Math.round(empleado.getDiasVacacionesAnuales() * mesesRestantes / 12.0f);
    }

    private ResumenVacacionesDTO obtenerResumenAnual(Empleado empleado, int anio) {
        int total = calcularCupoAnual(empleado, anio);

        List<SolicitudVacaciones> activas = solicitudesActivasDelAnio(empleado.getId(), anio);
        int aprobados = sumarDias(activas, EstadoSolicitud.APROBADA);
        int pendientes = sumarDias(activas, EstadoSolicitud.PENDIENTE);

        return new ResumenVacacionesDTO(total, aprobados, pendientes, total - aprobados - pendientes);
    }

    private List<SolicitudVacaciones> solicitudesActivasDelAnio(Long empleadoId, int anio) {
        LocalDate desde = LocalDate.of(anio, 1, 1);
        LocalDate hasta = LocalDate.of(anio, 12, 31);
        return solicitudVacacionesRepository.findByEmpleado_IdAndEstadoInAndFechaInicioBetween(
                empleadoId, ESTADOS_QUE_CONSUMEN_CUPO, desde, hasta);
    }

    private static int sumarDias(List<SolicitudVacaciones> solicitudes, EstadoSolicitud estado) {
        return solicitudes.stream()
                .filter(solicitud -> solicitud.getEstado() == estado)
                .mapToInt(SolicitudVacaciones::getDiasSolicitados)
                .sum();
    }

    private SolicitudVacacionesResponseDTO toResponse(SolicitudVacaciones solicitud) {
        return new SolicitudVacacionesResponseDTO(
                solicitud.getId(),
                solicitud.getFechaInicio(),
                solicitud.getFechaFin(),
                solicitud.getDiasSolicitados(),
                solicitud.getEstado(),
                solicitud.getFechaSolicitud(),
                solicitud.getMotivoRechazo());
    }
}
