package com.gestionrestaurante.back.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parseo heuristico best-effort del texto OCR crudo de una factura (back/fase2-3-back.md),
 * sin ninguna dependencia de Spring ni de BBDD - testeable de forma totalmente aislada contra
 * cadenas de texto fijas. El *fuzzy matching* de proveedor (que si necesita BBDD) vive en
 * TesseractFacturaExtractionService, no aqui.
 *
 * <p>Ninguna heuristica de esta clase pretende ser exacta: todo lo que devuelve se ofrece
 * siempre como valor editable en una previsualizacion que el Admin confirma o corrige antes de
 * guardar (nunca se persiste ciegamente, ver plan-implementacion-general.md).
 */
public final class FacturaTextParser {

    private FacturaTextParser() {
    }

    private static final Pattern PATRON_FECHA_NUMERICA =
            Pattern.compile("\\b(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4})\\b");

    private static final Pattern PATRON_FECHA_TEXTUAL =
            Pattern.compile("\\b(\\d{1,2})\\s+de\\s+([a-zA-Záéíóú]+)\\s+de\\s+(\\d{4})\\b",
                    Pattern.CASE_INSENSITIVE);

    private static final Map<String, Integer> MESES_ES = Map.ofEntries(
            Map.entry("enero", 1), Map.entry("febrero", 2), Map.entry("marzo", 3), Map.entry("abril", 4),
            Map.entry("mayo", 5), Map.entry("junio", 6), Map.entry("julio", 7), Map.entry("agosto", 8),
            Map.entry("septiembre", 9), Map.entry("setiembre", 9), Map.entry("octubre", 10),
            Map.entry("noviembre", 11), Map.entry("diciembre", 12));

    private static final Pattern PATRON_IMPORTE =
            Pattern.compile("\\b(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+\\.\\d{2})\\s?€?\\b");

    private static final Pattern PATRON_ETIQUETA_VALOR =
            Pattern.compile("^\\s*([\\p{L} .]{2,40}?)\\s*[:]\\s*(.+?)\\s*$");

    /** Ratio importe base -> total esperado para cada tipo de IVA habitual en Espana. */
    private static final double[] RATIOS_IVA = {0.04, 0.10, 0.21};
    private static final double TOLERANCIA_RATIO = 0.01;
    private static final BigDecimal TOLERANCIA_SUMA_CENTIMOS = new BigDecimal("0.02");

    public record ImportesDetectados(BigDecimal base, BigDecimal iva, BigDecimal total) {
    }

    /**
     * Primera fecha plausible (no mas de ~1 anio en el futuro o el pasado respecto a hoy) de
     * entre todas las encontradas en {@code texto}, probando primero el formato numerico y
     * luego el textual en espanol.
     */
    public static Optional<LocalDate> extraerFecha(String texto) {
        LocalDate hoy = LocalDate.now();
        LocalDate limiteInferior = hoy.minusYears(1);
        LocalDate limiteSuperior = hoy.plusYears(1);

        Matcher matcherNumerica = PATRON_FECHA_NUMERICA.matcher(texto);
        while (matcherNumerica.find()) {
            Optional<LocalDate> fecha = fechaValidaOVacia(
                    parseSeguro(matcherNumerica.group(3), matcherNumerica.group(2), matcherNumerica.group(1)),
                    limiteInferior, limiteSuperior);
            if (fecha.isPresent()) {
                return fecha;
            }
        }

        Matcher matcherTextual = PATRON_FECHA_TEXTUAL.matcher(texto);
        while (matcherTextual.find()) {
            Integer mes = MESES_ES.get(matcherTextual.group(2).toLowerCase());
            if (mes == null) {
                continue;
            }
            Optional<LocalDate> fecha = fechaValidaOVacia(
                    parseSeguro(matcherTextual.group(3), String.valueOf(mes), matcherTextual.group(1)),
                    limiteInferior, limiteSuperior);
            if (fecha.isPresent()) {
                return fecha;
            }
        }
        return Optional.empty();
    }

