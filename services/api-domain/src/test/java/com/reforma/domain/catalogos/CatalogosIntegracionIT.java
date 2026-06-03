package com.reforma.domain.catalogos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reforma.domain.animales.dto.AnimalRequest;
import com.reforma.domain.animales.repository.AnimalRepository;
import com.reforma.domain.animales.service.AnimalService;
import com.reforma.domain.materiasprimas.dto.MateriaPrimaRequest;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.materiasprimas.service.MateriaPrimaService;
import com.reforma.domain.proveedores.dto.ProveedorRequest;
import com.reforma.domain.proveedores.repository.ProveedorRepository;
import com.reforma.domain.proveedores.service.ProveedorService;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Tests de integración (Postgres real + Flyway V001–V005) para los tres catálogos.
 * Valida ID BIGINT autoincremental (ADR 0006) y soft-delete por código (ADR 0005).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers(disabledWithoutDocker = true)
class CatalogosIntegracionIT {

    private static final String ID_USUARIO = "u_it";
    private static final String ID_GRANJA = "g_it";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("reforma_it")
                    .withUsername("reforma")
                    .withPassword("reforma_it");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MateriaPrimaService materiaPrimaService;
    @Autowired private ProveedorService proveedorService;
    @Autowired private AnimalService animalService;
    @Autowired private MateriaPrimaRepository materiaPrimaRepository;
    @Autowired private ProveedorRepository proveedorRepository;
    @Autowired private AnimalRepository animalRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limpiarCatalogosYSeedGranja() {
        jdbcTemplate.execute("DELETE FROM t_animal WHERE id_granja = '" + ID_GRANJA + "'");
        jdbcTemplate.execute("DELETE FROM t_proveedor WHERE id_granja = '" + ID_GRANJA + "'");
        jdbcTemplate.execute("DELETE FROM t_materia_prima WHERE id_granja = '" + ID_GRANJA + "'");
        jdbcTemplate.execute(
                """
                INSERT INTO t_usuarios (
                    id, email, password_hash, nombre_usuario, apellido_usuario,
                    tipo_usuario, plan_suscripcion, max_granjas, activo, email_verificado,
                    es_usuario_empleado, activo_como_empleado, fecha_registro
                ) VALUES (
                    'u_it', 'catalogo-it@test.local', 'noop',
                    'IT', 'Catalogo', 'CLIENTE', 'ENTERPRISE', 10,
                    true, true, false, false, NOW()
                ) ON CONFLICT (email) DO NOTHING
                """);
        jdbcTemplate.execute(
                """
                INSERT INTO t_granja (id, id_usuario, nombre_granja, activa, fecha_creacion)
                VALUES ('g_it', 'u_it', 'Granja IT', true, NOW())
                ON CONFLICT (id) DO NOTHING
                """);
    }

    @Test
    void materiaPrima_crearAsignaIdNumericoYRespetaCodigo() {
        var response = materiaPrimaService.crear(
                ID_USUARIO, ID_GRANJA, new MateriaPrimaRequest("MAIZ", "Maíz molido", 25.0));

        assertThat(response.id()).isNotNull().isPositive();
        assertThat(response.codigoMateriaPrima()).isEqualTo("MAIZ");
        assertThat(materiaPrimaRepository.count()).isEqualTo(1);
    }

    @Test
    void materiaPrima_reutilizarCodigoTrasBajaCreaNuevoId() {
        var primera = materiaPrimaService.crear(
                ID_USUARIO, ID_GRANJA, new MateriaPrimaRequest("MAIZ", "Maíz viejo", 20.0));
        materiaPrimaService.desactivar(ID_USUARIO, ID_GRANJA, primera.id());

        var segunda = materiaPrimaService.crear(
                ID_USUARIO, ID_GRANJA, new MateriaPrimaRequest("MAIZ", "Maíz nuevo", 30.0));

        assertThat(segunda.id()).isNotEqualTo(primera.id());
        assertThat(materiaPrimaRepository.count()).isEqualTo(2);
        var inactiva = materiaPrimaRepository.findById(primera.id()).orElseThrow();
        assertThat(inactiva.getActiva()).isFalse();
        assertThat(inactiva.getNombreMateriaPrima()).isEqualTo("Maíz viejo");
    }

    @Test
    void materiaPrima_codigoDuplicadoActivo_lanza409() {
        materiaPrimaService.crear(
                ID_USUARIO, ID_GRANJA, new MateriaPrimaRequest("SOJA", "Harina de soja", 50.0));

        assertThatThrownBy(() -> materiaPrimaService.crear(
                        ID_USUARIO, ID_GRANJA, new MateriaPrimaRequest("SOJA", "Otra soja", 55.0)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(409));
    }

    @Test
    void proveedor_crearYReutilizarCodigoTrasBaja() {
        var p1 = proveedorService.crear(
                ID_USUARIO,
                ID_GRANJA,
                new ProveedorRequest("P001", "Acopio Sur", null, null, null, null, null, null));
        proveedorService.desactivar(ID_USUARIO, ID_GRANJA, p1.id());

        var p2 = proveedorService.crear(
                ID_USUARIO,
                ID_GRANJA,
                new ProveedorRequest("P001", "Acopio Renovado", null, null, null, null, null, null));

        assertThat(p2.id()).isNotEqualTo(p1.id());
        assertThat(proveedorRepository.count()).isEqualTo(2);
    }

    @Test
    void animal_crearAsignaIdNumerico() {
        var response = animalService.crear(
                ID_USUARIO,
                ID_GRANJA,
                new AnimalRequest("CAT01", "Cerda gestante", "Reproductora", "Notas"));

        assertThat(response.id()).isNotNull().isPositive();
        assertThat(response.codigoAnimal()).isEqualTo("CAT01");
        assertThat(animalRepository.count()).isEqualTo(1);
    }
}
