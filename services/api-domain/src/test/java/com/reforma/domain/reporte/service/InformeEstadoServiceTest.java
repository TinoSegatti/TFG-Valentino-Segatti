package com.reforma.domain.reporte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reforma.domain.anomalias.repository.AnomaliaPrecioRepository;
import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.service.AuditoriaService;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.entity.CompraCabecera;
import com.reforma.domain.compras.entity.CompraDetalle;
import com.reforma.domain.compras.repository.CompraCabeceraRepository;
import com.reforma.domain.fabricaciones.domain.EstadoFabricacion;
import com.reforma.domain.fabricaciones.entity.Fabricacion;
import com.reforma.domain.fabricaciones.entity.FabricacionDetalle;
import com.reforma.domain.fabricaciones.repository.FabricacionRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.inventario.dto.InventarioListadoResponse;
import com.reforma.domain.inventario.dto.InventarioResponse;
import com.reforma.domain.inventario.service.InventarioService;
import com.reforma.domain.prediccion.repository.PrediccionStockRepository;
import com.reforma.domain.reporte.dto.InformeEstadoResponse;
import com.reforma.domain.suscripciones.service.PlanService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tests del informe de estado (RF-REP-001/003): filtrado por período y estado, agregación
 * por proveedor/mes/materia prima, consumos de fabricaciones, gating de predicciones (RD-03)
 * y auditoría INFORME_GENERADO.
 */
@ExtendWith(MockitoExtension.class)
class InformeEstadoServiceTest {

    private static final String ID_TENANT = "u_demo";
    private static final String ID_USUARIO = "u_demo";
    private static final String ID_GRANJA = "g_demo";
    private static final LocalDate DESDE = LocalDate.of(2026, 4, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 6, 30);

    @Mock private CompraCabeceraRepository compraCabeceraRepository;
    @Mock private FabricacionRepository fabricacionRepository;
    @Mock private AnomaliaPrecioRepository anomaliaPrecioRepository;
    @Mock private PrediccionStockRepository prediccionStockRepository;
    @Mock private InventarioService inventarioService;
    @Mock private GranjaAccesoService granjaAccesoService;
    @Mock private PlanService planService;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks private InformeEstadoService informeEstadoService;

    /** Plan STARTER: sin predicciones de stock (RD-03). Solo para tests que arman el informe. */
    private void planSinPredicciones() {
        when(planService.obtenerPlanEfectivo(ID_TENANT)).thenReturn(PlanSuscripcion.STARTER);
        when(planService.permitePrediccionStock(PlanSuscripcion.STARTER)).thenReturn(false);
    }

    private void sinDatos() {
        planSinPredicciones();
        when(compraCabeceraRepository.findByGranjaIdAndActivoTrueOrderByFechaCompraDesc(ID_GRANJA))
                .thenReturn(List.of());
        when(fabricacionRepository.findByGranjaIdAndActivoTrueOrderByFechaFabricacionDesc(ID_GRANJA))
                .thenReturn(List.of());
        when(anomaliaPrecioRepository.listarPorGranjaYPeriodo(any(), any(), any()))
                .thenReturn(List.of());
        when(inventarioService.listar(ID_TENANT, ID_GRANJA))
                .thenReturn(new InventarioListadoResponse(false, List.of()));
    }

    private InformeEstadoResponse generar() {
        return informeEstadoService.generar(ID_TENANT, ID_USUARIO, ID_GRANJA, DESDE, HASTA);
    }

