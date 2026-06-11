package com.reforma.domain.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.reforma.domain.animales.dto.AnimalRequest;
import com.reforma.domain.animales.repository.AnimalRepository;
import com.reforma.domain.animales.service.AnimalService;
import com.reforma.domain.formulas.repository.FormulaCabeceraRepository;
import com.reforma.domain.formulas.service.FormulaService;
import com.reforma.domain.materiasprimas.dto.MateriaPrimaRequest;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.materiasprimas.service.MateriaPrimaService;
import com.reforma.domain.proveedores.dto.ProveedorRequest;
import com.reforma.domain.proveedores.repository.ProveedorRepository;
import com.reforma.domain.proveedores.service.ProveedorService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Tests de integración (Postgres real + Flyway) del import/export CSV de catálogos y fórmulas
 * (RF-MP-004, RF-ANI-003, extensión RF-FORM).
 *
 * <p>Ejercitan el flujo completo Service → JPA → Postgres sobre la utilidad {@code common/csv}:
 * round-trip export→import, resumen de filas OK/error sin abortar el lote, y la unidad de import
 * "fórmula completa" con la regla de 1000 kg.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers(disabledWithoutDocker = true)
class CsvImportacionIntegracionIT {

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
    @Autowired private FormulaService formulaService;
    @Autowired private MateriaPrimaRepository materiaPrimaRepository;
    @Autowired private ProveedorRepository proveedorRepository;
    @Autowired private AnimalRepository animalRepository;
    @Autowired private FormulaCabeceraRepository formulaCabeceraRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limpiarYSeedGranja() {
        // Orden por FKs: detalle de fórmula → cabecera → catálogos.
        jdbcTemplate.execute("DELETE FROM t_formula_detalle");
        jdbcTemplate.execute("DELETE FROM t_formula_cabecera WHERE id_granja = '" + ID_GRANJA + "'");
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
                    'u_it', 'csv-it@test.local', 'noop',
                    'IT', 'Csv', 'CLIENTE', 'ENTERPRISE', 10,
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

    private static InputStream csv(String contenido) {
        return new ByteArrayInputStream(contenido.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- Materias primas ----------

    @Test
    void materiasPrimas_roundTripExportImport() {
        materiaPrimaService.crear(ID_USUARIO, ID_GRANJA, new MateriaPrimaRequest("MAIZ", "Maíz molido", 25.5));
        materiaPrimaService.crear(ID_USUARIO, ID_GRANJA, new MateriaPrimaRequest("SOJA", "Harina de soja", 50.0));

        String exportado = materiaPrimaService.exportarCsv(ID_USUARIO, ID_GRANJA);
        assertThat(exportado)
                .startsWith("codigo,nombre,precio_por_kilo")
                .contains("MAIZ")
                .contains("SOJA");

        // Damos de baja ambas y reimportamos el mismo CSV: deben volver como altas nuevas.
        materiaPrimaRepository.findByGranjaIdAndActivaTrueOrderByNombreMateriaPrimaAsc(ID_GRANJA)
                .forEach(mp -> materiaPrimaService.desactivar(ID_USUARIO, ID_GRANJA, mp.getId()));

        var resultado = materiaPrimaService.importarCsv(ID_USUARIO, ID_GRANJA, csv(exportado));

        assertThat(resultado.filasOk()).isEqualTo(2);
        assertThat(resultado.filasError()).isZero();
        assertThat(materiaPrimaRepository.countByGranjaIdAndActivaTrue(ID_GRANJA)).isEqualTo(2);
    }

    @Test
    void materiasPrimas_importMezcla_okDuplicadoEInvalida_noAbortaElLote() {
        materiaPrimaService.crear(ID_USUARIO, ID_GRANJA, new MateriaPrimaRequest("MAIZ", "Maíz", 25.0));

        String entrada = """
                codigo,nombre,precio_por_kilo
                SOJA,Harina de soja,50
                MAIZ,Maíz duplicado,30
                ,Sin codigo,10
                """;

        var resultado = materiaPrimaService.importarCsv(ID_USUARIO, ID_GRANJA, csv(entrada));

        assertThat(resultado.filasOk()).isEqualTo(1);
        assertThat(resultado.filasError()).isEqualTo(2);
        // El duplicado activo (MAIZ) está en la línea 3 del archivo; la fila sin código en la 4.
        assertThat(resultado.errores()).anySatisfy(e -> {
            assertThat(e.linea()).isEqualTo(3);
            assertThat(e.codigo()).isEqualTo("MAIZ");
        });
        assertThat(resultado.errores()).anySatisfy(e -> assertThat(e.linea()).isEqualTo(4));
        assertThat(materiaPrimaRepository.countByGranjaIdAndActivaTrue(ID_GRANJA)).isEqualTo(2); // MAIZ + SOJA
    }

    // ---------- Proveedores ----------

    @Test
    void proveedores_roundTripConservaCamposOpcionales() {
        proveedorService.crear(
                ID_USUARIO,
                ID_GRANJA,
                new ProveedorRequest(
                        "P001", "Acopio Sur", "351-1234", "ventas@acopio.test",
                        "20-12345678-9", "Ruta 9 km 5", "Córdoba", "Pago a 30 días"));

        String exportado = proveedorService.exportarCsv(ID_USUARIO, ID_GRANJA);
        assertThat(exportado).startsWith("codigo,nombre,telefono,email,cuit,direccion,localidad,notas");

        proveedorRepository.findByGranjaIdAndActivoTrueOrderByNombreProveedorAsc(ID_GRANJA)
                .forEach(p -> proveedorService.desactivar(ID_USUARIO, ID_GRANJA, p.getId()));

        var resultado = proveedorService.importarCsv(ID_USUARIO, ID_GRANJA, csv(exportado));

        assertThat(resultado.filasOk()).isEqualTo(1);
        assertThat(resultado.filasError()).isZero();
        var reimportado = proveedorRepository
                .findByGranjaIdAndActivoTrueOrderByNombreProveedorAsc(ID_GRANJA)
                .get(0);
        assertThat(reimportado.getEmail()).isEqualTo("ventas@acopio.test");
        assertThat(reimportado.getCuit()).isEqualTo("20-12345678-9");
        assertThat(reimportado.getLocalidad()).isEqualTo("Córdoba");
    }

    // ---------- Animales ----------

    @Test
    void animales_importFaltaDescripcion_reportaErrorSinCortar() {
        String entrada = """
                codigo,descripcion,categoria,observaciones
                CAT01,Cerda gestante,Reproductora,ok
                CAT02,,Engorde,sin descripcion
                """;

        var resultado = animalService.importarCsv(ID_USUARIO, ID_GRANJA, csv(entrada));

        assertThat(resultado.filasOk()).isEqualTo(1);
        assertThat(resultado.filasError()).isEqualTo(1);
        assertThat(resultado.errores().get(0).linea()).isEqualTo(3);
        assertThat(animalRepository.existsByGranjaIdAndCodigoAnimalIgnoreCaseAndActivoTrue(ID_GRANJA, "CAT01"))
                .isTrue();
    }

    // ---------- Fórmulas (CSV denormalizado) ----------

    private void seedAnimalYMaterias() {
        animalService.crear(ID_USUARIO, ID_GRANJA, new AnimalRequest("CAT01", "Cerda gestante", "Reproductora", null));
        materiaPrimaService.crear(ID_USUARIO, ID_GRANJA, new MateriaPrimaRequest("MAIZ", "Maíz", 20.0));
        materiaPrimaService.crear(ID_USUARIO, ID_GRANJA, new MateriaPrimaRequest("SOJA", "Soja", 40.0));
    }

    @Test
    void formulas_importCompletaPersisteCabeceraDetalleYCosto() {
        seedAnimalYMaterias();
        String entrada = """
                codigo_formula,descripcion_formula,codigo_animal,codigo_materia_prima,cantidad_kg
                F1,Gestacion,CAT01,MAIZ,600
                F1,Gestacion,CAT01,SOJA,400
                """;

        var resultado = formulaService.importarCsv(ID_USUARIO, ID_GRANJA, csv(entrada));

        assertThat(resultado.filasOk()).isEqualTo(1); // cuenta fórmulas, no líneas
        assertThat(resultado.filasError()).isZero();

        var cabecera = formulaCabeceraRepository
                .findByGranjaIdAndActivaTrueOrderByCodigoFormulaAsc(ID_GRANJA)
                .get(0);
        assertThat(cabecera.getCodigoFormula()).isEqualTo("F1");
        // Costo = 600*20 + 400*40 = 12000 + 16000 = 28000 (campo eager de la cabecera).
        assertThat(cabecera.getCostoTotalFormula()).isEqualTo(28000.0);
        // Estructura persistida vía SQL (sin navegar proxies lazy fuera de sesión):
        // animal correctamente vinculado y dos líneas de detalle.
        String codigoAnimal = jdbcTemplate.queryForObject(
                "SELECT a.codigo_animal FROM t_formula_cabecera c "
                        + "JOIN t_animal a ON a.id = c.id_animal WHERE c.id = ?",
                String.class,
                cabecera.getId());
        assertThat(codigoAnimal).isEqualTo("CAT01");
        Integer cantidadDetalles = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_formula_detalle WHERE id_formula = ?",
                Integer.class,
                cabecera.getId());
        assertThat(cantidadDetalles).isEqualTo(2);

        // Round-trip: el export reproduce las dos líneas de la fórmula.
        String exportado = formulaService.exportarCsv(ID_USUARIO, ID_GRANJA);
        assertThat(exportado)
                .startsWith("codigo_formula,descripcion_formula,codigo_animal,codigo_materia_prima,cantidad_kg")
                .contains("F1")
                .contains("MAIZ")
                .contains("SOJA");
    }

    @Test
    void formulas_sumaDistintaDe1000_reportaErrorYNoPersiste() {
        seedAnimalYMaterias();
        String entrada = """
                codigo_formula,descripcion_formula,codigo_animal,codigo_materia_prima,cantidad_kg
                F1,Gestacion,CAT01,MAIZ,600
                F1,Gestacion,CAT01,SOJA,300
                """;

        var resultado = formulaService.importarCsv(ID_USUARIO, ID_GRANJA, csv(entrada));

        assertThat(resultado.filasOk()).isZero();
        assertThat(resultado.filasError()).isEqualTo(1);
        assertThat(resultado.errores().get(0).codigo()).isEqualTo("F1");
        assertThat(resultado.errores().get(0).mensaje()).contains("1000");
        assertThat(formulaCabeceraRepository.findByGranjaIdAndActivaTrueOrderByCodigoFormulaAsc(ID_GRANJA))
                .isEmpty();
    }

    @Test
    void formulas_cabeceraInconsistente_invalidaToda_laFormula() {
        seedAnimalYMaterias();
        // descripcion distinta dentro del mismo codigo_formula => grupo inválido.
        String entrada = """
                codigo_formula,descripcion_formula,codigo_animal,codigo_materia_prima,cantidad_kg
                F1,Gestacion,CAT01,MAIZ,600
                F1,Lactancia,CAT01,SOJA,400
                """;

        var resultado = formulaService.importarCsv(ID_USUARIO, ID_GRANJA, csv(entrada));

        assertThat(resultado.filasOk()).isZero();
        assertThat(resultado.filasError()).isEqualTo(1);
        assertThat(formulaCabeceraRepository.findByGranjaIdAndActivaTrueOrderByCodigoFormulaAsc(ID_GRANJA))
                .isEmpty();
    }
}
