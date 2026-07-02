package com.reforma.domain.anomalias.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reforma.domain.anomalias.dto.AnomaliaEvaluacionResponse;
import com.reforma.domain.anomalias.dto.EvaluarAnomaliaRequest;
import com.reforma.domain.anomalias.dto.LineaAnomaliaInput;
import com.reforma.domain.anomalias.entity.AnomaliaPrecio;
import com.reforma.domain.anomalias.repository.AnomaliaPrecioRepository;
import com.reforma.domain.compras.entity.CompraCabecera;
import com.reforma.domain.compras.repository.CompraCabeceraRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.materiasprimas.repository.RegistroPrecioRepository;
import com.reforma.domain.ml.MlClient;
import com.reforma.domain.ml.dto.AnomaliaMlResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnomaliaPrecioServiceTest {

    private static final String GRANJA = "g_demo";
    private static final String COMPRA = "c1";

    @Mock private MlClient mlClient;
    @Mock private RegistroPrecioRepository registroPrecioRepository;
    @Mock private MateriaPrimaRepository materiaPrimaRepository;
    @Mock private CompraCabeceraRepository compraCabeceraRepository;
    @Mock private AnomaliaPrecioRepository anomaliaPrecioRepository;
    @Mock private GranjaAccesoService granjaAccesoService;

    @InjectMocks private AnomaliaPrecioService service;

    private final MateriaPrima maiz = MateriaPrima.builder()
            .id(10L)
            .codigoMateriaPrima("MAIZ")
            .nombreMateriaPrima("Maíz")
            .build();

    private static AnomaliaMlResponse ml(String clasificacion) {
        return new AnomaliaMlResponse(clasificacion, 3.1, 100.0, 90.0, 110.0, 25.0, 12, "ESTACIONAL");
    }

    @Test
    void evaluar_anomaliaAltaPideConfirmacion() {
        when(materiaPrimaRepository.findByIdAndGranjaId(10L, GRANJA)).thenReturn(Optional.of(maiz));
        when(registroPrecioRepository.findByMateriaPrimaIdAndOrigenOrderByFechaReferenciaAsc(10L, "COMPRA"))
                .thenReturn(List.of());
        when(mlClient.evaluarAnomalia(any())).thenReturn(Optional.of(ml("ANOMALIA_ALTA")));

        AnomaliaEvaluacionResponse r =
                service.evaluar("u", GRANJA, new EvaluarAnomaliaRequest(10L, 125.0, 6));

        assertThat(r.clasificacion()).isEqualTo("ANOMALIA_ALTA");
        assertThat(r.requiereConfirmacion()).isTrue();
        assertThat(r.mensaje()).contains("superior");
    }

    @Test
    void evaluar_failOpenCuandoMlNoResponde() {
        when(materiaPrimaRepository.findByIdAndGranjaId(10L, GRANJA)).thenReturn(Optional.of(maiz));
        when(registroPrecioRepository.findByMateriaPrimaIdAndOrigenOrderByFechaReferenciaAsc(10L, "COMPRA"))
                .thenReturn(List.of());
        when(mlClient.evaluarAnomalia(any())).thenReturn(Optional.empty());

        AnomaliaEvaluacionResponse r =
                service.evaluar("u", GRANJA, new EvaluarAnomaliaRequest(10L, 125.0, null));

        assertThat(r.clasificacion()).isEqualTo("SIN_EVALUAR");
        assertThat(r.requiereConfirmacion()).isFalse();
    }

    @Test
    void registrarTrasCompra_persisteSoloAtencionYAnomalia() {
        when(registroPrecioRepository
                        .findByMateriaPrimaIdAndOrigenAndCompraIdNotOrderByFechaReferenciaAsc(
                                anyLong(), eq("COMPRA"), eq(COMPRA)))
                .thenReturn(List.of());
        // primera línea anómala (se persiste), segunda normal (se ignora)
        when(mlClient.evaluarAnomalia(any()))
                .thenReturn(Optional.of(ml("ANOMALIA_ALTA")))
                .thenReturn(Optional.of(ml("NORMAL")));
        when(compraCabeceraRepository.getReferenceById(COMPRA)).thenReturn(new CompraCabecera());
        when(materiaPrimaRepository.getReferenceById(10L)).thenReturn(maiz);

        // Sin transacción activa, la persistencia corre en línea (no espera afterCommit).
        service.registrarTrasCompra(
                GRANJA,
                COMPRA,
                Instant.now(),
                List.of(new LineaAnomaliaInput(10L, 125.0), new LineaAnomaliaInput(11L, 100.0)),
                Map.of(10L, Boolean.TRUE));

        verify(anomaliaPrecioRepository, times(1)).save(any(AnomaliaPrecio.class));
    }
}
