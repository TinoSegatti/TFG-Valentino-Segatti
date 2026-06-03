package com.reforma.domain.proveedores.service;

import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.proveedores.dto.ProveedorRequest;
import com.reforma.domain.proveedores.dto.ProveedorResponse;
import com.reforma.domain.proveedores.entity.Proveedor;
import com.reforma.domain.proveedores.repository.ProveedorRepository;
import com.reforma.domain.suscripciones.service.PlanService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lógica de negocio del módulo Proveedores (RF-PROV-001 / RF-PROV-002).
 *
 * <p>Invariantes que el servicio garantiza en cada operación de escritura:
 * <ol>
 *   <li>El usuario autenticado debe tener acceso a la granja (multi-tenancy).</li>
 *   <li>El código de proveedor es único dentro de la granja (case-insensitive).</li>
 *   <li>El número de proveedores activos por granja no excede el límite del plan.</li>
 * </ol>
 *
 * <p>La baja es <em>lógica</em> ({@code activo=false}) para preservar la trazabilidad
 * exigida por RF-PROV-002 (un proveedor inactivo no aparece en selectors pero el historial
 * de compras se mantiene).
 */
@Service
@RequiredArgsConstructor
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;
    private final GranjaRepository granjaRepository;
    private final GranjaAccesoService granjaAccesoService;
    private final PlanService planService;

    @Transactional(readOnly = true)
    public List<ProveedorResponse> listarPorGranja(String idUsuario, String idGranja, String buscar) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        List<Proveedor> activos = (buscar == null || buscar.isBlank())
                ? proveedorRepository.findByGranjaIdAndActivoTrueOrderByNombreProveedorAsc(idGranja)
                : proveedorRepository
                        .findByGranjaIdAndActivoTrueAndNombreProveedorContainingIgnoreCaseOrderByNombreProveedorAsc(
                                idGranja, buscar.trim());
        return activos.stream().map(ProveedorResponse::from).toList();
    }

    /**
     * Alta de proveedor. Política de soft-delete + entidades versionadas (ADR 0005):
     * <ul>
     *   <li>Rechaza con 409 si ya existe otro proveedor <b>activo</b> con el mismo código.</li>
     *   <li>Si solo existen proveedores <b>inactivos</b> con ese código, quedan intactos y se
     *       inserta una fila nueva (id autoincremental distinto), preservando el histórico.</li>
     * </ul>
     */
    @Transactional
    public ProveedorResponse crear(String idUsuario, String idGranja, ProveedorRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        validarLimitePlan(idUsuario, idGranja);

        String codigo = request.codigoProveedor().trim();
        if (proveedorRepository
                .existsByGranjaIdAndCodigoProveedorIgnoreCaseAndActivoTrue(idGranja, codigo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe un proveedor activo con código " + codigo + " en esta granja");
        }
        Granja granja = granjaRepository.findById(idGranja).orElseThrow();
        Instant now = Instant.now();
        Proveedor proveedor = Proveedor.builder()
                .granja(granja)
                .codigoProveedor(codigo)
                .nombreProveedor(request.nombreProveedor().trim())
                .telefono(normalizar(request.telefono()))
                .email(normalizar(request.email()))
                .cuit(normalizar(request.cuit()))
                .direccion(normalizar(request.direccion()))
                .localidad(normalizar(request.localidad()))
                .notas(normalizar(request.notas()))
                .activo(true)
                .fechaCreacion(now)
                .fechaUltimaActualizacion(now)
                .build();
        return ProveedorResponse.from(proveedorRepository.save(proveedor));
    }

    @Transactional
    public ProveedorResponse actualizar(
            String idUsuario, String idGranja, Long idProveedor, ProveedorRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        Proveedor proveedor = obtenerOFallar(idProveedor, idGranja);

        String nuevoCodigo = request.codigoProveedor().trim();
        // Solo bloqueamos si el código nuevo colisiona con OTRO proveedor ACTIVO (ADR 0005).
        if (!proveedor.getCodigoProveedor().equalsIgnoreCase(nuevoCodigo)
                && proveedorRepository
                        .existsByGranjaIdAndCodigoProveedorIgnoreCaseAndActivoTrue(
                                idGranja, nuevoCodigo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Código duplicado en la granja: " + nuevoCodigo);
        }

        proveedor.setCodigoProveedor(nuevoCodigo);
        proveedor.setNombreProveedor(request.nombreProveedor().trim());
        proveedor.setTelefono(normalizar(request.telefono()));
        proveedor.setEmail(normalizar(request.email()));
        proveedor.setCuit(normalizar(request.cuit()));
        proveedor.setDireccion(normalizar(request.direccion()));
        proveedor.setLocalidad(normalizar(request.localidad()));
        proveedor.setNotas(normalizar(request.notas()));
        proveedor.setFechaUltimaActualizacion(Instant.now());
        return ProveedorResponse.from(proveedor);
    }

    @Transactional
    public void desactivar(String idUsuario, String idGranja, Long idProveedor) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        Proveedor proveedor = obtenerOFallar(idProveedor, idGranja);
        proveedor.setActivo(false);
        proveedor.setFechaUltimaActualizacion(Instant.now());
        // Sin delete() — RF-PROV-002 exige conservar el historial.
    }

    private Proveedor obtenerOFallar(Long idProveedor, String idGranja) {
        return proveedorRepository
                .findByIdAndGranjaId(idProveedor, idGranja)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));
    }

    /**
     * Aplica el plan-gating: si el plan efectivo del usuario ya tope los proveedores activos
     * de esta granja, el alta se rechaza con 403 indicando el límite.
     */
    private void validarLimitePlan(String idUsuario, String idGranja) {
        var plan = planService.obtenerPlanEfectivo(idUsuario);
        int limite = planService.limiteProveedores(plan);
        long actuales = proveedorRepository.countByGranjaIdAndActivoTrue(idGranja);
        if (actuales >= limite) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Plan " + plan.name() + " permite hasta " + limite
                            + " proveedores activos por granja");
        }
    }

    /** Convierte cadenas vacías o solo espacios en {@code null} y trimea las que tienen contenido. */
    private static String normalizar(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
