package com.reforma.domain.compras.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.entity.CompraCabecera;
import com.reforma.domain.compras.entity.CompraDetalle;
import com.reforma.domain.compras.repository.CompraDetalleRepository;
import com.reforma.domain.formulas.service.FormulaCostoSyncService;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.materiasprimas.repository.RegistroPrecioRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CompraPrecioMateriaPrimaServiceTest {

    private static final String ID_GRANJA = "g_demo";

    @Mock private CompraDetalleRepository compraDetalleRepository;
    @Mock private MateriaPrimaRepository materiaPrimaRepository;
    @Mock private RegistroPrecioRepository registroPrecioRepository;
    @Mock private FormulaCostoSyncService formulaCostoSyncService;

    @InjectMocks private CompraPrecioMateriaPrimaService service;

    private Granja granja;
    private MateriaPrima maiz;
    private CompraCabecera compra;

    @BeforeEach
    void setUp() {
        granja = Granja.builder().id(ID_GRANJA).nombreGranja("Demo").activa(true).build();
        maiz = MateriaPrima.builder()
                .id(10L)
                .granja(granja)
                .codigoMateriaPrima("MAIZ")
                .nombreMateriaPrima("Maíz")
                .precioPorKilo(100.0)
                .activa(true)
                .fechaCreacion(Instant.now())
                .fechaUltimaActualizacion(Instant.now())
                .build();
        compra = CompraCabecera.builder()
                .id("c1")
                .granja(granja)
                .numeroFactura("F-001")
                .fechaCompra(Instant.parse("2026-06-01T00:00:00Z"))
                .estado(EstadoCompra.REGISTRADA)
                .activo(true)
                .build();
    }

    @Test
    @DisplayName("aplicarTrasGuardarDetalle: persiste registro y actualiza catálogo con compra más reciente")
    void aplicarTrasGuardarDetalle_actualizaCatalogo() {
        CompraDetalle linea = linea(120.0);
        when(materiaPrimaRepository.findById(10L)).thenReturn(Optional.of(maiz));
        when(compraDetalleRepository.findMasRecientePorMateriaPrima(
                        eq(ID_GRANJA), eq(10L), eq(EstadoCompra.REGISTRADA), any(Pageable.class)))
                .thenReturn(List.of(linea));

        service.aplicarTrasGuardarDetalle(compra, List.of(linea));

        verify(registroPrecioRepository).deleteByCompra_Id("c1");
        var captor = ArgumentCaptor.forClass(com.reforma.domain.materiasprimas.entity.RegistroPrecio.class);
        verify(registroPrecioRepository).save(captor.capture());
        assertThat(captor.getValue().getPrecioNuevo()).isEqualTo(120.0);
        assertThat(captor.getValue().getFechaReferencia()).isEqualTo(compra.getFechaCompra());
        assertThat(captor.getValue().getOrigen()).isEqualTo(CompraPrecioMateriaPrimaService.ORIGEN_COMPRA);
        assertThat(maiz.getPrecioPorKilo()).isEqualTo(120.0);
        verify(formulaCostoSyncService).recalcularPorMateriasPrimas(ID_GRANJA, java.util.Set.of(10L));
    }

    @Test
    @DisplayName("recalcularCatalogo: factura antigua no modifica catálogo si hay compra más reciente")
    void recalcularCatalogo_respetaCompraMasReciente() {
        maiz.setPrecioPorKilo(150.0);
        CompraDetalle lineaReciente = CompraDetalle.builder()
                .precioUnitario(150.0)
                .materiaPrima(maiz)
                .build();
        when(materiaPrimaRepository.findById(10L)).thenReturn(Optional.of(maiz));
        when(compraDetalleRepository.findMasRecientePorMateriaPrima(
                        eq(ID_GRANJA), eq(10L), eq(EstadoCompra.REGISTRADA), any(Pageable.class)))
                .thenReturn(List.of(lineaReciente));

        service.recalcularCatalogo(ID_GRANJA, List.of(10L));

        assertThat(maiz.getPrecioPorKilo()).isEqualTo(150.0);
    }

    @Test
    @DisplayName("calcularDiferenciaPorcentual: evita división por cero")
    void calcularDiferenciaPorcentual() {
        assertThat(CompraPrecioMateriaPrimaService.calcularDiferenciaPorcentual(0, 50)).isZero();
        assertThat(CompraPrecioMateriaPrimaService.calcularDiferenciaPorcentual(100, 120)).isEqualTo(20.0);
    }

    private CompraDetalle linea(double precio) {
        return CompraDetalle.builder()
                .id("d1")
                .compra(compra)
                .materiaPrima(maiz)
                .precioUnitario(precio)
                .precioAnteriorMateriaPrima(100.0)
                .cantidadComprada(10.0)
                .subtotal(precio * 10)
                .build();
    }
}
