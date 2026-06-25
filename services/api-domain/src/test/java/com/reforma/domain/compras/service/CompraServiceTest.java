package com.reforma.domain.compras.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.dto.CompraCabeceraRequest;
import com.reforma.domain.compras.dto.CompraCompletaResponse;
import com.reforma.domain.compras.dto.CompraDetalleLineRequest;
import com.reforma.domain.compras.dto.GuardarCompraDetalleRequest;
import com.reforma.domain.compras.entity.CompraCabecera;
import com.reforma.domain.compras.repository.CompraCabeceraRepository;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.proveedores.entity.Proveedor;
import com.reforma.domain.proveedores.repository.ProveedorRepository;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CompraServiceTest {

    private static final String ID_USUARIO = "u_demo";
    private static final String ID_GRANJA = "g_demo";
    private static final String ID_COMPRA = "c_test01";

    @Mock private CompraCabeceraRepository compraCabeceraRepository;
    @Mock private ProveedorRepository proveedorRepository;
    @Mock private MateriaPrimaRepository materiaPrimaRepository;
    @Mock private GranjaRepository granjaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private GranjaAccesoService granjaAccesoService;
    @Mock private CompraPrecioMateriaPrimaService compraPrecioMateriaPrimaService;

    @InjectMocks private CompraService compraService;

    private Proveedor proveedor;
    private MateriaPrima materiaPrima;
    private Granja granja;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        granja = Granja.builder().id(ID_GRANJA).nombreGranja("Demo").activa(true).build();
        usuario = Usuario.builder().id(ID_USUARIO).email("demo@test.com").activo(true).build();
        proveedor = Proveedor.builder()
                .id(1L)
                .granja(granja)
                .codigoProveedor("PROV1")
                .nombreProveedor("Proveedor Uno")
                .activo(true)
                .fechaCreacion(Instant.now())
                .fechaUltimaActualizacion(Instant.now())
                .build();
        materiaPrima = MateriaPrima.builder()
                .id(10L)
                .granja(granja)
                .codigoMateriaPrima("MAIZ")
                .nombreMateriaPrima("Maíz")
                .precioPorKilo(100.0)
                .activa(true)
                .fechaCreacion(Instant.now())
                .fechaUltimaActualizacion(Instant.now())
                .build();
    }

    @Test
    @DisplayName("crearCabecera: happy path en estado BORRADOR con snapshots de proveedor")
    void crearCabecera_happyPath() {
        var request = new CompraCabeceraRequest(1L, "F-001", LocalDate.now(ZoneOffset.UTC), 1500.0, null);
        when(proveedorRepository.findByIdAndGranjaId(1L, ID_GRANJA)).thenReturn(Optional.of(proveedor));
        when(granjaRepository.findById(ID_GRANJA)).thenReturn(Optional.of(granja));
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(usuario));
        when(compraCabeceraRepository.save(any(CompraCabecera.class)))
                .thenAnswer(inv -> {
                    CompraCabecera c = inv.getArgument(0);
                    c.setId(ID_COMPRA);
                    return c;
                });

        CompraCompletaResponse response = compraService.crearCabecera(ID_USUARIO, ID_GRANJA, request);

        assertThat(response.estado()).isEqualTo(EstadoCompra.BORRADOR);
        assertThat(response.codigoProveedor()).isEqualTo("PROV1");
        assertThat(response.nombreProveedor()).isEqualTo("Proveedor Uno");
        assertThat(response.totalFactura()).isEqualTo(1500.0);
        verify(granjaAccesoService).validarAcceso(ID_USUARIO, ID_GRANJA);
    }

    @Test
    @DisplayName("crearCabecera: rechaza fecha futura")
    void crearCabecera_fechaFutura() {
        var request = new CompraCabeceraRequest(
                1L, "F-001", LocalDate.now(ZoneOffset.UTC).plusDays(1), 100.0, null);

        assertThatThrownBy(() -> compraService.crearCabecera(ID_USUARIO, ID_GRANJA, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("guardarDetalle: registra compra cuando subtotales coinciden con total")
    void guardarDetalle_happyPath() {
        CompraCabecera cabecera = cabeceraBorrador(1000.0);
        when(compraCabeceraRepository.findByIdAndGranjaIdAndActivoTrue(ID_COMPRA, ID_GRANJA))
                .thenReturn(Optional.of(cabecera));
        when(materiaPrimaRepository.findByIdAndGranjaId(10L, ID_GRANJA))
                .thenReturn(Optional.of(materiaPrima));

        var request = new GuardarCompraDetalleRequest(
                List.of(new CompraDetalleLineRequest(10L, 10.0, 100.0, 1000.0)));

        CompraCompletaResponse response =
                compraService.guardarDetalle(ID_USUARIO, ID_GRANJA, ID_COMPRA, request);

        assertThat(response.estado()).isEqualTo(EstadoCompra.REGISTRADA);
        assertThat(response.lineas()).hasSize(1);
        assertThat(response.lineas().getFirst().codigoMpSnapshot()).isEqualTo("MAIZ");
    }

    @Test
    @DisplayName("guardarDetalle: rechaza si suma no coincide con total de factura")
    void guardarDetalle_totalNoCoincide() {
        CompraCabecera cabecera = cabeceraBorrador(2000.0);
        when(compraCabeceraRepository.findByIdAndGranjaIdAndActivoTrue(ID_COMPRA, ID_GRANJA))
                .thenReturn(Optional.of(cabecera));
        when(materiaPrimaRepository.findByIdAndGranjaId(10L, ID_GRANJA))
                .thenReturn(Optional.of(materiaPrima));

        var request = new GuardarCompraDetalleRequest(
                List.of(new CompraDetalleLineRequest(10L, 10.0, 100.0, 1000.0)));

        assertThatThrownBy(() ->
                        compraService.guardarDetalle(ID_USUARIO, ID_GRANJA, ID_COMPRA, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("guardarDetalle: rechaza línea con cantidad×precio fuera de tolerancia")
    void guardarDetalle_lineaInvalida() {
        CompraCabecera cabecera = cabeceraBorrador(1000.0);
        when(compraCabeceraRepository.findByIdAndGranjaIdAndActivoTrue(ID_COMPRA, ID_GRANJA))
                .thenReturn(Optional.of(cabecera));
        when(materiaPrimaRepository.findByIdAndGranjaId(10L, ID_GRANJA))
                .thenReturn(Optional.of(materiaPrima));

        var request = new GuardarCompraDetalleRequest(
                List.of(new CompraDetalleLineRequest(10L, 10.0, 100.0, 500.0)));

        assertThatThrownBy(() ->
                        compraService.guardarDetalle(ID_USUARIO, ID_GRANJA, ID_COMPRA, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("guardarDetalle: permite actualizar una compra ya registrada")
    void guardarDetalle_actualizaRegistrada() {
        CompraCabecera cabecera = cabeceraBorrador(1000.0);
        cabecera.setEstado(EstadoCompra.REGISTRADA);
        when(compraCabeceraRepository.findByIdAndGranjaIdAndActivoTrue(ID_COMPRA, ID_GRANJA))
                .thenReturn(Optional.of(cabecera));
        when(materiaPrimaRepository.findByIdAndGranjaId(10L, ID_GRANJA))
                .thenReturn(Optional.of(materiaPrima));

        var request = new GuardarCompraDetalleRequest(
                List.of(new CompraDetalleLineRequest(10L, 10.0, 100.0, 1000.0)));

        CompraCompletaResponse response =
                compraService.guardarDetalle(ID_USUARIO, ID_GRANJA, ID_COMPRA, request);

        assertThat(response.estado()).isEqualTo(EstadoCompra.REGISTRADA);
        assertThat(response.lineas()).hasSize(1);
    }

    @Test
    @DisplayName("guardarDetalle: acepta factura 30M con múltiples líneas de materias distintas")
    void guardarDetalle_factura30Millones() {
        CompraCabecera cabecera = cabeceraBorrador(30_000_000.0);
        MateriaPrima soja = MateriaPrima.builder()
                .id(11L)
                .granja(granja)
                .codigoMateriaPrima("SOJA")
                .nombreMateriaPrima("Soja")
                .precioPorKilo(200.0)
                .activa(true)
                .fechaCreacion(Instant.now())
                .fechaUltimaActualizacion(Instant.now())
                .build();
        when(compraCabeceraRepository.findByIdAndGranjaIdAndActivoTrue(ID_COMPRA, ID_GRANJA))
                .thenReturn(Optional.of(cabecera));
        when(materiaPrimaRepository.findByIdAndGranjaId(10L, ID_GRANJA))
                .thenReturn(Optional.of(materiaPrima));
        when(materiaPrimaRepository.findByIdAndGranjaId(11L, ID_GRANJA))
                .thenReturn(Optional.of(soja));

        var request = new GuardarCompraDetalleRequest(List.of(
                new CompraDetalleLineRequest(10L, 5_000.0, 3_000.0, 15_000_000.0),
                new CompraDetalleLineRequest(11L, 6_000.0, 2_500.0, 15_000_000.0)));

        CompraCompletaResponse response =
                compraService.guardarDetalle(ID_USUARIO, ID_GRANJA, ID_COMPRA, request);

        assertThat(response.estado()).isEqualTo(EstadoCompra.REGISTRADA);
        assertThat(response.lineas()).hasSize(2);
    }

    @Test
    @DisplayName("guardarDetalle: rechaza materia prima repetida en el detalle")
    void guardarDetalle_materiaPrimaDuplicada() {
        CompraCabecera cabecera = cabeceraBorrador(2_000.0);
        when(compraCabeceraRepository.findByIdAndGranjaIdAndActivoTrue(ID_COMPRA, ID_GRANJA))
                .thenReturn(Optional.of(cabecera));
        when(materiaPrimaRepository.findByIdAndGranjaId(10L, ID_GRANJA))
                .thenReturn(Optional.of(materiaPrima));

        var request = new GuardarCompraDetalleRequest(List.of(
                new CompraDetalleLineRequest(10L, 10.0, 100.0, 1_000.0),
                new CompraDetalleLineRequest(10L, 10.0, 100.0, 1_000.0)));

        assertThatThrownBy(() ->
                        compraService.guardarDetalle(ID_USUARIO, ID_GRANJA, ID_COMPRA, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("guardarDetalle: acepta subtotal con decimales coherentes con cantidad×precio")
    void guardarDetalle_decimalesLineaDentroTolerancia() {
        CompraCabecera cabecera = cabeceraBorrador(30_070.842);
        when(compraCabeceraRepository.findByIdAndGranjaIdAndActivoTrue(ID_COMPRA, ID_GRANJA))
                .thenReturn(Optional.of(cabecera));
        when(materiaPrimaRepository.findByIdAndGranjaId(10L, ID_GRANJA))
                .thenReturn(Optional.of(materiaPrima));

        var request = new GuardarCompraDetalleRequest(
                List.of(new CompraDetalleLineRequest(10L, 100.125, 300.333, 30_070.842)));

        CompraCompletaResponse response =
                compraService.guardarDetalle(ID_USUARIO, ID_GRANJA, ID_COMPRA, request);

        assertThat(response.estado()).isEqualTo(EstadoCompra.REGISTRADA);
        assertThat(response.lineas().getFirst().subtotal()).isEqualTo(30_070.842);
    }

    @Test
    @DisplayName("guardarDetalle: acepta suma de subtotales con diferencia ≤0,50 respecto al total")
    void guardarDetalle_totalConToleranciaMonetaria() {
        CompraCabecera cabecera = cabeceraBorrador(1_000.0);
        when(compraCabeceraRepository.findByIdAndGranjaIdAndActivoTrue(ID_COMPRA, ID_GRANJA))
                .thenReturn(Optional.of(cabecera));
        when(materiaPrimaRepository.findByIdAndGranjaId(10L, ID_GRANJA))
                .thenReturn(Optional.of(materiaPrima));

        var request = new GuardarCompraDetalleRequest(
                List.of(new CompraDetalleLineRequest(10L, 10.0, 100.0, 999.6)));

        CompraCompletaResponse response =
                compraService.guardarDetalle(ID_USUARIO, ID_GRANJA, ID_COMPRA, request);

        assertThat(response.estado()).isEqualTo(EstadoCompra.REGISTRADA);
    }

    @Test
    @DisplayName("crearCabecera: valida acceso a granja")
    void crearCabecera_sinAcceso() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso"))
                .when(granjaAccesoService)
                .validarAcceso(anyString(), anyString());

        assertThatThrownBy(() -> compraService.crearCabecera(
                        ID_USUARIO,
                        ID_GRANJA,
                        new CompraCabeceraRequest(1L, "F", LocalDate.now(ZoneOffset.UTC), 1.0, null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    private CompraCabecera cabeceraBorrador(double total) {
        return CompraCabecera.builder()
                .id(ID_COMPRA)
                .granja(granja)
                .usuario(usuario)
                .proveedor(proveedor)
                .numeroFactura("F-001")
                .fechaCompra(Instant.now())
                .totalFactura(total)
                .activo(true)
                .codigoProveedorSnapshot("PROV1")
                .nombreProveedorSnapshot("Proveedor Uno")
                .estado(EstadoCompra.BORRADOR)
                .detalles(new ArrayList<>())
                .build();
    }
}
