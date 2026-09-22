package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.entity.EstadoPago;
import com.gestionrestaurante.back.entity.Factura;
import com.gestionrestaurante.back.entity.FacturaCampoDinamico;
import com.gestionrestaurante.back.entity.TipoProveedor;
import com.gestionrestaurante.back.repository.FacturaCampoDinamicoRepository;
import com.gestionrestaurante.back.repository.FacturaRepository;
import java.io.IOException;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exportacion a .xlsx de todas las facturas que cumplen los filtros (back/fase2-2-back.md,
 * extendido en back/fase2-3-back.md con la columna "Datos adicionales"). Ignora la
 * paginacion de la UI a proposito - el proposito del boton es obtener el detalle completo
 * fuera de la tabla de 25 filas, no una copia en Excel de lo que ya se ve en pantalla.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacturaExportService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] CABECERA = {
            "ID", "Empresa", "CIF", "Email contacto", "Telefono", "Tipo", "Nº factura", "Producto",
            "Fecha factura", "Fecha vencimiento", "Importe base", "IVA", "Importe total", "Mes", "Año",
            "Estado pago", "Nombre archivo original", "Fecha subida", "Datos adicionales"
    };

    private final FacturaRepository facturaRepository;
    private final FacturaCampoDinamicoRepository facturaCampoDinamicoRepository;

    public void exportar(
            OutputStream out, TipoProveedor tipo, Long proveedorIdOpcional,
            EstadoPago estadoOpcional, Integer mesOpcional, Integer anioOpcional) throws IOException {
        List<Factura> facturas = facturaRepository
                .findByFiltros(tipo, proveedorIdOpcional, estadoOpcional, mesOpcional, anioOpcional, Pageable.unpaged())
                .getContent();

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Facturas");
            CellStyle estiloCabecera = crearEstiloCabecera(workbook);

            escribirFila(sheet, 0, CABECERA, estiloCabecera);

            int filaIndex = 1;
            for (Factura factura : facturas) {
                escribirFilaFactura(sheet, filaIndex++, factura);
            }

            workbook.write(out);
            workbook.dispose();
        }
    }

    private void escribirFilaFactura(Sheet sheet, int filaIndex, Factura factura) {
        Row row = sheet.createRow(filaIndex);
        int col = 0;
        setCell(row, col++, factura.getId());
        setCell(row, col++, factura.getProveedor().getNombre());
        setCell(row, col++, factura.getProveedor().getCif());
        setCell(row, col++, factura.getProveedor().getEmailContacto());
        setCell(row, col++, factura.getProveedor().getTelefono());
        setCell(row, col++, factura.getProveedor().getTipo().name());
        setCell(row, col++, factura.getNumeroFactura());
        setCell(row, col++, factura.getProducto());
        setCell(row, col++, formatearFecha(factura.getFechaFactura()));
        setCell(row, col++, formatearFecha(factura.getFechaVencimiento()));
        setCell(row, col++, factura.getImporteBase());
        setCell(row, col++, factura.getIva());
        setCell(row, col++, factura.getImporteTotal());
        setCell(row, col++, factura.getMes());
        setCell(row, col++, factura.getAnio());
        setCell(row, col++, factura.getEstadoPago().name());
        setCell(row, col++, factura.getNombreArchivoOriginal());
        setCell(row, col++, factura.getFechaSubida() != null ? factura.getFechaSubida().toString() : null);
        setCell(row, col, datosAdicionalesTexto(factura.getId()));
    }

    private String datosAdicionalesTexto(Long facturaId) {
        List<FacturaCampoDinamico> campos = facturaCampoDinamicoRepository.findByFactura_Id(facturaId);
        if (campos.isEmpty()) {
            return "";
        }
        return campos.stream()
                .sorted(Comparator.comparing(FacturaCampoDinamico::getNombreCampo))
                .map(campo -> campo.getNombreCampo() + ": " + campo.getValor())
                .collect(Collectors.joining("; "));
    }

    private static void escribirFila(Sheet sheet, int filaIndex, String[] valores, CellStyle estilo) {
        Row row = sheet.createRow(filaIndex);
        for (int i = 0; i < valores.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(valores[i]);
            cell.setCellStyle(estilo);
        }
    }

    private static void setCell(Row row, int col, String valor) {
        row.createCell(col).setCellValue(valor != null ? valor : "");
    }

    private static void setCell(Row row, int col, Number valor) {
        if (valor != null) {
            row.createCell(col).setCellValue(valor.doubleValue());
        } else {
            row.createCell(col);
        }
    }

    private static String formatearFecha(java.time.LocalDate fecha) {
        return fecha != null ? fecha.format(FORMATO_FECHA) : null;
    }

    private static CellStyle crearEstiloCabecera(SXSSFWorkbook workbook) {
        Font fuenteNegrita = workbook.createFont();
        fuenteNegrita.setBold(true);
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFont(fuenteNegrita);
        return estilo;
    }
}
