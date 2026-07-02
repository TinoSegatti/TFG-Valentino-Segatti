package com.reforma.domain.prediccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.repository.CompraDetalleRepository;
import com.reforma.domain.fabricaciones.repository.FabricacionDetalleRepository;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.inventario.entity.Inventario;
import com.reforma.domain.inventario.repository.InventarioRepository;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.ml.MlClient;
import com.reforma.domain.ml.dto.PrediccionItemMl;
import com.reforma.domain.ml.dto.PrediccionStockMlRequest;
import com.reforma.domain.ml.dto.PrediccionStockMlResponse;
import com.reforma.domain.ml.dto.PuntoMensualMl;
import com.reforma.domain.ml.dto.PuntoSerieMl;
import com.reforma.domain.prediccion.dto.PrediccionStockDetalleResponse;
import com.reforma.domain.prediccion.dto.PrediccionStockResponse;
import com.reforma.domain.prediccion.entity.PrediccionStock;
import com.reforma.domain.prediccion.repository.PrediccionStockRepository;
import com.reforma.domain.prediccion.support.AgregadoMensualMateria;
import com.reforma.domain.suscripciones.service.PlanService;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PrediccionStockServiceTest {

    private static final String TENANT = "u";
    private static final String GRANJA = "g";

    @Mock private MlClient mlClient;
    @Mock private CompraDetalleRepository compraDetalleRepository;
    @Mock private FabricacionDetalleRepository fabricacionDetalleRepository;
    @Mock private InventarioRepository inventarioRepository;
    @Mock private MateriaPrimaRepository materiaPrimaRepository;
    @Mock private GranjaRepository granjaRepository;
    @Mock private PrediccionStockRepository prediccionStockRepository;
    @Mock private GranjaAccesoService granjaAccesoService;
    @Mock private PlanService planService;

    @InjectMocks private PrediccionStockService service;

    private final MateriaPrima maiz = MateriaPrima.builder()
            .id(10L)
            .codigoMateriaPrima("MAIZ")
            .nombreMateriaPrima("Maíz")
            .build();

    private void planPermite() {
        when(planService.obtenerPlanEfectivo(TENANT)).thenReturn(PlanSuscripcion.BUSINESS);
        when(planService.permitePrediccionStock(PlanSuscripcion.BUSINESS)).thenReturn(true);
    }

    private static PrediccionItemMl mlItem() {
        return new PrediccionItemMl(
                10L, "ALERTA", "DECRECIENTE", 500.0, 21, 21, -50.0, 200.0, 150.0, 3,
                "PROMEDIO_NETO_MENSUAL_v1",
                List.of(new PuntoSerieMl("2026-05", 600), new PuntoSerieMl("2026-06", 500)),
                List.of(new PuntoSerieMl("2026-07", 250), new PuntoSerieMl("2026-08", 0)));
    }

    @Test
    void gateaPorPlan_lanza403() {
        when(planService.obtenerPlanEfectivo(TENANT)).thenReturn(PlanSuscripcion.STARTER);
        when(planService.permitePrediccionStock(PlanSuscripcion.STARTER)).thenReturn(false);

        assertThatThrownBy(() -> service.predecirGranja(TENANT, GRANJA))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("BUSINESS");
        verify(mlClient, never()).predecirStock(any());
    }

    @Test
    void predecirGranja_armaSerieContiguaConHuecos_yPersiste() {
        planPermite();
        when(materiaPrimaRepository.findByGranjaIdAndActivaTrueOrderByNombreMateriaPrimaAsc(GRANJA))
                .thenReturn(List.of(maiz));

        YearMonth actual = YearMonth.now(ZoneOffset.UTC);
        YearMonth m0 = actual.minusMonths(2);
        YearMonth m1 = actual.minusMonths(1);
        // Ingresos solo en m0 y en el mes actual (hueco en m1).
        when(compraDetalleRepository.ingresosMensualesPorMateria(
                        eq(GRANJA), eq(EstadoCompra.REGISTRADA), any()))
                .thenReturn(List.of(
                        new AgregadoMensualMateria(10L, m0.toString(), 300.0),
                        new AgregadoMensualMateria(10L, actual.toString(), 100.0)));
        when(fabricacionDetalleRepository.consumosMensualesPorMateria(eq(GRANJA), any()))
                .thenReturn(List.of(
                        new AgregadoMensualMateria(10L, m0.toString(), 100.0),
                        new AgregadoMensualMateria(10L, m1.toString(), 200.0),
                        new AgregadoMensualMateria(10L, actual.toString(), 200.0)));

        Inventario inv = org.mockito.Mockito.mock(Inventario.class);
        when(inv.getMateriaPrima()).thenReturn(maiz);
        when(inv.getCantidadReal()).thenReturn(500.0);
        when(inventarioRepository.findByGranjaIdOrderByMateriaPrimaCodigoMateriaPrimaAsc(GRANJA))
                .thenReturn(List.of(inv));

        ArgumentCaptor<PrediccionStockMlRequest> captor =
                ArgumentCaptor.forClass(PrediccionStockMlRequest.class);
        when(mlClient.predecirStock(captor.capture()))
                .thenReturn(Optional.of(new PrediccionStockMlResponse(List.of(mlItem()))));
        when(prediccionStockRepository.findByGranjaIdAndMateriaPrimaId(GRANJA, 10L))
                .thenReturn(Optional.empty());

        List<PrediccionStockResponse> salida = service.predecirGranja(TENANT, GRANJA);

        // Serie contigua m0, m1 (hueco relleno a 0 ingresos / 200 consumo), actual.
        List<PuntoMensualMl> serie = captor.getValue().items().get(0).serieMensual();
        assertThat(serie).hasSize(3);
        assertThat(serie.get(0).mes()).isEqualTo(m0.toString());
        assertThat(serie.get(0).ingresos()).isEqualTo(300.0);
        assertThat(serie.get(1).mes()).isEqualTo(m1.toString());
        assertThat(serie.get(1).ingresos()).isEqualTo(0.0); // hueco relleno
        assertThat(serie.get(1).consumo()).isEqualTo(200.0);
        assertThat(serie.get(2).ingresos()).isEqualTo(100.0);
        // Stock actual tomado del inventario (cantidad real).
        assertThat(captor.getValue().items().get(0).stockActual()).isEqualTo(500.0);
        assertThat(captor.getValue().incluirSeries()).isFalse();

        assertThat(salida).hasSize(1);
        assertThat(salida.get(0).nivelAlerta()).isEqualTo("ALERTA");
        assertThat(salida.get(0).diasRestantes()).isEqualTo(21);
        assertThat(salida.get(0).fechaAgotamiento()).isNotNull();
        verify(prediccionStockRepository, times(1)).save(any(PrediccionStock.class));
    }

    @Test
    void predecirMateriaPrima_devuelveSeries_yUpsert() {
        planPermite();
        when(materiaPrimaRepository.findByIdAndGranjaId(10L, GRANJA)).thenReturn(Optional.of(maiz));
        when(compraDetalleRepository.ingresosMensualesPorMateria(
                        eq(GRANJA), eq(EstadoCompra.REGISTRADA), any()))
                .thenReturn(List.of());
        when(fabricacionDetalleRepository.consumosMensualesPorMateria(eq(GRANJA), any()))
                .thenReturn(List.of());
        when(inventarioRepository.findByGranjaIdOrderByMateriaPrimaCodigoMateriaPrimaAsc(GRANJA))
                .thenReturn(List.of());
        when(mlClient.predecirStock(any()))
                .thenReturn(Optional.of(new PrediccionStockMlResponse(List.of(mlItem()))));
        when(prediccionStockRepository.findByGranjaIdAndMateriaPrimaId(GRANJA, 10L))
                .thenReturn(Optional.empty());

        PrediccionStockDetalleResponse detalle =
                service.predecirMateriaPrima(TENANT, GRANJA, 10L);

        assertThat(detalle.resumen().nombreMateriaPrima()).isEqualTo("Maíz");
        assertThat(detalle.serieHistorica()).hasSize(2);
        assertThat(detalle.serieProyeccion()).hasSize(2);
        assertThat(detalle.serieProyeccion().get(1).existencias()).isEqualTo(0.0);
        verify(prediccionStockRepository, times(1)).save(any(PrediccionStock.class));
    }

    @Test
    void predecirMateriaPrima_failOpen_sinDatos_noPersiste() {
        planPermite();
        when(materiaPrimaRepository.findByIdAndGranjaId(10L, GRANJA)).thenReturn(Optional.of(maiz));
        when(compraDetalleRepository.ingresosMensualesPorMateria(
                        eq(GRANJA), eq(EstadoCompra.REGISTRADA), any()))
                .thenReturn(List.of());
        when(fabricacionDetalleRepository.consumosMensualesPorMateria(eq(GRANJA), any()))
                .thenReturn(List.of());
        when(inventarioRepository.findByGranjaIdOrderByMateriaPrimaCodigoMateriaPrimaAsc(GRANJA))
                .thenReturn(List.of());
        when(mlClient.predecirStock(any())).thenReturn(Optional.empty()); // api-ml caído

        PrediccionStockDetalleResponse detalle =
                service.predecirMateriaPrima(TENANT, GRANJA, 10L);

        assertThat(detalle.resumen().nivelAlerta()).isEqualTo("SIN_DATOS");
        assertThat(detalle.serieHistorica()).isEmpty();
        verify(prediccionStockRepository, never()).save(any());
    }

    @Test
    void predecirGranja_failOpen_devuelveVacio() {
        planPermite();
        when(materiaPrimaRepository.findByGranjaIdAndActivaTrueOrderByNombreMateriaPrimaAsc(GRANJA))
                .thenReturn(List.of(maiz));
        when(compraDetalleRepository.ingresosMensualesPorMateria(
                        eq(GRANJA), eq(EstadoCompra.REGISTRADA), any()))
                .thenReturn(List.of());
        when(fabricacionDetalleRepository.consumosMensualesPorMateria(eq(GRANJA), any()))
                .thenReturn(List.of());
        when(inventarioRepository.findByGranjaIdOrderByMateriaPrimaCodigoMateriaPrimaAsc(GRANJA))
                .thenReturn(List.of());
        when(mlClient.predecirStock(any())).thenReturn(Optional.empty());

        assertThat(service.predecirGranja(TENANT, GRANJA)).isEmpty();
        verify(prediccionStockRepository, never()).save(any());
    }
}
