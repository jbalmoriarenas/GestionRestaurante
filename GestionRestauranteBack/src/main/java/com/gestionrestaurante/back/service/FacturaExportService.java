package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.entity.EstadoPago;
import com.gestionrestaurante.back.entity.Factura;
import com.gestionrestaurante.back.entity.TipoProveedor;
import com.gestionrestaurante.back.repository.FacturaRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exportacion a .xlsx de todas las facturas que cumplen los filtros (back/fase2-2-back.md).
 * Ignora la paginacion de la UI a proposito - el proposito del boton es obtener el detalle
 * completo fuera de la tabla de 25 filas, no una copia en Excel de lo que ya se ve en pantalla.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacturaExportService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Cabecera de columnas en castellano, usada solo como fallback si el front no manda
     * {@code cabeceras} (p.ej. una llamada manual a la API) - en uso normal, el front siempre
     * las traduce en el idioma activo de la app y las manda ya resueltas, igual que
     * {@code titulo}, porque el back no tiene ningun mecanismo de i18n para el resto de la app.
     *
     * <p>IMPORTANTE: el orden de estas 13 columnas tiene que coincidir exactamente, columna a
     * columna, con el orden en que {@link #escribirFilaFactura} escribe los valores y con
     * {@code cabecerasExportar()} en facturas-admin.ts (front) - solo se valida aqui que el
     * NUMERO de cabeceras coincida ({@code cabeceras.size() == CABECERA_DEFECTO.length}), no el
     * orden. Si reordenas una columna aqui, reordena las otras dos. */
    private static final String[] CABECERA_DEFECTO = {
            "Empresa", "Telefono", "Tipo",
            "Fecha impresión", "Fecha entrega", "Importe base", "IVA", "Importe total",
            "Mes", "Año", "Estado pago", "Nombre archivo original", "Fecha subida"
    };

    private final FacturaRepository facturaRepository;
    private final FileStorageService fileStorageService;

    /**
     * {@code titulo} y {@code cabeceras} son texto libre ya traducido por el front (p.ej.
     * titulo="Facturas periodo julio 2026"); si no se mandan, se usan {@link #CABECERA_DEFECTO}
     * y no se escribe fila de titulo.
     */
    public void exportar(
            OutputStream out, TipoProveedor tipo, Long proveedorIdOpcional, EstadoPago estadoOpcional,
            Integer mesOpcional, Integer anioOpcional, String titulo, List<String> cabeceras) throws IOException {
        List<Factura> facturas = facturaRepository
                .findByFiltros(tipo, proveedorIdOpcional, estadoOpcional, mesOpcional, anioOpcional, Pageable.unpaged())
                .getContent();
        String[] cabecera = cabeceras != null && cabeceras.size() == CABECERA_DEFECTO.length
                ? cabeceras.toArray(new String[0])
                : CABECERA_DEFECTO;

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Facturas");
            CellStyle estiloCabecera = crearEstiloCabecera(workbook);

            int filaIndex = 0;
            if (titulo != null && !titulo.isBlank()) {
                escribirTitulo(sheet, filaIndex++, titulo, crearEstiloTitulo(workbook), cabecera.length);
            }
            escribirFila(sheet, filaIndex++, cabecera, estiloCabecera);

            for (Factura factura : facturas) {
                escribirFilaFactura(sheet, filaIndex++, factura);
            }

            workbook.write(out);
            workbook.dispose();
        }
    }

    /**
     * ZIP con el archivo original (PDF/imagen) de cada factura que cumple los filtros, todos
     * dentro de una unica carpeta {@code nombreCarpeta} (p.ej. "facturas_julio_2026", generado
     * por el front igual que {@code titulo}/{@code cabeceras} de {@link #exportar}). Un archivo
     * ilegible o borrado del disco no aborta el ZIP entero: se omite esa factura (best-effort,
     * mismo criterio que la extraccion OCR) en vez de fallar toda la descarga por un archivo.
     */
    public void exportarArchivos(
            OutputStream out, TipoProveedor tipo, Long proveedorIdOpcional, EstadoPago estadoOpcional,
            int mes, int anio, String nombreCarpeta) throws IOException {
        List<Factura> facturas = facturaRepository
                .findByFiltros(tipo, proveedorIdOpcional, estadoOpcional, mes, anio, Pageable.unpaged())
                .getContent();
        String carpeta = sanearNombreCarpeta(nombreCarpeta);

        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Factura factura : facturas) {
                agregarArchivoAlZip(zip, factura, carpeta);
            }
        }
    }

    /**
     * {@code nombreCarpeta} llega tal cual desde un {@code @RequestParam} (en uso normal el
     * front siempre manda "facturas_&lt;mes&gt;_&lt;anio&gt;", pero nada impide llamar a la API
     * directamente con otra cosa): se quitan barras y secuencias ".." para que no pueda acabar
     * escribiendo entradas del ZIP fuera de la carpeta interna prevista al extraerlo con una
     * herramienta que no sanee rutas.
     */
    public static String sanearNombreCarpeta(String nombreCarpeta) {
        if (nombreCarpeta == null || nombreCarpeta.isBlank()) {
            return null;
        }
        String limpio = nombreCarpeta.replace("\\", "_").replace("/", "_").replaceAll("\\.\\.+", "_");
        return limpio.isBlank() ? null : limpio;
    }

    private void agregarArchivoAlZip(ZipOutputStream zip, Factura factura, String nombreCarpeta) throws IOException {
        Resource recurso;
        try {
            recurso = fileStorageService.cargarComoResource(factura.getRutaArchivo());
        } catch (UncheckedIOException e) {
            // Archivo ilegible o borrado del disco (fuera de la aplicacion): se omite esa
            // factura en vez de abortar todo el ZIP por un archivo (best-effort, mismo
            // criterio que la extraccion OCR). Cualquier otra RuntimeException (bug real) sigue
            // propagandose.
            return;
        }
        String nombreEntrada = nombreCarpeta + "/" + nombreArchivoZip(factura);
        zip.putNextEntry(new ZipEntry(nombreEntrada));
        try (InputStream in = recurso.getInputStream()) {
            in.transferTo(zip);
        }
        zip.closeEntry();
    }

    /** Antepone el ID de la factura al nombre original para garantizar que no colisionan dos
     * entradas del ZIP aunque dos facturas se subieran con el mismo nombre de archivo; tambien
     * quita barras del nombre original (nunca deberian llegar via {@code extensionDe}, pero
     * nada las prohibe explicitamente) para que no puedan alterar la estructura de carpetas
     * del ZIP. */
    private static String nombreArchivoZip(Factura factura) {
        String original = factura.getNombreArchivoOriginal();
        String limpio = original != null && !original.isBlank()
                ? original.replace("\\", "_").replace("/", "_")
                : "factura";
        return factura.getId() + "_" + limpio;
    }

    /** Fila de titulo centrada sobre el ancho de la tabla (una celda fusionada, no una por
     * columna, para que el centrado sea sobre toda la tabla y no solo la primera columna). */
    private static void escribirTitulo(Sheet sheet, int filaIndex, String titulo, CellStyle estilo, int numColumnas) {
        Row row = sheet.createRow(filaIndex);
        Cell cell = row.createCell(0);
        cell.setCellValue(escaparFormula(titulo));
        cell.setCellStyle(estilo);
        sheet.addMergedRegion(new CellRangeAddress(filaIndex, filaIndex, 0, numColumnas - 1));
    }

    /** {@code titulo}/{@code cabeceras} son texto libre que llega desde query params: si algun
     * valor empezara por =, +, - o @ (sintaxis de formula/DDE en Excel), Excel podria
     * interpretarlo como formula al abrir el archivo en vez de como texto plano. Se antepone
     * un apostrofe para forzar que se trate siempre como texto. */
    private static String escaparFormula(String valor) {
        if (valor != null && !valor.isEmpty() && "=+-@".indexOf(valor.charAt(0)) >= 0) {
            return "'" + valor;
        }
        return valor;
    }

    /** El orden de los {@code setCell} de abajo define el orden real de las columnas - tiene
     * que coincidir con {@link #CABECERA_DEFECTO} y con {@code cabecerasExportar()} del front
     * (ver comentario de {@link #CABECERA_DEFECTO}). */
    private void escribirFilaFactura(Sheet sheet, int filaIndex, Factura factura) {
        Row row = sheet.createRow(filaIndex);
        int col = 0;
        setCell(row, col++, factura.getProveedor().getNombre());
        setCell(row, col++, factura.getProveedor().getTelefono());
        setCell(row, col++, factura.getProveedor().getTipo().name());
        setCell(row, col++, formatearFecha(factura.getFechaImpresion()));
        setCell(row, col++, formatearFecha(factura.getFechaEntrega()));
        setCell(row, col++, factura.getImporteBase());
        setCell(row, col++, factura.getIva());
        setCell(row, col++, factura.getImporteTotal());
        setCell(row, col++, factura.getMes());
        setCell(row, col++, factura.getAnio());
        setCell(row, col++, factura.getEstadoPago().name());
        setCell(row, col++, factura.getNombreArchivoOriginal());
        setCell(row, col, factura.getFechaSubida() != null ? factura.getFechaSubida().toString() : null);
    }

    private static void escribirFila(Sheet sheet, int filaIndex, String[] valores, CellStyle estilo) {
        Row row = sheet.createRow(filaIndex);
        for (int i = 0; i < valores.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(escaparFormula(valores[i]));
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

    private static CellStyle crearEstiloTitulo(SXSSFWorkbook workbook) {
        CellStyle estilo = crearEstiloCabecera(workbook);
        estilo.setAlignment(HorizontalAlignment.CENTER);
        return estilo;
    }
}
