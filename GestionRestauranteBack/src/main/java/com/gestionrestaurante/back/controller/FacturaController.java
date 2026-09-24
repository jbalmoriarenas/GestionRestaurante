package com.gestionrestaurante.back.controller;

import com.gestionrestaurante.back.Wuris;
import com.gestionrestaurante.back.dto.CambiarEstadoPagoRequestDTO;
import com.gestionrestaurante.back.dto.FacturaCreateDTO;
import com.gestionrestaurante.back.dto.FacturaDescargaDTO;
import com.gestionrestaurante.back.dto.FacturaDetalleDTO;
import com.gestionrestaurante.back.dto.FacturaExtraccionPreviewDTO;
import com.gestionrestaurante.back.dto.FacturaListadoDTO;
import com.gestionrestaurante.back.entity.EstadoPago;
import com.gestionrestaurante.back.entity.TipoProveedor;
import com.gestionrestaurante.back.security.CurrentUser;
import com.gestionrestaurante.back.service.FacturaExportService;
import com.gestionrestaurante.back.service.FacturaExtractionService;
import com.gestionrestaurante.back.service.FacturaService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequiredArgsConstructor
public class FacturaController {

    private final FacturaService facturaService;
    private final FacturaExtractionService facturaExtractionService;
    private final FacturaExportService facturaExportService;

    @GetMapping(Wuris.Facturas.BASE)
    @PreAuthorize("hasAuthority('FACTURAS:EDICION')")
    public Page<FacturaListadoDTO> listar(
            @RequestParam TipoProveedor tipo,
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) EstadoPago estadoPago,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer anio,
            @PageableDefault(size = 25) Pageable pageable) {
        Pageable pageableAcotado = pageable.getPageSize() > 100
                ? org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), 100, pageable.getSort())
                : pageable;
        return facturaService.listar(tipo, proveedorId, estadoPago, mes, anio, pageableAcotado);
    }

    @GetMapping(Wuris.Facturas.BY_ID)
    @PreAuthorize("hasAuthority('FACTURAS:EDICION')")
    public FacturaDetalleDTO obtenerDetalle(@PathVariable Long id) {
        return facturaService.obtenerDetalle(id);
    }

    @PostMapping(value = Wuris.Facturas.BASE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('FACTURAS:EDICION')")
    @ResponseStatus(HttpStatus.CREATED)
    public FacturaDetalleDTO crear(Authentication authentication, @Valid @ModelAttribute FacturaCreateDTO request) {
        return facturaService.crear(request, CurrentUser.empleadoId(authentication));
    }

    @PatchMapping(Wuris.Facturas.BY_ID_ESTADO_PAGO)
    @PreAuthorize("hasAuthority('FACTURAS:EDICION')")
    public FacturaDetalleDTO cambiarEstadoPago(@PathVariable Long id, @Valid @RequestBody CambiarEstadoPagoRequestDTO request) {
        return facturaService.cambiarEstadoPago(id, request.estadoPago());
    }

    @GetMapping(Wuris.Facturas.BY_ID_ARCHIVO)
    @PreAuthorize("hasAuthority('FACTURAS:EDICION')")
    public ResponseEntity<Resource> descargarArchivo(@PathVariable Long id) {
        FacturaDescargaDTO descarga = facturaService.descargarArchivo(id);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(descarga.nombreArchivoOriginal(), StandardCharsets.UTF_8)
                .build();
        MediaType mediaType = MediaTypeFactory.getMediaType(descarga.nombreArchivoOriginal())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(descarga.recurso());
    }

    @PostMapping(value = Wuris.Facturas.EXTRAER, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('FACTURAS:EDICION')")
    public FacturaExtraccionPreviewDTO extraer(
            @RequestParam TipoProveedor tipo, @RequestPart MultipartFile archivo) throws java.io.IOException {
        var resultado = facturaExtractionService.extraer(archivo.getInputStream(), archivo.getContentType(), tipo);
        return new FacturaExtraccionPreviewDTO(resultado.fechaImpresionSugerida(), resultado.fechaEntregaSugerida(),
                resultado.importeBaseSugerido(), resultado.ivaSugerido(), resultado.importeTotalSugerido());
    }

    @GetMapping(Wuris.Facturas.EXPORTAR)
    @PreAuthorize("hasAuthority('FACTURAS:EDICION')")
    public ResponseEntity<StreamingResponseBody> exportar(
            @RequestParam TipoProveedor tipo,
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) EstadoPago estadoPago,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer anio,
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) List<String> cabecera) {
        String nombreArchivo = "facturas_" + tipo.name().toLowerCase() + "_"
                + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".xlsx";
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(nombreArchivo, StandardCharsets.UTF_8)
                .build();
        StreamingResponseBody body =
                out -> facturaExportService.exportar(out, tipo, proveedorId, estadoPago, mes, anio, titulo, cabecera);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(body);
    }

    /**
     * {@code mes}/{@code anio} son obligatorios (a diferencia de /exportar): no tiene sentido
     * descargar de golpe el archivo original de todas las facturas de todos los periodos.
     * {@code nombreCarpeta} lo genera el front (p.ej. "facturas_julio_2026", mismo criterio que
     * {@code titulo} en /exportar) y se usa tanto como nombre del .zip descargado como carpeta
     * interna que agrupa todos los archivos dentro de el.
     */
    @GetMapping(Wuris.Facturas.EXPORTAR_ZIP)
    @PreAuthorize("hasAuthority('FACTURAS:EDICION')")
    public ResponseEntity<StreamingResponseBody> exportarZip(
            @RequestParam TipoProveedor tipo,
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) EstadoPago estadoPago,
            @RequestParam int mes,
            @RequestParam int anio,
            @RequestParam(required = false) String nombreCarpeta) {
        String carpetaSaneada = FacturaExportService.sanearNombreCarpeta(nombreCarpeta);
        String carpeta = carpetaSaneada != null ? carpetaSaneada : "facturas_" + anio + "-" + mes;
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(carpeta + ".zip", StandardCharsets.UTF_8)
                .build();
        StreamingResponseBody body =
                out -> facturaExportService.exportarArchivos(out, tipo, proveedorId, estadoPago, mes, anio, carpeta);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(body);
    }
}
