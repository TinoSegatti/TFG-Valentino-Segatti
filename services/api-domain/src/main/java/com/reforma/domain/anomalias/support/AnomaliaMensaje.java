package com.reforma.domain.anomalias.support;

import com.reforma.domain.anomalias.domain.ClasificacionAnomalia;
import com.reforma.domain.ml.dto.AnomaliaMlResponse;
import java.util.Locale;

/**
 * Construye el mensaje legible para el usuario (RF-IA-ANOM-003), sin jerga estadística (RF-NF-640).
 */
public final class AnomaliaMensaje {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

    private AnomaliaMensaje() {}

    public static String construir(
            ClasificacionAnomalia clasificacion, String nombreMp, double precio, AnomaliaMlResponse ml) {
        return switch (clasificacion) {
            case ANOMALIA_ALTA, ATENCION -> desviacion(nombreMp, precio, ml);
            case NORMAL -> "El precio de " + nombreMp + " está dentro del rango habitual.";
            case SIN_HISTORIAL ->
                    "Aún no hay historial suficiente para comparar el precio de " + nombreMp + ".";
            case SIN_EVALUAR -> "No se pudo evaluar el precio en este momento.";
        };
    }

    private static String desviacion(String nombreMp, double precio, AnomaliaMlResponse ml) {
        double promedio = ml.promedioHistorico() != null ? ml.promedioHistorico() : 0.0;
        double pct = ml.desviacionPct() != null ? ml.desviacionPct() : 0.0;
        String direccion = pct >= 0 ? "superior" : "inferior";
        return "El precio ingresado (" + dinero(precio) + ") es un " + porcentaje(Math.abs(pct))
                + " " + direccion + " al promedio histórico (" + dinero(promedio) + ") para " + nombreMp
                + ". Precio mínimo histórico: " + dinero(valor(ml.minHistorico()))
                + ". Precio máximo histórico: " + dinero(valor(ml.maxHistorico())) + ".";
    }

    private static double valor(Double d) {
        return d != null ? d : 0.0;
    }

    private static String dinero(double v) {
        return String.format(ES_AR, "$%,.2f", v);
    }

    private static String porcentaje(double v) {
        return String.format(ES_AR, "%,.1f%%", v);
    }
}
