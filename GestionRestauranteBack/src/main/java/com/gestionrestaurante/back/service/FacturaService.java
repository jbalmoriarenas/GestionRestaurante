package com.gestionrestaurante.back.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestionrestaurante.back.dto.FacturaCampoDinamicoDTO;
import com.gestionrestaurante.back.dto.FacturaCreateDTO;
import com.gestionrestaurante.back.dto.FacturaDescargaDTO;
import com.gestionrestaurante.back.dto.FacturaDetalleDTO;
import com.gestionrestaurante.back.dto.FacturaListadoDTO;
import com.gestionrestaurante.back.entity.EstadoExtraccion;
import com.gestionrestaurante.back.entity.EstadoPago;
import com.gestionrestaurante.back.entity.Factura;
import com.gestionrestaurante.back.entity.FacturaCampoDinamico;
import com.gestionrestaurante.back.entity.Proveedor;
import com.gestionrestaurante.back.entity.TipoProveedor;
import com.gestionrestaurante.back.exception.ArchivoFacturaNoValidoException;
import com.gestionrestaurante.back.exception.FacturaNotFoundException;
import com.gestionrestaurante.back.repository.FacturaCampoDinamicoRepository;
import com.gestionrestaurante.back.repository.FacturaRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * alta para aceptar campos dinamicos detectados por OCR (back/fase2-3-back.md).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacturaService {

    private static final Set<String> CONTENT_TYPES_VALIDOS =
            Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    private final FacturaRepository facturaRepository;
    private final FacturaCampoDinamicoRepository facturaCampoDinamicoRepository;
    private final ProveedorService proveedorService;
    private final FileStorageService fileStorageService;

    // Instancia propia, no inyectada: spring-boot-starter-webmvc (a diferencia del clasico
    // spring-boot-starter-web) no autoconfigura un bean ObjectMapper sin spring-boot-starter-json,
    // y este es el unico uso de Jackson del proyecto hasta ahora (parseo interno de un campo de
    // formulario, no serializacion HTTP) - no justifica anadir esa dependencia solo por esto.
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        factura.setProducto(request.producto());
        factura.setFechaFactura(request.fechaFactura());
        factura.setFechaVencimiento(request.fechaVencimiento());
        factura.setImporteBase(request.importeBase());
        factura.setIva(request.iva());
        factura.setImporteTotal(request.importeTotal());
        // mes/anio siempre derivados de fechaFactura en el servidor, nunca aceptados del
        // cliente (back/fase2-1-back.md).
        factura.setMes(request.fechaFactura().getMonthValue());
        factura.setAnio(request.fechaFactura().getYear());
        factura.setEstadoPago(request.estadoPago() != null ? request.estadoPago() : EstadoPago.PENDIENTE);
        boolean origenOcr = Boolean.TRUE.equals(request.origenOcr());
        factura.setEstadoExtraccion(origenOcr ? EstadoExtraccion.REVISADA : EstadoExtraccion.MANUAL);
        factura.setDatosExtraidosRaw(origenOcr ? request.datosExtraidosRawJson() : null);

        String rutaGuardada = fileStorageService.guardar(request.archivo(), "facturas");
        factura.setRutaArchivo(rutaGuardada);
        factura.setNombreArchivoOriginal(request.archivo().getOriginalFilename());
        factura.setSubidaPorAdminId(adminId);
        factura.setFechaSubida(LocalDateTime.now());

        Factura guardada = facturaRepository.save(factura);
        upsertCamposDinamicos(guardada, parsearCamposAdicionales(request.camposAdicionalesJson()));
        return toDetalleDTO(guardada);
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

    private void upsertCamposDinamicos(Factura factura, Map<String, String> camposAdicionales) {
        if (camposAdicionales == null || camposAdicionales.isEmpty()) {
            return;
        }
        camposAdicionales.forEach((nombreCampo, valor) -> {
            FacturaCampoDinamico campo = facturaCampoDinamicoRepository
                    .findByFactura_IdAndNombreCampo(factura.getId(), nombreCampo)
                    .orElseGet(() -> {
                        FacturaCampoDinamico nuevo = new FacturaCampoDinamico();
                        nuevo.setFactura(factura);
                        nuevo.setNombreCampo(nombreCampo);
                        nuevo.setFechaCreacion(LocalDateTime.now());
                        return nuevo;
                    });
            campo.setValor(valor);
            facturaCampoDinamicoRepository.save(campo);
        });
    }

    private Map<String, String> parsearCamposAdicionales(String camposAdicionalesJson) {
        if (camposAdicionalesJson == null || camposAdicionalesJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(camposAdicionalesJson, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (JsonProcessingException e) {
            // Un JSON de campos adicionales corrupto no debe tumbar el alta de la factura en
            // si (los campos fijos ya se validaron y son correctos) - se ignora y se guarda
            // sin campos dinamicos, en vez de fallar toda la operacion.
            return Map.of();
        }
    }

    private void validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ArchivoFacturaNoValidoException();
        }
        if (!CONTENT_TYPES_VALIDOS.contains(archivo.getContentType())) {
            throw new ArchivoFacturaNoValidoException();
        }
    }

    private Factura buscarOrThrow(Long facturaId) {
        return facturaRepository.findById(facturaId)
                .orElseThrow(() -> new FacturaNotFoundException(facturaId));
    }

    private static FacturaListadoDTO toListadoDTO(Factura factura) {
        return new FacturaListadoDTO(factura.getId(), factura.getProveedor().getNombre(), factura.getProducto(),
                factura.getImporteTotal(), factura.getFechaFactura(), factura.getEstadoPago());
    }

    private FacturaDetalleDTO toDetalleDTO(Factura factura) {
        List<FacturaCampoDinamicoDTO> camposDinamicos = facturaCampoDinamicoRepository
                .findByFactura_Id(factura.getId()).stream()
                .map(campo -> new FacturaCampoDinamicoDTO(campo.getNombreCampo(), campo.getValor()))
                .toList();
        return new FacturaDetalleDTO(factura.getId(), factura.getProveedor().getId(), factura.getProveedor().getNombre(),
                factura.getProveedor().getTipo(), factura.getNumeroFactura(), factura.getProducto(),
                factura.getFechaFactura(), factura.getFechaVencimiento(), factura.getImporteBase(), factura.getIva(),
                factura.getImporteTotal(), factura.getEstadoPago(), factura.getEstadoExtraccion(),
                factura.getNombreArchivoOriginal(), factura.getFechaSubida(), camposDinamicos);
    }
}
