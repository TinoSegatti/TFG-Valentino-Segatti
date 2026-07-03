package com.reforma.domain.reporte.dto;

import com.reforma.domain.inventario.dto.InventarioResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * Informe de estado de la granja para un período (RF-REP-001/003): analítica por secciones
 * calculada on-demand (sin persistencia propia; para congelar un estado está el módulo
 * Archivos). El frontend lo renderiza y genera el HTML autocontenido descargable.
 *
 * <p>Solo se agregan compras y fabricaciones REGISTRADAS (los borradores no tienen líneas
 * confirmadas). La sección de inventario reusa {@link InventarioResponse} (estado actual,
 * no depende del período).
 */
public record InformeEstadoResponse(
        String idGranja,
        LocalDate desde,
        LocalDate hasta,
        ResumenGeneral resumen,
        SeccionProveedores proveedores,
        SeccionInventario inventario,
        SeccionCompras compras,
        SeccionConsumos consumos,
        SeccionIa ia) {

    /** KPIs de cabecera del informe. */
    public record ResumenGeneral(
            int compras,
            double gastoTotal,
            double valorStock,
            int fabricaciones,
            double kgProducidos,
            double mermaTotal) {}

    /** Agregado por proveedor (snapshots de la compra, no el catálogo vivo — ADR 0005). */
    public record ProveedorInforme(
            String codigo,
            String nombre,
            int compras,
            double monto,
            double kg,
            String materiaPrincipal) {}

    public record SeccionProveedores(List<ProveedorInforme> proveedores) {}

    public record SeccionInventario(
            List<InventarioResponse> items, double valorTotal, double mermaTotal) {}

    /** Punto de la evolución mensual de compras ({@code mes} = "yyyy-MM"). */
    public record PuntoMensual(String mes, double monto, double kg) {}

    /** Agregado de compras por materia prima (precios min/máx/promedio ponderado del período). */
    public record MateriaCompradaInforme(
            String codigo,
            String nombre,
            double kg,
            double monto,
            double precioMin,
            double precioMax,
            double precioPromedio) {}

    public record SeccionCompras(
            List<PuntoMensual> evolucionMensual, List<MateriaCompradaInforme> materias) {}

    /** Fabricaciones agregadas por fórmula (snapshots de la fabricación). */
    public record FormulaConsumoInforme(
            String codigo,
            String descripcion,
            int fabricaciones,
            double kgProducidos,
            double costoTotal) {}

    /** Consumo de una materia prima en las fabricaciones del período. */
    public record MateriaConsumidaInforme(
            String codigo, String nombre, double kgConsumidos, double costo) {}

    public record SeccionConsumos(
            List<FormulaConsumoInforme> formulas, List<MateriaConsumidaInforme> materias) {}

    /** Alerta de precio del período (RF-REP-003), con la decisión del usuario si la hubo. */
    public record AnomaliaInforme(
            String numeroFactura,
            LocalDate fechaCompra,
            String codigoMateriaPrima,
            String nombreMateriaPrima,
            double precioIngresado,
            Double precioPromedioHistorico,
            Double zScore,
            String clasificacion,
            Boolean usuarioConfirmo) {}

    public record PrediccionInforme(
            String codigoMateriaPrima,
            String nombreMateriaPrima,
            Integer diasRestantes,
            LocalDate fechaAgotamiento,
            String nivelAlerta) {}

    /**
     * Las anomalías están disponibles en todos los planes; las predicciones de stock solo en
     * BUSINESS/ENTERPRISE (RD-03) — {@code prediccionesDisponibles} lo indica para el upsell.
     */
    public record SeccionIa(
            List<AnomaliaInforme> anomalias,
            boolean prediccionesDisponibles,
            List<PrediccionInforme> predicciones) {}
}