    /**
     * El importe mayor encontrado se asume total (en una factura, el total suele ser el
     * numero mas alto). Con exactamente 3 importes distintos (caso habitual de una factura
     * bien desglosada: base + IVA + total), si los dos menores suman aprox. el total, se
     * proponen como base/IVA. Con exactamente 2 importes distintos, si uno es aprox. un
     * 4/10/21% mayor que el otro, tambien se proponen como base+IVA. En cualquier otro caso
     * base/iva quedan sin proponer (el Admin los rellena a mano si los necesita).
     */
    public static ImportesDetectados extraerImportes(String texto) {
        List<BigDecimal> importes = new java.util.ArrayList<>();
        Matcher matcher = PATRON_IMPORTE.matcher(texto);
        while (matcher.find()) {
            parseImporte(matcher.group(1)).ifPresent(importes::add);
        }
        if (importes.isEmpty()) {
            return new ImportesDetectados(null, null, null);
        }

        List<BigDecimal> distintos = importes.stream().distinct()
                .sorted(java.util.Comparator.reverseOrder())
                .toList();
        BigDecimal total = distintos.get(0);

        if (distintos.size() == 3) {
            BigDecimal base = distintos.get(1);
            BigDecimal iva = distintos.get(2);
            if (base.add(iva).subtract(total).abs().compareTo(TOLERANCIA_SUMA_CENTIMOS) <= 0) {
                return new ImportesDetectados(base, iva, total);
            }
        } else if (distintos.size() == 2) {
            BigDecimal otro = distintos.get(1);
            if (otro.signum() > 0) {
                double ratio = total.divide(otro, 6, RoundingMode.HALF_UP).doubleValue() - 1.0;
                for (double ratioIva : RATIOS_IVA) {
                    if (Math.abs(ratio - ratioIva) <= TOLERANCIA_RATIO) {
                        return new ImportesDetectados(otro, total.subtract(otro), total);
                    }
                }
            }
        }
        return new ImportesDetectados(null, null, total);
    }

    /** Primeras lineas no vacias del texto (donde suele estar el nombre de la empresa). */
    public static List<String> lineasCandidatasProveedor(String texto, int maxLineas) {
        return texto.lines()
                .map(String::trim)
                .filter(linea -> !linea.isBlank())
                .limit(maxLineas)
                .toList();
    }

    /**
     * Lineas con forma "Etiqueta: Valor" que no esten en {@code etiquetasYaConsumidas}
     * (ya interpretadas como fecha/importe/proveedor) se ofrecen como campos adicionales -
     * es la base de "los campos nuevos se crean automaticamente" (back/fase2-3-back.md).
     */
    public static Map<String, String> extraerCamposAdicionales(String texto, java.util.Set<String> etiquetasYaConsumidas) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (String linea : texto.lines().toList()) {
            Matcher matcher = PATRON_ETIQUETA_VALOR.matcher(linea);
            if (!matcher.matches()) {
                continue;
            }
            String etiqueta = matcher.group(1).trim();
            String valor = matcher.group(2).trim();
            if (etiqueta.isEmpty() || valor.isEmpty()) {
                continue;
            }
            boolean yaConsumida = etiquetasYaConsumidas.stream()
                    .anyMatch(consumida -> etiqueta.equalsIgnoreCase(consumida));
            if (!yaConsumida) {
                campos.putIfAbsent(etiqueta, valor);
            }
        }
        return campos;
    }

    private static Optional<LocalDate> fechaValidaOVacia(LocalDate fecha, LocalDate limiteInferior, LocalDate limiteSuperior) {
        if (fecha == null) {
            return Optional.empty();
        }
        return (!fecha.isBefore(limiteInferior) && !fecha.isAfter(limiteSuperior)) ? Optional.of(fecha) : Optional.empty();
    }

    private static LocalDate parseSeguro(String anio, String mes, String dia) {
        try {
            return LocalDate.of(Integer.parseInt(anio), Integer.parseInt(mes), Integer.parseInt(dia));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Optional<BigDecimal> parseImporte(String token) {
        try {
            String normalizado = token.contains(",")
                    ? token.replace(".", "").replace(",", ".")
                    : token;
            return Optional.of(new BigDecimal(normalizado));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
