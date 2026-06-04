package com.reforma.domain.compras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.dto.CompraCabeceraRequest;
import com.reforma.domain.compras.dto.CompraDetalleLineRequest;
import com.reforma.domain.compras.dto.GuardarCompraDetalleRequest;
import com.reforma.domain.compras.repository.CompraCabeceraRepository;
import com.reforma.domain.compras.service.CompraService;
import com.reforma.domain.materiasprimas.dto.MateriaPrimaRequest;
import com.reforma.domain.materiasprimas.service.MateriaPrimaService;
import com.reforma.domain.proveedores.dto.ProveedorRequest;
import com.reforma.domain.proveedores.service.ProveedorService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers(disabledWithoutDocker = true)
class ComprasIntegracionIT {

    private static final String ID_USUARIO = "u_compra_it";
    private static final String ID_GRANJA = "g_compra_it";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("reforma_compras_it")
                    .withUsername("reforma")
                    .withPassword("reforma_it");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private CompraService compraService;
    @Autowired private ProveedorService proveedorService;
    @Autowired private MateriaPrimaService materiaPrimaService;
    @Autowired private CompraCabeceraRepository compraCabeceraRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long idProveedor;
    private long idMpMaiz;
    private long idMpSoja;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("DELETE FROM t_compra_detalle WHERE id_compra IN "
                + "(SELECT id FROM t_compra_cabecera WHERE id_granja = '" + ID_GRANJA + "')");
        jdbcTemplate.execute("DELETE FROM t_compra_cabecera WHERE id_granja = '" + ID_GRANJA + "'");
        jdbcTemplate.execute("DELETE FROM t_materia_prima WHERE id_granja = '" + ID_GRANJA + "'");
        jdbcTemplate.execute("DELETE FROM t_proveedor WHERE id_granja = '" + ID_GRANJA + "'");
        jdbcTemplate.execute(
                """
                INSERT INTO t_usuarios (
                    id, email, password_hash, nombre_usuario, apellido_usuario,
                    tipo_usuario, plan_suscripcion, max_granjas, activo, email_verificado,
                    es_usuario_empleado, activo_como_empleado, fecha_registro
                ) VALUES (
                    'u_compra_it', 'compras-it@test.local', 'noop',
                    'IT', 'Compras', 'CLIENTE', 'ENTERPRISE', 10,
                    true, true, false, false, NOW()
                ) ON CONFLICT (email) DO NOTHING
                """);
        jdbcTemplate.execute(
                """
                INSERT INTO t_granja (id, id_usuario, nombre_granja, activa, fecha_creacion)
                VALUES ('g_compra_it', 'u_compra_it', 'Granja Compras IT', true, NOW())
                ON CONFLICT (id) DO NOTHING
                """);

        idProveedor = proveedorService
                .crear(
                        ID_USUARIO,
                        ID_GRANJA,
                        new ProveedorRequest("PROV1", "Proveedor IT", null, null, null, null, null, null))
                .id();
        idMpMaiz = materiaPrimaService
                .crear(ID_USUARIO, ID_GRANJA, new MateriaPrimaRequest("MAIZ", "Maíz", 3_000.125))
                .id();
        idMpSoja = materiaPrimaService
                .crear(ID_USUARIO, ID_GRANJA, new MateriaPrimaRequest("SOJA", "Soja", 2_500.333))
                .id();
    }

    @Test
    @DisplayName("guardarDetalle: factura 30M con dos líneas decimales → REGISTRADA")
    void guardarDetalle_facturaGrandeDosLineas() {
        var cabecera = compraService.crearCabecera(
                ID_USUARIO,
                ID_GRANJA,
                new CompraCabeceraRequest(
                        idProveedor,
                        "F-30M",
                        LocalDate.now(ZoneOffset.UTC),
                        30_000_000.0,
                        null));

        var response = compraService.guardarDetalle(
                ID_USUARIO,
                ID_GRANJA,
                cabecera.id(),
                new GuardarCompraDetalleRequest(List.of(
                        new CompraDetalleLineRequest(idMpMaiz, 5_000.0, 3_000.0, 15_000_000.0),
                        new CompraDetalleLineRequest(idMpSoja, 6_000.0, 2_500.0, 15_000_000.0))));

        assertThat(response.estado()).isEqualTo(EstadoCompra.REGISTRADA);
        assertThat(response.lineas()).hasSize(2);
        assertThat(response.sumaSubtotales()).isEqualTo(30_000_000.0);

        var persistida = compraCabeceraRepository.findById(cabecera.id()).orElseThrow();
        assertThat(persistida.getEstado()).isEqualTo(EstadoCompra.REGISTRADA);
        assertThat(persistida.getDetalles()).hasSize(2);
    }

    @Test
    @DisplayName("guardarDetalle: línea con cantidad/precio decimales cuadra con tolerancia")
    void guardarDetalle_lineaConDecimales() {
        double cantidad = 100.125;
        double precio = 300.333;
        double subtotal = 30_070.842;

        var cabecera = compraService.crearCabecera(
                ID_USUARIO,
                ID_GRANJA,
                new CompraCabeceraRequest(
                        idProveedor,
                        "F-DEC",
                        LocalDate.now(ZoneOffset.UTC),
                        subtotal,
                        null));

        var response = compraService.guardarDetalle(
                ID_USUARIO,
                ID_GRANJA,
                cabecera.id(),
                new GuardarCompraDetalleRequest(
                        List.of(new CompraDetalleLineRequest(idMpMaiz, cantidad, precio, subtotal))));

        assertThat(response.estado()).isEqualTo(EstadoCompra.REGISTRADA);
        assertThat(response.lineas().getFirst().cantidadKg()).isEqualTo(100.125);
        assertThat(response.lineas().getFirst().precioPorKilo()).isEqualTo(300.333);
    }

    @Test
    @DisplayName("guardarDetalle: rechaza suma fuera de tolerancia ±0,50")
    void guardarDetalle_sumaFueraDeTolerancia() {
        var cabecera = compraService.crearCabecera(
                ID_USUARIO,
                ID_GRANJA,
                new CompraCabeceraRequest(
                        idProveedor,
                        "F-TOL",
                        LocalDate.now(ZoneOffset.UTC),
                        1_000.0,
                        null));

        assertThatThrownBy(() -> compraService.guardarDetalle(
                        ID_USUARIO,
                        ID_GRANJA,
                        cabecera.id(),
                        new GuardarCompraDetalleRequest(
                                List.of(new CompraDetalleLineRequest(idMpMaiz, 10.0, 100.0, 999.0)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("suma de subtotales");
    }

    @Test
    @DisplayName("guardarDetalle: acepta diferencia de total dentro de tolerancia ±0,50")
    void guardarDetalle_totalDentroDeTolerancia() {
        var cabecera = compraService.crearCabecera(
                ID_USUARIO,
                ID_GRANJA,
                new CompraCabeceraRequest(
                        idProveedor,
                        "F-OK-TOL",
                        LocalDate.now(ZoneOffset.UTC),
                        1_000.0,
                        null));

        var response = compraService.guardarDetalle(
                ID_USUARIO,
                ID_GRANJA,
                cabecera.id(),
                new GuardarCompraDetalleRequest(
                        List.of(new CompraDetalleLineRequest(idMpMaiz, 10.0, 100.0, 999.6))));

        assertThat(response.estado()).isEqualTo(EstadoCompra.REGISTRADA);
        assertThat(response.sumaSubtotales()).isEqualTo(999.6);
    }
}
