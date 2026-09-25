package com.gestionrestaurante.back.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
 * <p>Solo propone los campos fijos de Factura (fecha, importe base/IVA/total): no existe
 * heuristica de "campos adicionales" - cualquier dato de la factura que no sea uno de estos
 * esta siempre disponible en el PDF/imagen original, que se conserva y se puede descargar.
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

    private static final Pattern PALABRA_FECHA_IMPRESION = Pattern.compile("(?i)impres");
    private static final Pattern PALABRA_FECHA_ENTREGA = Pattern.compile("(?i)entrega");

    private static final Map<String, Integer> MESES_ES = Map.ofEntries(
            Map.entry("enero", 1), Map.entry("febrero", 2), Map.entry("marzo", 3), Map.entry("abril", 4),
            Map.entry("mayo", 5), Map.entry("junio", 6), Map.entry("julio", 7), Map.entry("agosto", 8),
            Map.entry("septiembre", 9), Map.entry("setiembre", 9), Map.entry("octubre", 10),
            Map.entry("noviembre", 11), Map.entry("diciembre", 12));

    private static final Pattern PATRON_IMPORTE =
            Pattern.compile("\\b(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+\\.\\d{2})\\s?€?\\b");

    /** Frases especificas del importe total a pagar de la factura. Una palabra "total" suelta
     * NO es suficiente en general: aparece con frecuencia en lineas que no son el total de la
     * factura ("Peso Total", "Valor total descuentos", la cabecera "Total IMP" de la tabla de
     * IVA...), por lo que buscarla de forma generica produce falsos positivos. */
    private static final Pattern FRASE_TOTAL_FACTURA = Pattern.compile(
            "(?i)total\\s*a\\s*pagar|total\\s*a\\s*abonar|importe\\s*total|total\\s*factura|total\\s*general");

    /** "Total" SI es suficiente cuando es la primera palabra de la linea y va seguido de
     * inmediato (solo espacios/dos puntos de por medio) de un importe - ahí no hay
     * ambiguedad posible ("Total: 584,21", "Total 117,61"), a diferencia de "Peso Total: ..."
     * (el calificador va ANTES de "total") o "Total bases: ..."/"Valor total descuentos ..."
     * (el calificador va DESPUES, entre "total" y el importe) - back/fase2-3-back.md, facturas
     * de Hostelor/Coca-Cola donde ninguna de las frases especificas de arriba aparece nunca. */
    private static final Pattern FRASE_TOTAL_SOLO = Pattern.compile("(?i)^\\s*total\\s*[:\\-]?\\s*(?=\\d)");

    /** Lineas que etiquetan explicitamente el IVA/base imponible ("IVA (21%): 21,00 €",
     * "Base imponible: 100,00 €"), formato habitual de facturas de servicios (luz, telefono...).
     * Tolera "I.V.A." (con puntos entre letras, formato usado por algunos proveedores). */
    private static final Pattern PALABRA_IVA = Pattern.compile("(?i)\\bi\\.?v\\.?a\\.?\\b");
    private static final Pattern PALABRA_BASE_IMPONIBLE = Pattern.compile("(?i)base\\s+imponible");

    /** Cabecera de la tabla-resumen de IVA de tickets de venta al por mayor (p. ej. Makro):
     * "Mercancia % IMP Total IMP" seguido de una fila por tipo de IVA aplicado y una fila final
     * con los totales agregados (base total, IVA total) sin ningun "%" en ella. */
    private static final Pattern CABECERA_DESGLOSE_IVA = Pattern.compile("(?i)mercan.*imp");
    private static final int LINEAS_MAX_TRAS_CABECERA_DESGLOSE = 15;

    /** Ratio importe base -> total esperado para cada tipo de IVA habitual en Espana, usado
     * solo como ultimo recurso cuando no hay ninguna etiqueta ni tabla-resumen reconocible. */
    private static final double[] RATIOS_IVA = {0.04, 0.10, 0.21};
    private static final double TOLERANCIA_RATIO = 0.01;
    private static final BigDecimal TOLERANCIA_SUMA_CENTIMOS = new BigDecimal("0.02");

    public record ImportesDetectados(BigDecimal base, BigDecimal iva, BigDecimal total) {
    }

    /** Fecha de impresion del documento (p. ej. "Fecha impresión: 28/07/2026"). No cae a
     * ninguna fecha generica si no hay una linea que la identifique explicitamente como tal. */
    public static Optional<LocalDate> extraerFechaImpresion(String texto) {
        return primeraFechaEnLineaConPalabra(texto, PALABRA_FECHA_IMPRESION);
    }

    /** Fecha de entrega del documento (p. ej. "Fecha de entrega: 28/07/2026"), mismo criterio
     * que {@link #extraerFechaImpresion}. */
    public static Optional<LocalDate> extraerFechaEntrega(String texto) {
        return primeraFechaEnLineaConPalabra(texto, PALABRA_FECHA_ENTREGA);
    }

    private static Optional<LocalDate> primeraFechaEnLineaConPalabra(String texto, Pattern palabra) {
        LocalDate hoy = LocalDate.now();
        LocalDate limiteInferior = hoy.minusYears(1);
        LocalDate limiteSuperior = hoy.plusYears(1);

        for (String linea : texto.lines().toList()) {
            if (!palabra.matcher(linea).find()) {
                continue;
            }
            Optional<LocalDate> fecha = primeraFechaPlausibleEnTexto(linea, limiteInferior, limiteSuperior);
            if (fecha.isPresent()) {
                return fecha;
            }
        }
        return Optional.empty();
    }

    private static Optional<LocalDate> primeraFechaPlausibleEnTexto(
            String texto, LocalDate limiteInferior, LocalDate limiteSuperior) {
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
     * Importe total, base imponible e IVA de la factura, probados en este orden:
     * <ol>
     *   <li>Etiquetas explicitas ("Total a pagar", "IVA", "Base imponible") en el propio texto
     *   - formato habitual de facturas de servicios con un unico tipo de IVA.</li>
     *   <li>Tabla-resumen de desglose de IVA por tipo ("Mercancia % IMP Total IMP...") habitual
     *   en tickets de venta al por mayor con varios tipos de IVA en la misma factura.</li>
     *   <li>Ultimo recurso: el importe mayor encontrado se asume total (en una factura, el
     *   total suele ser el numero mas alto); con exactamente 3 importes distintos, si los dos
     *   menores suman aprox. el total, se proponen como base/IVA; con exactamente 2, si uno es
     *   aprox. un 4/10/21% mayor que el otro, tambien se proponen como base+IVA.</li>
     * </ol>
     * En cualquier caso sin suficiente informacion, base/iva quedan sin proponer (el Admin los
     * rellena a mano si los necesita).
     */
    public static ImportesDetectados extraerImportes(String texto) {
        List<String> lineas = texto.lines().toList();

        BigDecimal total = buscarTotalPorEtiqueta(lineas).orElse(null);
        BigDecimal ivaEtiquetado = sumarImportesEnLineasConPalabra(lineas, PALABRA_IVA);
        BigDecimal baseEtiquetada = primerImporteEnLineaConPalabra(lineas, PALABRA_BASE_IMPONIBLE).orElse(null);

        List<BigDecimal> distintosDesc = importesDistintosDescendente(texto);
        if (total == null && !distintosDesc.isEmpty()) {
            total = distintosDesc.get(0);
        }

        if (total != null && ivaEtiquetado != null && ivaEtiquetado.signum() > 0) {
            BigDecimal base = baseEtiquetada != null ? baseEtiquetada : total.subtract(ivaEtiquetado);
            return new ImportesDetectados(base, ivaEtiquetado, total);
        }

        if (total != null) {
            Optional<BigDecimal[]> desglose = buscarDesgloseTablaIva(lineas, total);
            if (desglose.isPresent()) {
                return new ImportesDetectados(desglose.get()[0], desglose.get()[1], total);
            }
        }

        return extraerPorRatioOSumaSimple(distintosDesc, total);
    }

    private static Optional<BigDecimal> buscarTotalPorEtiqueta(List<String> lineas) {
        for (String linea : lineas) {
            if (!FRASE_TOTAL_FACTURA.matcher(linea).find() && !FRASE_TOTAL_SOLO.matcher(linea).find()) {
                continue;
            }
            List<BigDecimal> importes = importesEnLinea(linea);
            if (!importes.isEmpty()) {
                return Optional.of(importes.get(importes.size() - 1));
            }
        }
        return Optional.empty();
    }

    /** Solo suma lineas con un unico importe: una linea que etiqueta el IVA pero que en
     * realidad es una fila de tabla con varios importes (base, iva e importe a pagar todos en
     * la misma linea de texto, p.ej. "IVA 21% 97,20 20,41 117,61 Pendiente") es ambigua sobre
     * cual de ellos es el IVA - sumarlos todos da un resultado disparatado, mejor no proponer
     * nada ahi (back/fase2-3-back.md, factura de Hostelor). */
    private static BigDecimal sumarImportesEnLineasConPalabra(List<String> lineas, Pattern palabra) {
        BigDecimal suma = null;
        for (String linea : lineas) {
            if (!palabra.matcher(linea).find()) {
                continue;
            }
            List<BigDecimal> importes = importesEnLinea(linea);
            if (importes.size() == 1) {
                suma = suma == null ? importes.get(0) : suma.add(importes.get(0));
            }
        }
        return suma;
    }

    private static Optional<BigDecimal> primerImporteEnLineaConPalabra(List<String> lineas, Pattern palabra) {
        for (String linea : lineas) {
            if (!palabra.matcher(linea).find()) {
                continue;
            }
            List<BigDecimal> importes = importesEnLinea(linea);
            if (!importes.isEmpty()) {
                return Optional.of(importes.get(0));
            }
        }
        return Optional.empty();
    }

    /**
     * Busca, tras la cabecera de la tabla-resumen de IVA, la fila de totales agregados: la
     * primera linea con exactamente dos importes y sin ningun "%" (las filas de cada tipo de
     * IVA si llevan "%"). Se valida que ambos importes sumen el total ya identificado, para
     * descartar coincidencias con otras lineas de dos importes que no sean esa fila.
     */
    private static Optional<BigDecimal[]> buscarDesgloseTablaIva(List<String> lineas, BigDecimal total) {
        int indiceCabecera = -1;
        for (int i = 0; i < lineas.size(); i++) {
            if (CABECERA_DESGLOSE_IVA.matcher(lineas.get(i)).find()) {
                indiceCabecera = i;
                break;
            }
        }
        if (indiceCabecera == -1) {
            return Optional.empty();
        }

        int limite = Math.min(lineas.size(), indiceCabecera + 1 + LINEAS_MAX_TRAS_CABECERA_DESGLOSE);
        for (int i = indiceCabecera + 1; i < limite; i++) {
            String linea = lineas.get(i);
            if (linea.contains("%")) {
                continue;
            }
            List<BigDecimal> importes = importesEnLinea(linea);
            if (importes.size() != 2) {
                continue;
            }
            BigDecimal base = importes.get(0);
            BigDecimal iva = importes.get(1);
            if (base.add(iva).subtract(total).abs().compareTo(TOLERANCIA_SUMA_CENTIMOS) <= 0) {
                return Optional.of(new BigDecimal[] {base, iva});
            }
        }
        return Optional.empty();
    }

    private static ImportesDetectados extraerPorRatioOSumaSimple(List<BigDecimal> distintosDesc, BigDecimal totalYaConocido) {
        if (distintosDesc.isEmpty()) {
            return new ImportesDetectados(null, null, totalYaConocido);
        }
        BigDecimal total = totalYaConocido != null ? totalYaConocido : distintosDesc.get(0);

        if (distintosDesc.size() == 3) {
            BigDecimal base = distintosDesc.get(1);
            BigDecimal iva = distintosDesc.get(2);
            if (base.add(iva).subtract(total).abs().compareTo(TOLERANCIA_SUMA_CENTIMOS) <= 0) {
                return new ImportesDetectados(base, iva, total);
            }
        } else if (distintosDesc.size() == 2) {
            BigDecimal otro = distintosDesc.get(1);
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

    private static List<BigDecimal> importesDistintosDescendente(String texto) {
        List<BigDecimal> importes = new ArrayList<>(importesEnLinea(texto));
        return importes.stream().distinct().sorted(Comparator.reverseOrder()).toList();
    }

    private static List<BigDecimal> importesEnLinea(String linea) {
        List<BigDecimal> importes = new ArrayList<>();
        Matcher matcher = PATRON_IMPORTE.matcher(linea);
        while (matcher.find()) {
            parseImporte(matcher.group(1)).ifPresent(importes::add);
        }
        return importes;
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
