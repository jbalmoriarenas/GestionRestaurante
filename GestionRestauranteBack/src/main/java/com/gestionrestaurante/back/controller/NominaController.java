package com.gestionrestaurante.back.controller;

import com.gestionrestaurante.back.Wuris;
import com.gestionrestaurante.back.dto.AsignarPendienteRequestDTO;
import com.gestionrestaurante.back.dto.NominaDescargaDTO;
import com.gestionrestaurante.back.dto.NominaListadoDTO;
import com.gestionrestaurante.back.dto.NominaResponseDTO;
import com.gestionrestaurante.back.dto.ProcesamientoZipDTO;
import com.gestionrestaurante.back.security.CurrentUser;
import com.gestionrestaurante.back.service.NominaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class NominaController {

    // Mismo rango ya usado en VacacionesController para el filtro de anio.
    private static final int ANIO_MINIMO = 2000;
    private static final int ANIO_MAXIMO = 2100;
    private static final String REGEX_ESTADO = "(?i)PENDIENTE|ADJUNTADA|ENVIADA";
    // tempId siempre es el UUID que genera LocalFileStorageService.guardar (ver
    // NominaService.guardarPendiente); LocalFileStorageService.resolverDentroDeBase ya protege
    // contra path traversal, pero validar el formato aqui evita depender solo de esa red de
    // seguridad interna y da un 400 claro en vez de un 404 confuso ante un valor con basura.
    private static final String REGEX_UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final NominaService nominaService;

    @GetMapping(Wuris.Nominas.BASE)
    @PreAuthorize("hasAuthority('NOMINAS:EDICION')")
    public Page<NominaListadoDTO> listar(
            @RequestParam(required = false) Long empleadoId,
            @RequestParam(required = false) @Min(1) @Max(12) Integer periodoMes,
            @RequestParam(required = false) @Min(ANIO_MINIMO) @Max(ANIO_MAXIMO) Integer periodoAnio,
            @RequestParam(required = false) @Pattern(regexp = REGEX_ESTADO, message = "Estado no valido") String estado,
            @PageableDefault(size = 20) Pageable pageable) {
        LocalDate hoy = LocalDate.now();
        int mes = periodoMes != null ? periodoMes : hoy.getMonthValue();
        int anio = periodoAnio != null ? periodoAnio : hoy.getYear();
        return nominaService.listar(empleadoId, mes, anio, estado, pageable);
    }

    @PostMapping(value = Wuris.Nominas.BASE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('NOMINAS:EDICION')")
    public NominaResponseDTO subir(
            Authentication authentication,
            @RequestParam Long empleadoId,
            @RequestParam @Min(1) @Max(12) Integer periodoMes,
            @RequestParam @Min(ANIO_MINIMO) @Max(ANIO_MAXIMO) Integer periodoAnio,
            @RequestPart MultipartFile archivo) {
        return nominaService.subir(empleadoId, periodoMes, periodoAnio, archivo, CurrentUser.empleadoId(authentication));
    }

    @PostMapping(value = Wuris.Nominas.ZIP, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('NOMINAS:EDICION')")
    public ProcesamientoZipDTO subirZip(Authentication authentication, @RequestPart MultipartFile archivo) {
        return nominaService.procesarZip(archivo, CurrentUser.empleadoId(authentication));
    }

    @PostMapping(Wuris.Nominas.PENDIENTE_ASIGNAR)
    @PreAuthorize("hasAuthority('NOMINAS:EDICION')")
    public NominaResponseDTO asignarPendiente(
            Authentication authentication,
            @PathVariable @Pattern(regexp = REGEX_UUID, message = "Identificador de pendiente no valido") String tempId,
            @Valid @RequestBody AsignarPendienteRequestDTO request) {
        return nominaService.asignarPendiente(
                tempId, request.empleadoId(), request.periodoMes(), request.periodoAnio(),
                CurrentUser.empleadoId(authentication));
    }

    @GetMapping(Wuris.Nominas.BY_ID_DESCARGAR)
    @PreAuthorize("hasAuthority('NOMINAS:EDICION')")
    public ResponseEntity<Resource> descargar(@PathVariable Long id) {
        NominaDescargaDTO descarga = nominaService.descargar(id);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(descarga.nombreArchivoOriginal(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(descarga.recurso());
    }

    @PostMapping(Wuris.Nominas.BY_ID_ENVIAR)
    @PreAuthorize("hasAuthority('NOMINAS:EDICION')")
    public NominaResponseDTO enviar(Authentication authentication, @PathVariable Long id) {
        return nominaService.enviar(id, CurrentUser.empleadoId(authentication));
    }
}
