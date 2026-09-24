package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.dto.FacturaCreateDTO;
import com.gestionrestaurante.back.dto.FacturaDescargaDTO;
import com.gestionrestaurante.back.dto.FacturaDetalleDTO;
import com.gestionrestaurante.back.dto.FacturaListadoDTO;
import com.gestionrestaurante.back.entity.EstadoExtraccion;
import com.gestionrestaurante.back.entity.EstadoPago;
import com.gestionrestaurante.back.entity.Factura;
import com.gestionrestaurante.back.entity.Proveedor;
import com.gestionrestaurante.back.entity.TipoProveedor;
import com.gestionrestaurante.back.exception.ArchivoFacturaNoValidoException;
import com.gestionrestaurante.back.exception.FacturaNotFoundException;
import com.gestionrestaurante.back.repository.FacturaRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Fase 2 (Facturas): listado paginado en BBDD con filtros por tipo/proveedor/estado de
 * pago/periodo (back/fase2-1-back.md), alta manual con archivo obligatorio, y extension del
 * alta para marcar el origen OCR de los valores fijos (back/fase2-3-back.md). No existe ningun
 * mecanismo de campos dinamicos: el archivo guardado (rutaArchivo) es la unica fuente de verdad
 * para cualquier dato de la factura que no sea proveedor/fecha/importes - si se subio como foto
 * se convierte a PDF antes de guardarse (ver {@link #guardarArchivo}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacturaService {

    private static final Set<String> CONTENT_TYPES_VALIDOS =
            Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    private final FacturaRepository facturaRepository;
    private final ProveedorService proveedorService;
    private final FileStorageService fileStorageService;

    public Page<FacturaListadoDTO> listar(
            TipoProveedor tipo, Long proveedorIdOpcional, EstadoPago estadoOpcional,
            Integer mesOpcional, Integer anioOpcional, Pageable pageable) {
        return facturaRepository.findByFiltros(tipo, proveedorIdOpcional, estadoOpcional, mesOpcional, anioOpcional, pageable)
                .map(FacturaService::toListadoDTO);
    }

    public FacturaDetalleDTO obtenerDetalle(Long facturaId) {
        return toDetalleDTO(buscarOrThrow(facturaId));
    }

    @Transactional
    public FacturaDetalleDTO crear(FacturaCreateDTO request, Long adminId) {
        validarArchivo(request.archivo());
        Proveedor proveedor = proveedorService.obtenerOrThrow(request.proveedorId());

        Factura factura = new Factura();
        factura.setProveedor(proveedor);
        factura.setNumeroFactura(request.numeroFactura());
        factura.setFechaImpresion(request.fechaImpresion());
        factura.setFechaEntrega(request.fechaEntrega());
        factura.setImporteBase(request.importeBase());
        factura.setIva(request.iva());
        factura.setImporteTotal(request.importeTotal());
        // mes/anio siempre derivados de fechaImpresion en el servidor, nunca aceptados del
        // cliente (back/fase2-1-back.md).
        factura.setMes(request.fechaImpresion().getMonthValue());
        factura.setAnio(request.fechaImpresion().getYear());
        factura.setEstadoPago(request.estadoPago() != null ? request.estadoPago() : EstadoPago.PENDIENTE);
        boolean origenOcr = Boolean.TRUE.equals(request.origenOcr());
        factura.setEstadoExtraccion(origenOcr ? EstadoExtraccion.REVISADA : EstadoExtraccion.MANUAL);

        guardarArchivo(factura, request.archivo());
        factura.setSubidaPorAdminId(adminId);
        factura.setFechaSubida(LocalDateTime.now());

        return toDetalleDTO(facturaRepository.save(factura));
    }

    @Transactional
    public FacturaDetalleDTO cambiarEstadoPago(Long facturaId, EstadoPago nuevoEstado) {
        Factura factura = buscarOrThrow(facturaId);
        factura.setEstadoPago(nuevoEstado);
        return toDetalleDTO(facturaRepository.save(factura));
    }

    public FacturaDescargaDTO descargarArchivo(Long facturaId) {
        Factura factura = buscarOrThrow(facturaId);
        Resource recurso = fileStorageService.cargarComoResource(factura.getRutaArchivo());
        return new FacturaDescargaDTO(recurso, factura.getNombreArchivoOriginal());
    }

    private void validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ArchivoFacturaNoValidoException();
        }
        if (!CONTENT_TYPES_VALIDOS.contains(archivo.getContentType())) {
            throw new ArchivoFacturaNoValidoException();
        }
    }

    /**
     * Si {@code archivo} es una foto (JPEG/PNG/WEBP), se convierte a PDF antes de guardarla
     * (confirmado con el usuario, ver comentario de {@link Factura}); si ya es un PDF, se
     * guarda tal cual. En ambos casos deja {@code factura.rutaArchivo} y
     * {@code factura.nombreArchivoOriginal} listos.
     */
    private void guardarArchivo(Factura factura, MultipartFile archivo) {
        String contentType = archivo.getContentType();
        if (contentType != null && contentType.startsWith("image/")) {
            byte[] pdf;
            try {
                pdf = ImagenPdfConverter.convertirAPdf(archivo.getBytes());
            } catch (IOException e) {
                throw new ArchivoFacturaNoValidoException(e);
            }
            String nombrePdf = ImagenPdfConverter.nombrePdfDesde(archivo.getOriginalFilename());
            factura.setRutaArchivo(fileStorageService.guardar(pdf, nombrePdf, "facturas"));
            factura.setNombreArchivoOriginal(nombrePdf);
        } else {
            factura.setRutaArchivo(fileStorageService.guardar(archivo, "facturas"));
            factura.setNombreArchivoOriginal(archivo.getOriginalFilename());
        }
    }

    private Factura buscarOrThrow(Long facturaId) {
        return facturaRepository.findById(facturaId)
                .orElseThrow(() -> new FacturaNotFoundException(facturaId));
    }

    private static FacturaListadoDTO toListadoDTO(Factura factura) {
        return new FacturaListadoDTO(factura.getId(), factura.getProveedor().getNombre(),
                factura.getFechaImpresion(), factura.getFechaEntrega(),
                factura.getImporteBase(), factura.getIva(), factura.getImporteTotal(), factura.getEstadoPago());
    }

    private static FacturaDetalleDTO toDetalleDTO(Factura factura) {
        return new FacturaDetalleDTO(factura.getId(), factura.getProveedor().getId(), factura.getProveedor().getNombre(),
                factura.getProveedor().getTipo(), factura.getNumeroFactura(),
                factura.getFechaImpresion(), factura.getFechaEntrega(),
                factura.getImporteBase(), factura.getIva(), factura.getImporteTotal(), factura.getEstadoPago(),
                factura.getEstadoExtraccion(), factura.getNombreArchivoOriginal(), factura.getFechaSubida());
    }
}