    @Test
    @DisplayName("generar: agrega compras/fabricaciones del período y arma todas las secciones")
    void generar_happyPath() {
        planSinPredicciones();
        CompraCabecera compraMayo = compra("2026-05-10", 1500.0, "PRV1", "Proveedor Uno",
                linea("MAIZ", "Maíz", 100.0, 10.0, 1000.0),
                linea("SOJA", "Soja", 25.0, 20.0, 500.0));
        CompraCabecera compraJunio = compra("2026-06-05", 600.0, "PRV1", "Proveedor Uno",
                linea("MAIZ", "Maíz", 50.0, 12.0, 600.0));
        CompraCabecera fueraDePeriodo = compra("2026-01-15", 999.0, "PRV2", "Otro",
                linea("MAIZ", "Maíz", 10.0, 9.0, 90.0));
        CompraCabecera borrador = compra("2026-05-20", 400.0, "PRV1", "Proveedor Uno");
        borrador.setEstado(EstadoCompra.BORRADOR);
        when(compraCabeceraRepository.findByGranjaIdAndActivoTrueOrderByFechaCompraDesc(ID_GRANJA))
                .thenReturn(List.of(compraJunio, compraMayo, borrador, fueraDePeriodo));

        Fabricacion fabricacion = fabricacion("2026-05-15", "F1", "Engorde", 1000.0, 5200.0,
                consumo("MAIZ", "Maíz", 700.0, 4200.0));
        Fabricacion fabricacionVieja = fabricacion("2025-12-01", "F1", "Engorde", 500.0, 2000.0);
        when(fabricacionRepository.findByGranjaIdAndActivoTrueOrderByFechaFabricacionDesc(ID_GRANJA))
                .thenReturn(List.of(fabricacion, fabricacionVieja));

        when(anomaliaPrecioRepository.listarPorGranjaYPeriodo(any(), any(), any()))
                .thenReturn(List.of());
        when(inventarioService.listar(ID_TENANT, ID_GRANJA))
                .thenReturn(new InventarioListadoResponse(true, List.of(
                        itemInventario("MAIZ", 3000.0, 12.5),
                        itemInventario("SOJA", 800.0, -2.0))));

        InformeEstadoResponse informe = generar();

        // Resumen: 2 compras del período (borrador y enero excluidos), 1 fabricación.
        assertThat(informe.resumen().compras()).isEqualTo(2);
        assertThat(informe.resumen().gastoTotal()).isEqualTo(2100.0);
        assertThat(informe.resumen().fabricaciones()).isEqualTo(1);
        assertThat(informe.resumen().kgProducidos()).isEqualTo(1000.0);
        assertThat(informe.resumen().valorStock()).isEqualTo(3800.0);
        assertThat(informe.resumen().mermaTotal()).isEqualTo(10.5);

        // Proveedores: un solo proveedor con 2 compras; su MP principal por gasto es el maíz.
        assertThat(informe.proveedores().proveedores()).hasSize(1);
        var proveedor = informe.proveedores().proveedores().getFirst();
        assertThat(proveedor.codigo()).isEqualTo("PRV1");
        assertThat(proveedor.compras()).isEqualTo(2);
        assertThat(proveedor.monto()).isEqualTo(2100.0);
        assertThat(proveedor.kg()).isEqualTo(175.0);
        assertThat(proveedor.materiaPrincipal()).isEqualTo("Maíz");

        // Compras: evolución mensual ordenada y agregado por MP con min/max/promedio ponderado.
        assertThat(informe.compras().evolucionMensual())
                .extracting(InformeEstadoResponse.PuntoMensual::mes)
                .containsExactly("2026-05", "2026-06");
        var maiz = informe.compras().materias().getFirst();
        assertThat(maiz.codigo()).isEqualTo("MAIZ");
        assertThat(maiz.kg()).isEqualTo(150.0);
        assertThat(maiz.monto()).isEqualTo(1600.0);
        assertThat(maiz.precioMin()).isEqualTo(10.0);
        assertThat(maiz.precioMax()).isEqualTo(12.0);
        assertThat(maiz.precioPromedio()).isEqualTo(10.67);

        // Consumos: fórmula del período con su consumo por MP.
        assertThat(informe.consumos().formulas()).hasSize(1);
        assertThat(informe.consumos().formulas().getFirst().kgProducidos()).isEqualTo(1000.0);
        assertThat(informe.consumos().materias().getFirst().kgConsumidos()).isEqualTo(700.0);

        // IA: plan STARTER sin predicciones (RD-03).
        assertThat(informe.ia().prediccionesDisponibles()).isFalse();
        assertThat(informe.ia().predicciones()).isEmpty();
        verify(prediccionStockRepository, never()).findByGranjaId(any());

        var eventoCaptor = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.INFORME_GENERADO);
        verify(granjaAccesoService).validarAcceso(ID_TENANT, ID_GRANJA);
    }

    @Test
    @DisplayName("generar: período sin movimientos devuelve secciones vacías sin error")
    void generar_periodoVacio() {
        sinDatos();

        InformeEstadoResponse informe = generar();

        assertThat(informe.resumen().compras()).isZero();
        assertThat(informe.proveedores().proveedores()).isEmpty();
        assertThat(informe.compras().evolucionMensual()).isEmpty();
        assertThat(informe.consumos().formulas()).isEmpty();
        assertThat(informe.ia().anomalias()).isEmpty();
    }

    @Test
    @DisplayName("generar: sin período usa los últimos 90 días por defecto")
    void generar_periodoPorDefecto() {
        sinDatos();

        InformeEstadoResponse informe =
                informeEstadoService.generar(ID_TENANT, ID_USUARIO, ID_GRANJA, null, null);

        assertThat(informe.hasta()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
        assertThat(informe.desde())
                .isEqualTo(informe.hasta().minusDays(InformeEstadoService.PERIODO_DEFAULT_DIAS));
    }

    @Test
    @DisplayName("generar: 400 si el inicio es posterior al fin")
    void generar_periodoInvertido() {
        assertThatThrownBy(() ->
                        informeEstadoService.generar(ID_TENANT, ID_USUARIO, ID_GRANJA, HASTA, DESDE))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("generar: 400 si el período supera los 24 meses")
    void generar_periodoDemasiadoLargo() {
        assertThatThrownBy(() -> informeEstadoService.generar(
                        ID_TENANT, ID_USUARIO, ID_GRANJA, HASTA.minusYears(3), HASTA))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------- fixtures ----------

    private static CompraCabecera compra(
            String fecha, double total, String codigoProv, String nombreProv, CompraDetalle... lineas) {
        CompraCabecera cabecera = CompraCabecera.builder()
                .id("c_" + fecha + "_" + codigoProv)
                .numeroFactura("F-" + fecha)
                .fechaCompra(instante(fecha))
                .totalFactura(total)
                .codigoProveedorSnapshot(codigoProv)
                .nombreProveedorSnapshot(nombreProv)
                .estado(EstadoCompra.REGISTRADA)
                .activo(true)
                .build();
        cabecera.getDetalles().addAll(List.of(lineas));
        return cabecera;
    }

    private static CompraDetalle linea(
            String codigo, String nombre, double kg, double precio, double subtotal) {
        return CompraDetalle.builder()
                .cantidadComprada(kg)
                .precioUnitario(precio)
                .subtotal(subtotal)
                .codigoMpSnapshot(codigo)
                .nombreMpSnapshot(nombre)
                .build();
    }

    private static Fabricacion fabricacion(
            String fecha, String codigoFormula, String descripcion, double kg, double costo,
            FabricacionDetalle... consumos) {
        Fabricacion fabricacion = Fabricacion.builder()
                .id("f_" + fecha)
                .fechaFabricacion(instante(fecha))
                .cantidadFabricacion(kg)
                .costoTotalFabricacion(costo)
                .codigoFormulaSnapshot(codigoFormula)
                .descripcionFormulaSnapshot(descripcion)
                .estado(EstadoFabricacion.REGISTRADA)
                .activo(true)
                .build();
        fabricacion.getDetalles().addAll(List.of(consumos));
        return fabricacion;
    }

    private static FabricacionDetalle consumo(
            String codigo, String nombre, double kg, double costo) {
        return FabricacionDetalle.builder()
                .cantidadUsada(kg)
                .costoParcial(costo)
                .codigoMpSnapshot(codigo)
                .nombreMpSnapshot(nombre)
                .build();
    }

    private static InventarioResponse itemInventario(String codigo, double valorStock, double merma) {
        return new InventarioResponse(
                "inv_" + codigo, 1L, codigo, codigo, 10.0, 0, 0, 0, merma, 0, valorStock, 0, null);
    }

    private static Instant instante(String fecha) {
        return LocalDate.parse(fecha).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
