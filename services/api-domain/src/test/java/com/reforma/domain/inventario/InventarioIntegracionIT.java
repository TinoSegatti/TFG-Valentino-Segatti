package com.reforma.domain.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import com.reforma.domain.compras.dto.CompraCabeceraRequest;
import com.reforma.domain.compras.dto.CompraDetalleLineRequest;
import com.reforma.domain.compras.dto.GuardarCompraDetalleRequest;
import com.reforma.domain.compras.service.CompraService;
import com.reforma.domain.inventario.dto.ActualizarCantidadRealRequest;
import com.reforma.domain.inventario.dto.InicializarInventarioRequest;
import com.reforma.domain.inventario.dto.InventarioInicialLineRequest;
import com.reforma.domain.inventario.repository.InventarioRepository;
import com.reforma.domain.inventario.service.InventarioService;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers(disabledWithoutDocker = true)
class InventarioIntegracionIT {

    private static final String ID_USUARIO = "u_inv_it";
    private static final String ID_GRANJA = "g_inv_it";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("reforma_inv_it")
                    .withUsername("reforma")
                    .withPassword("reforma_it");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private InventarioService inventarioService;
    @Autowired private InventarioRepository inventarioRepository;
    @Autowired private CompraService compraService;
    @Autowired private ProveedorService proveedorService;
    @Autowired private MateriaPrimaService materiaPrimaService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long idProveedor;
    private long idMpTrigo;
    private long idMpSoja;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("DELETE FROM t_inventario WHERE id_granja = '" + ID_GRANJA + "'");
        jdbcTemplate.execute("DELETE FROM t_inventario_inicial WHERE id_granja = '" + ID_GRANJA + "'");
        jdbcTemplate.execute("DELETE FROM t_compra_detalle WHERE id_compra IN "
                + "(SELECT id FROM t_compra_cabecera WHERE id_granja = '" + ID_GRANJA + "')");
        jdbcTemplate.execute("DELETE FROM t_registro_precio WHERE id_materia_prima IN "
                + "(SELECT id FROM t_materia_prima WHERE id_granja = '" + ID_GRANJA + "')");
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
                    'u_inv_it', 'inv-it@test.local', 'noop',
                    'IT', 'Inventario', 'CLIENTE', 'ENTERPRISE', 10,
                    true, true, false, false, NOW()
                ) ON CONFLICT (email) DO NOTHING
                """);
        jdbcTemplate.execute(
                """
                INSERT INTO t_granja (id, id_usuario, nombre_granja, activa, fecha_creacion)
                VALUES ('g_inv_it', 'u_inv_it', 'Granja Inventario IT', true, NOW())
                ON CONFLICT (id) DO NOTHING
                """);

        idProveedor = proveedorService
                .crear(
                        ID_USUARIO,
                        ID_GRANJA,
                        new ProveedorRequest("PROV1", "Proveedor IT", null, null, null, null, null, null))
                .id();
        idMpTrigo = materiaPrimaService
                .crear(ID_USUARIO, ID_GRANJA, new MateriaPrimaRequest("TRIGO", "Trigo", 0.0))
                .id();
        idMpSoja = materiaPrimaService
                .crear(ID_USUARIO, ID_GRANJA, new MateriaPrimaRequest("SOJA", "Soja", 0.0))
                .id();
    }

    @Test
    @DisplayName("listar: incluye todas las MPs activas aun sin fila en t_inventario")
    void listar_todasLasMateriasPrimasActivas() {
        var listado = inventarioService.listar(ID_USUARIO, ID_GRANJA);

        assertThat(listado.inicializado()).isFalse();
        assertThat(listado.items()).hasSize(2);
        assertThat(listado.items())
                .extracting(i -> i.codigoMateriaPrima())
                .containsExactlyInAnyOrder("SOJA", "TRIGO");
        assertThat(listado.items()).allMatch(i -> i.cantidadAcumulada() == 0.0);
    }

    @Test
    @DisplayName("inicializar + compra: precio almacen ponderado y valor stock con precio vigente")
    void inicializarYCompra_precioAlmacenYValorStock() {
        inventarioService.inicializar(
                ID_USUARIO,
                ID_GRANJA,
                new InicializarInventarioRequest(List.of(
                        new InventarioInicialLineRequest(idMpTrigo, 100.0, 10.0))));

        var cabecera = compraService.crearCabecera(
                ID_USUARIO,
                ID_GRANJA,
                new CompraCabeceraRequest(
                        idProveedor,
                        "F-001",
                        LocalDate.now(ZoneOffset.UTC),
                        600.0,
                        null));
        compraService.guardarDetalle(
                ID_USUARIO,
                ID_GRANJA,
                cabecera.id(),
                new GuardarCompraDetalleRequest(
                        List.of(new CompraDetalleLineRequest(idMpTrigo, 50.0, 12.0, 600.0))));

        var trigo = inventarioService.listar(ID_USUARIO, ID_GRANJA).items().stream()
                .filter(i -> i.idMateriaPrima().equals(idMpTrigo))
                .findFirst()
                .orElseThrow();

        assertThat(trigo.cantidadAcumulada()).isEqualTo(150.0);
        assertThat(trigo.cantidadSistema()).isEqualTo(150.0);
        assertThat(trigo.precioPorKilo()).isEqualTo(12.0);
        assertThat(trigo.precioAlmacen()).isEqualTo(10.667);
        assertThat(trigo.valorStock()).isEqualTo(1800.0); // 150 kg * 12 $/kg
    }

    @Test
    @DisplayName("actualizar cantidad real: ajusta merma y valor stock")
    void actualizarCantidadReal_ajustaMermaYValor() {
        inventarioService.inicializar(
                ID_USUARIO,
                ID_GRANJA,
                new InicializarInventarioRequest(List.of(
                        new InventarioInicialLineRequest(idMpTrigo, 100.0, 10.0))));

        var actualizado = inventarioService.actualizarCantidadReal(
                ID_USUARIO,
                ID_GRANJA,
                idMpTrigo,
                new ActualizarCantidadRealRequest(95.0, "Conteo fisico"));

        assertThat(actualizado.cantidadReal()).isEqualTo(95.0);
        assertThat(actualizado.merma()).isEqualTo(5.0);
        assertThat(actualizado.valorStock()).isEqualTo(950.0);
        assertThat(inventarioRepository.findByGranjaIdAndMateriaPrimaId(ID_GRANJA, idMpTrigo))
                .isPresent();
    }
}
