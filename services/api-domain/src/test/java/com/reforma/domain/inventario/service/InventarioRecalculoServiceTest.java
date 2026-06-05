package com.reforma.domain.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.repository.CompraDetalleRepository;
import com.reforma.domain.compras.support.CompraTotalesMateriaPrima;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.inventario.entity.Inventario;
import com.reforma.domain.inventario.repository.InventarioInicialRepository;
import com.reforma.domain.inventario.repository.InventarioRepository;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventarioRecalculoServiceTest {

    private static final String ID_GRANJA = "g_demo";

    @Mock private InventarioRepository inventarioRepository;
    @Mock private InventarioInicialRepository inventarioInicialRepository;
    @Mock private CompraDetalleRepository compraDetalleRepository;
    @Mock private MateriaPrimaRepository materiaPrimaRepository;
    @Mock private GranjaRepository granjaRepository;

    @InjectMocks private InventarioRecalculoService service;

    private MateriaPrima trigo;
    private Granja granja;

    @BeforeEach
    void setUp() {
        granja = Granja.builder().id(ID_GRANJA).nombreGranja("Demo").activa(true).build();
        trigo = MateriaPrima.builder()
                .id(10L)
                .granja(granja)
                .codigoMateriaPrima("TRIGO")
                .nombreMateriaPrima("Trigo")
                .precioPorKilo(12.0)
                .activa(true)
                .build();
    }

    @Test
    @DisplayName("recalcular: dos compras (100x10 y 50x12) → acumulada 150kg, almacen 10.667, valor con precio vigente 12")
    void recalcular_dosComprasPrecioPonderado() {
        when(materiaPrimaRepository.findById(10L)).thenReturn(Optional.of(trigo));
        when(inventarioInicialRepository.findByGranjaIdAndMateriaPrimaId(ID_GRANJA, 10L))
                .thenReturn(Optional.empty());
        when(compraDetalleRepository.totalPorMateriaPrima(eq(ID_GRANJA), eq(10L), eq(EstadoCompra.REGISTRADA)))
                .thenReturn(new com.reforma.domain.compras.support.CompraTotalesMateriaPrima(150.0, 1600.0));
        when(inventarioRepository.findByGranjaIdAndMateriaPrimaId(ID_GRANJA, 10L)).thenReturn(Optional.empty());
        when(granjaRepository.findById(ID_GRANJA)).thenReturn(Optional.of(granja));
        when(inventarioRepository.save(any(Inventario.class))).thenAnswer(inv -> inv.getArgument(0));

        Inventario inv = service.recalcular(ID_GRANJA, 10L);

        assertThat(inv.getCantidadAcumulada()).isEqualTo(150.0);
        assertThat(inv.getCantidadSistema()).isEqualTo(150.0);
        assertThat(inv.getCantidadReal()).isEqualTo(150.0);
        assertThat(inv.getPrecioAlmacen()).isEqualTo(10.667);
        assertThat(inv.getValorStock()).isEqualTo(1800.0); // 150 * 12 (precio vigente catalogo)
        assertThat(inv.getMerma()).isZero();
    }

    @Test
    @DisplayName("calcularValores: sin compras devuelve ceros")
    void calcularValores_sinCompras() {
        when(materiaPrimaRepository.findById(10L)).thenReturn(Optional.of(trigo));
        when(inventarioInicialRepository.findByGranjaIdAndMateriaPrimaId(ID_GRANJA, 10L))
                .thenReturn(Optional.empty());
        when(compraDetalleRepository.totalPorMateriaPrima(eq(ID_GRANJA), eq(10L), eq(EstadoCompra.REGISTRADA)))
                .thenReturn(CompraTotalesMateriaPrima.vacio());
        when(inventarioRepository.findByGranjaIdAndMateriaPrimaId(ID_GRANJA, 10L))
                .thenReturn(Optional.empty());

        var valores = service.calcularValores(ID_GRANJA, 10L);

        assertThat(valores.cantidadAcumulada()).isZero();
        assertThat(valores.precioAlmacen()).isZero();
    }

    @Test
    @DisplayName("recalcular: preserva diferencia manual real - sistema al actualizar")
    void recalcular_preservaDiferenciaManual() {
        Inventario existente = Inventario.builder()
                .id("inv1")
                .granja(granja)
                .materiaPrima(trigo)
                .cantidadAcumulada(100.0)
                .cantidadSistema(100.0)
                .cantidadReal(95.0)
                .merma(5.0)
                .precioAlmacen(10.0)
                .valorStock(950.0)
                .version(0)
                .build();
        when(materiaPrimaRepository.findById(10L)).thenReturn(Optional.of(trigo));
        when(inventarioInicialRepository.findByGranjaIdAndMateriaPrimaId(ID_GRANJA, 10L))
                .thenReturn(Optional.empty());
        when(compraDetalleRepository.totalPorMateriaPrima(eq(ID_GRANJA), eq(10L), eq(EstadoCompra.REGISTRADA)))
                .thenReturn(new com.reforma.domain.compras.support.CompraTotalesMateriaPrima(150.0, 1600.0));
        when(inventarioRepository.findByGranjaIdAndMateriaPrimaId(ID_GRANJA, 10L))
                .thenReturn(Optional.of(existente));
        when(inventarioRepository.save(any(Inventario.class))).thenAnswer(inv -> inv.getArgument(0));

        Inventario inv = service.recalcular(ID_GRANJA, 10L);

        assertThat(inv.getCantidadSistema()).isEqualTo(150.0);
        // diferencia manual era -5 (95 - 100), se conserva → real = 145
        assertThat(inv.getCantidadReal()).isEqualTo(145.0);
        assertThat(inv.getMerma()).isEqualTo(5.0);
    }
}
