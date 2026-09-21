package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.dto.NominaDescargaDTO;
import com.gestionrestaurante.back.dto.NominaListadoDTO;
import com.gestionrestaurante.back.dto.NominaPendienteDTO;
import com.gestionrestaurante.back.dto.NominaResponseDTO;
import com.gestionrestaurante.back.dto.ProcesamientoZipDTO;
import com.gestionrestaurante.back.entity.Empleado;
import com.gestionrestaurante.back.entity.EstadoNomina;
import com.gestionrestaurante.back.entity.Nomina;
import com.gestionrestaurante.back.exception.ArchivoNoValidoException;
import com.gestionrestaurante.back.exception.EmpleadoNotFoundException;
import com.gestionrestaurante.back.exception.EnvioNominaFallidoException;
import com.gestionrestaurante.back.exception.NominaNotFoundException;
import com.gestionrestaurante.back.exception.PendienteNoEncontradoException;
import com.gestionrestaurante.back.exception.ZipInvalidoException;
import com.gestionrestaurante.back.repository.EmpleadoRepository;
import com.gestionrestaurante.back.repository.NominaRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Fase 3 (Nominas): listado con estado calculado (PENDIENTE nunca se persiste, ver
 * bbdd/fase3-1-bbdd.md), subida individual y por ZIP con matching automatico
 * (back/fase3-2-back.md) y envio por email (back/fase3-3-back.md).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NominaService {

    public static final String ESTADO_PENDIENTE = "PENDIENTE";

    public static final String MOTIVO_NO_ES_PDF = "NO_ES_PDF";
    public static final String MOTIVO_NOMBRE_NO_VALIDO = "NOMBRE_NO_VALIDO";
    public static final String MOTIVO_EMPLEADO_NO_ENCONTRADO = "EMPLEADO_NO_ENCONTRADO";

    private static final String CONTENT_TYPE_PDF = "application/pdf";
    private static final String SUBCARPETA_NOMINAS = "nominas";
    private static final String SUBCARPETA_PENDIENTES = "nominas/pendientes";

    /** Mismo patron de DNI/NIE ya validado en EmpleadoCreateDTO (fase0-back.md). */
    private static final Pattern PATRON_NOMBRE_ZIP = Pattern.compile(
            "^([0-9XYZxyz][0-9]{7}[A-Za-z])_([0-9]{4})-([0-9]{2})\\.pdf$", Pattern.CASE_INSENSITIVE);

    private final NominaRepository nominaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final FileStorageService fileStorageService;
    private final EmailService emailService;

    /**
     * Los pendientes de un ZIP no se persisten en BBDD (back/fase3-2-back.md): el nombre
     * fisico en disco es siempre "{uuid}.pdf", asi que el nombre original que se muestra al
     * Admin (y que hay que recuperar al asignar manualmente) se guarda aqui, en memoria, con
     * la misma naturaleza efimera que el propio archivo temporal (se pierde si se reinicia la
     * aplicacion, igual que los archivos huerfanos documentados como limitacion conocida).
     */
    private final Map<String, String> nombresOriginalesPendientes = new ConcurrentHashMap<>();

    public Page<NominaListadoDTO> listar(
            Long empleadoIdOpcional, int periodoMes, int periodoAnio, String estadoOpcional, Pageable pageable) {
        List<Empleado> empleados = empleadoIdOpcional != null
                ? empleadoRepository.findById(empleadoIdOpcional)
                        .filter(Empleado::isActivo)
                        .map(List::of)
                        .orElseGet(List::of)
                : empleadoRepository.findByActivoTrueOrderByApellidosAscNombreAsc();

        Map<Long, Nomina> nominasPorEmpleado = nominaRepository
                .findByPeriodoMesAndPeriodoAnio(periodoMes, periodoAnio).stream()
                .collect(Collectors.toMap(nomina -> nomina.getEmpleado().getId(), nomina -> nomina));

        List<NominaListadoDTO> filas = empleados.stream()
                .map(empleado -> toListadoDTO(empleado, nominasPorEmpleado.get(empleado.getId()), periodoMes, periodoAnio))
                .filter(dto -> estadoOpcional == null || dto.estado().equalsIgnoreCase(estadoOpcional))
                .toList();

        return paginarEnMemoria(filas, pageable);
    }

    @Transactional
    public NominaResponseDTO subir(Long empleadoId, int periodoMes, int periodoAnio, MultipartFile archivo, Long adminId) {
        validarPdf(archivo);
        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new EmpleadoNotFoundException(empleadoId));

        String rutaGuardada = fileStorageService.guardar(archivo, SUBCARPETA_NOMINAS);
        return upsertNomina(empleado, periodoMes, periodoAnio, rutaGuardada, archivo.getOriginalFilename(), adminId);
    }

    public NominaDescargaDTO descargar(Long nominaId) {
        Nomina nomina = buscarOrThrow(nominaId);
        Resource recurso = fileStorageService.cargarComoResource(nomina.getRutaArchivo());
        return new NominaDescargaDTO(recurso, nomina.getNombreArchivoOriginal());
    }

    @Transactional
    public ProcesamientoZipDTO procesarZip(MultipartFile zip, Long adminId) {
        if (zip == null || zip.isEmpty()) {
            throw new ZipInvalidoException();
        }

        int asignados = 0;
        List<NominaPendienteDTO> pendientes = new ArrayList<>();
        boolean huboEntradas = false;

        try (ZipInputStream zis = new ZipInputStream(zip.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                huboEntradas = true;

                String nombreEntrada = nombreBaseDe(entry.getName());
                byte[] contenido = zis.readAllBytes();

                if (!nombreEntrada.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    pendientes.add(guardarPendiente(contenido, nombreEntrada, MOTIVO_NO_ES_PDF));
                    continue;
                }

                Matcher matcher = PATRON_NOMBRE_ZIP.matcher(nombreEntrada);
                if (!matcher.matches()) {
                    pendientes.add(guardarPendiente(contenido, nombreEntrada, MOTIVO_NOMBRE_NO_VALIDO));
                    continue;
                }

                String dniNie = matcher.group(1);
                int anio = Integer.parseInt(matcher.group(2));
                int mes = Integer.parseInt(matcher.group(3));

                Optional<Empleado> empleadoOpt = empleadoRepository.findByDniNieAndActivoTrue(dniNie);
                if (empleadoOpt.isEmpty()) {
                    pendientes.add(guardarPendiente(contenido, nombreEntrada, MOTIVO_EMPLEADO_NO_ENCONTRADO));
                    continue;
                }

                String rutaGuardada = fileStorageService.guardar(
                        new ByteArrayMultipartFile(nombreEntrada, CONTENT_TYPE_PDF, contenido), SUBCARPETA_NOMINAS);
                upsertNomina(empleadoOpt.get(), mes, anio, rutaGuardada, nombreEntrada, adminId);
                asignados++;
            }
        } catch (IOException e) {
            throw new ZipInvalidoException();
        }

        if (!huboEntradas) {
            throw new ZipInvalidoException();
        }

        return new ProcesamientoZipDTO(asignados, pendientes);
    }

    @Transactional
    public NominaResponseDTO asignarPendiente(String tempId, Long empleadoId, int periodoMes, int periodoAnio, Long adminId) {
        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new EmpleadoNotFoundException(empleadoId));

        String rutaPendiente = SUBCARPETA_PENDIENTES + "/" + tempId + ".pdf";
        String nuevaRuta;
        try {
            nuevaRuta = fileStorageService.mover(rutaPendiente, SUBCARPETA_NOMINAS);
        } catch (UncheckedIOException | IllegalArgumentException e) {
            throw new PendienteNoEncontradoException(tempId);
        }

        String nombreOriginal = nombresOriginalesPendientes.remove(tempId);
        if (nombreOriginal == null) {
            nombreOriginal = tempId + ".pdf";
        }

        return upsertNomina(empleado, periodoMes, periodoAnio, nuevaRuta, nombreOriginal, adminId);
    }

    @Transactional
    public NominaResponseDTO enviar(Long nominaId, Long adminId) {
        Nomina nomina = nominaRepository.findWithEmpleadoById(nominaId)
                .orElseThrow(() -> new NominaNotFoundException(nominaId));
        Resource archivo = fileStorageService.cargarComoResource(nomina.getRutaArchivo());

        try {
            emailService.enviarNomina(nomina.getEmpleado(), archivo, nomina.getNombreArchivoOriginal(),
                    nomina.getPeriodoMes(), nomina.getPeriodoAnio());
        } catch (MailException e) {
            throw new EnvioNominaFallidoException(e);
        }

        nomina.setEstado(EstadoNomina.ENVIADA);
        nomina.setFechaEnvio(LocalDateTime.now());
        nomina.setEnviadaPorAdminId(adminId);
        return toResponse(nominaRepository.save(nomina));
    }

    /**
     * Upsert compartido por la subida individual (subir), el matching automatico de ZIP
     * (procesarZip) y la asignacion manual de un pendiente (asignarPendiente): si el empleado
     * ya tenia nomina de ese periodo, se reemplaza el archivo fisico anterior y, si estaba
     * ENVIADA, vuelve a ADJUNTADA (el contenido enviado por email ya no es el que hay ahora).
     *
     * <p>El archivo antiguo NO se borra aqui directamente: el borrado de disco no es
     * transaccional (no lo revierte un rollback de BBDD), asi que si se hiciera antes o en
     * paralelo al {@code save()}, un fallo posterior (constraint, timeout, u otra entrada del
     * mismo ZIP fallando y arrastrando el rollback de esta) dejaria la fila revertida
     * apuntando a un fichero que ya no existe. En vez de eso, el borrado se registra para
     * ejecutarse solo tras el commit real de la transaccion (ver {@link #borrarTrasCommit}).
     */
    private NominaResponseDTO upsertNomina(
            Empleado empleado, int periodoMes, int periodoAnio, String rutaGuardada, String nombreArchivoOriginal, Long adminId) {
        Nomina nomina = nominaRepository
                .findByEmpleado_IdAndPeriodoMesAndPeriodoAnio(empleado.getId(), periodoMes, periodoAnio)
                .orElse(null);

        String rutaAnterior = nomina != null ? nomina.getRutaArchivo() : null;

        if (nomina == null) {
            nomina = new Nomina();
            nomina.setEmpleado(empleado);
            nomina.setPeriodoMes(periodoMes);
            nomina.setPeriodoAnio(periodoAnio);
        }

        nomina.setRutaArchivo(rutaGuardada);
        nomina.setNombreArchivoOriginal(nombreArchivoOriginal);
        nomina.setEstado(EstadoNomina.ADJUNTADA);
        nomina.setFechaSubida(LocalDateTime.now());
        nomina.setSubidaPorAdminId(adminId);
        nomina.setFechaEnvio(null);
        nomina.setEnviadaPorAdminId(null);

        NominaResponseDTO respuesta = toResponse(nominaRepository.save(nomina));
        borrarTrasCommit(rutaAnterior);
        return respuesta;
    }

    /**
     * Registra el borrado de {@code rutaArchivo} para que ocurra solo despues de que la
     * transaccion actual confirme (nunca antes, y nunca si acaba en rollback). Si no hay
     * transaccion activa (p.ej. un test unitario sin @Transactional), borra directamente.
     */
    private void borrarTrasCommit(String rutaArchivo) {
        if (rutaArchivo == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fileStorageService.eliminar(rutaArchivo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileStorageService.eliminar(rutaArchivo);
            }
        });
    }

    /**
     * El nombre fisico de todo pendiente es siempre "{uuid}.pdf" (fase3-2-back.md), sin
     * importar la extension real de la entrada original (un motivo NO_ES_PDF puede venir con
     * cualquier extension, o ninguna): asignarPendiente busca siempre por esa extension fija,
     * asi que aqui se fuerza pasando un nombre sintetico ".pdf" a FileStorageService (que solo
     * lo usa para derivar la extension fisica) en vez de nombreOriginal. El nombre real que ve
     * el Admin se preserva aparte, en el DTO y en nombresOriginalesPendientes.
     */
    private NominaPendienteDTO guardarPendiente(byte[] contenido, String nombreOriginal, String motivo) {
        String rutaGuardada = fileStorageService.guardar(
                new ByteArrayMultipartFile("pendiente.pdf", CONTENT_TYPE_PDF, contenido), SUBCARPETA_PENDIENTES);
        String tempId = nombreBaseDe(rutaGuardada).replace(".pdf", "");
        nombresOriginalesPendientes.put(tempId, nombreOriginal);
        return new NominaPendienteDTO(tempId, nombreOriginal, motivo);
    }

    private void validarPdf(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ArchivoNoValidoException();
        }
        String nombre = archivo.getOriginalFilename();
        boolean extensionValida = nombre != null && nombre.toLowerCase(Locale.ROOT).endsWith(".pdf");
        boolean contentTypeValido = CONTENT_TYPE_PDF.equalsIgnoreCase(archivo.getContentType());
        if (!extensionValida || !contentTypeValido) {
            throw new ArchivoNoValidoException();
        }
    }

    private Nomina buscarOrThrow(Long nominaId) {
        return nominaRepository.findById(nominaId)
                .orElseThrow(() -> new NominaNotFoundException(nominaId));
    }

    private static Page<NominaListadoDTO> paginarEnMemoria(List<NominaListadoDTO> filas, Pageable pageable) {
        int desde = (int) pageable.getOffset();
        if (desde >= filas.size()) {
            return new PageImpl<>(List.of(), pageable, filas.size());
        }
        int hasta = Math.min(desde + pageable.getPageSize(), filas.size());
        return new PageImpl<>(filas.subList(desde, hasta), pageable, filas.size());
    }

    private static NominaListadoDTO toListadoDTO(Empleado empleado, Nomina nomina, int periodoMes, int periodoAnio) {
        if (nomina == null) {
            return new NominaListadoDTO(empleado.getId(), nombreCompleto(empleado), empleado.getEmail(),
                    periodoMes, periodoAnio, ESTADO_PENDIENTE, null, null, null, null);
        }
        return new NominaListadoDTO(empleado.getId(), nombreCompleto(empleado), empleado.getEmail(),
                periodoMes, periodoAnio, nomina.getEstado().name(), nomina.getId(),
                nomina.getNombreArchivoOriginal(), nomina.getFechaSubida(), nomina.getFechaEnvio());
    }

    private static NominaResponseDTO toResponse(Nomina nomina) {
        return new NominaResponseDTO(nomina.getId(), nomina.getEmpleado().getId(), nomina.getPeriodoMes(),
                nomina.getPeriodoAnio(), nomina.getEstado(), nomina.getNombreArchivoOriginal(),
                nomina.getFechaSubida(), nomina.getFechaEnvio());
    }

    private static String nombreCompleto(Empleado empleado) {
        return empleado.getNombre() + " " + empleado.getApellidos();
    }

    private static String nombreBaseDe(String rutaOEntrada) {
        String normalizado = rutaOEntrada.replace('\\', '/');
        int barraIndex = normalizado.lastIndexOf('/');
        return barraIndex >= 0 ? normalizado.substring(barraIndex + 1) : normalizado;
    }
}
