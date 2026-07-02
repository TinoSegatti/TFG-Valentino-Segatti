package com.reforma.domain.anomalias.dto;

import com.reforma.domain.anomalias.entity.AnomaliaPrecio;
import java.time.Instant;

/** Item del historial de anomalías (consola de reportes / ficha de proveedor — RF-IA-ANOM-006). */
public record AnomaliaHistorialResponse(
        String id,
        Long idMateriaPrima,
        String codigoMateriaPrima,
        String nombreMateriaPrima,
        String idCompra,
        String numeroFactura,
        Double precioIngresado,
        Double precioPromedioHistorico,
        Double zScore,
        Double desviacionPct,
        String clasificacion,
        Boolean usuarioConfirmo,
        Instant detectadoEn) {

    public static AnomaliaHistorialResponse from(AnomaliaPrecio a) {
        var mp = a.getMateriaPrima();
        var compra = a.getCompra();
        Double promedio = a.getPrecioPromedioHistorico();
        Double desviacion = (promedio != null && promedio != 0.0)
                ? Math.round(((a.getPrecioIngresado() - promedio) / promedio) * 100.0 * 100.0) / 100.0
                : null;
        return new AnomaliaHistorialResponse(
                a.getId(),
                mp.getId(),
                mp.getCodigoMateriaPrima(),
                mp.getNombreMateriaPrima(),
                compra != null ? compra.getId() : null,
                compra != null ? compra.getNumeroFactura() : null,
                a.getPrecioIngresado(),
                promedio,
                a.getZScore(),
                desviacion,
                a.getClasificacion().name(),
                a.getUsuarioConfirmo(),
                a.getDetectadoEn());
    }
}
