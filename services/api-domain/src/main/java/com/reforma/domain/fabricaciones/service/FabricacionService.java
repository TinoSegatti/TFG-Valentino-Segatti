package com.reforma.domain.fabricaciones.service;

import com.reforma.domain.common.util.IdGenerator;
import com.reforma.domain.fabricaciones.domain.EstadoFabricacion;
import com.reforma.domain.fabricaciones.dto.FabricacionCabeceraRequest;
import com.reforma.domain.fabricaciones.dto.FabricacionCompletaResponse;
import com.reforma.domain.fabricaciones.dto.FabricacionResumenResponse;
import com.reforma.domain.fabricaciones.dto.GuardarFabricacionDetalleRequest;
import com.reforma.domain.fabricaciones.entity.Fabricacion;
import com.reforma.domain.fabricaciones.entity.FabricacionDetalle;
import com.reforma.domain.fabricaciones.repository.FabricacionRepository;
import com.reforma.domain.fabricaciones.support.FabricacionCalculo;
import com.reforma.domain.formulas.entity.FormulaCabecera;
import com.reforma.domain.formulas.entity.FormulaDetalle;
import com.reforma.domain.formulas.repository.FormulaCabeceraRepository;
import com.reforma.domain.formulas.support.FormulaCalculo;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.inventario.service.InventarioRecalculoService;
import com.reforma.domain.inventario.support.InventarioValoresCalculados;
import com.reforma.domain.suscripciones.service.PlanService;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FabricacionService {

    private final FabricacionRepository fabricacionRepository;
    private final FormulaCabeceraRepository formulaCabeceraRepository;
    private final GranjaRepository granjaRepository;
    private final UsuarioRepository usuarioRepository;
    private final GranjaAccesoService granjaAccesoService;
    private final InventarioRecalculoService inventarioRecalculoService;
    private final PlanService planService;

    @Transactional(readOnly = true)
    public List<FabricacionResumenResponse> listar(String idUsuario, String idGranja) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        return fabricacionRepository.findByGranjaIdAndActivoTrueOrderByFechaFabricacionDesc(idGranja).stream()
                .map(FabricacionResumenResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FabricacionCompletaResponse obtener(String idUsuario, String idGranja, String idFabricacion) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        return FabricacionCompletaResponse.from(obtenerFabricacion(idGranja, idFabricacion));
    }

    @Transactional
    public FabricacionCompletaResponse crearCabecera(
            String idUsuario, String idGranja, FabricacionCabeceraRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        validarLimitePlan(idUsuario, idGranja);
        validarFechaNoFutura(request.fechaFabricacion());

        String codigo = request.codigoFabricacion().trim();
        if (fabricacionRepository.existsByGranjaIdAndCodigoFabricacionIgnoreCaseAndActivoTrue(idGranja, codigo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Ya existe una fabricacion activa con codigo " + codigo);
        }

        Granja granja = granjaRepository.findById(idGranja).orElseThrow();
        Usuario usuario = usuarioRepository.findById(idUsuario).orElseThrow();

        Fabricacion fabricacion = Fabricacion.builder()
                .id(IdGenerator.newId())
                .granja(granja)
                .usuario(usuario)
                .codigoFabricacion(codigo)
                .descripcionFabricacion(request.descripcionFabricacion().trim())
                .fechaFabricacion(request.fechaFabricacion().atStartOfDay(ZoneOffset.UTC).toInstant())
                .observaciones(normalizar(request.observaciones()))
                .cantidadFabricacion(0.0)
                .costoTotalFabricacion(0.0)
                .costoPorKilo(0.0)
                .sinExistencias(false)
                .activo(true)
                .estado(EstadoFabricacion.BORRADOR)
                .build();

        return FabricacionCompletaResponse.from(fabricacionRepository.save(fabricacion));
    }

    @Transactional
    public FabricacionCompletaResponse actualizarCabecera(
            String idUsuario,
            String idGranja,
            String idFabricacion,
            FabricacionCabeceraRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        validarFechaNoFutura(request.fechaFabricacion());

        Fabricacion fabricacion = obtenerFabricacion(idGranja, idFabricacion);
        String codigo = request.codigoFabricacion().trim();
        if (!codigo.equalsIgnoreCase(fabricacion.getCodigoFabricacion())
                && fabricacionRepository.existsByGranjaIdAndCodigoFabricacionIgnoreCaseAndActivoTrueAndIdNot(
                        idGranja, codigo, idFabricacion)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Codigo duplicado en la granja: " + codigo);
        }

        fabricacion.setCodigoFabricacion(codigo);
        fabricacion.setDescripcionFabricacion(request.descripcionFabricacion().trim());
        fabricacion.setFechaFabricacion(request.fechaFabricacion().atStartOfDay(ZoneOffset.UTC).toInstant());
        fabricacion.setObservaciones(normalizar(request.observaciones()));
        return FabricacionCompletaResponse.from(fabricacionRepository.save(fabricacion));
    }

    @Transactional
    public FabricacionCompletaResponse guardarDetalle(
            String idUsuario,
            String idGranja,
            String idFabricacion,
            GuardarFabricacionDetalleRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        Fabricacion fabricacion = obtenerFabricacion(idGranja, idFabricacion);

        Set<Long> materiasAfectadas = new LinkedHashSet<>();
        fabricacion.getDetalles().forEach(d -> materiasAfectadas.add(d.getMateriaPrima().getId()));

        FormulaCabecera formula = formulaCabeceraRepository
                .findByIdAndGranjaIdAndActivaTrue(request.idFormula(), idGranja)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formula no valida o inactiva"));

        validarFormulaCompleta(formula);

        double veces = FabricacionCalculo.redondear(request.veces());
        double kilos = FabricacionCalculo.vecesToKg(veces);

        boolean formulaCambiada =
                fabricacion.getFormula() == null || !fabricacion.getFormula().getId().equals(formula.getId());
        double costoUnitario;
        if (formulaCambiada || fabricacion.getCostoUnitarioFormulaSnapshot() == null) {
            costoUnitario = FabricacionCalculo.redondear(formula.getCostoTotalFormula());
        } else {
            costoUnitario = fabricacion.getCostoUnitarioFormulaSnapshot();
        }

        double costoTotal = FabricacionCalculo.costoTotal(costoUnitario, veces);
        double costoPorKilo = FabricacionCalculo.costoPorKilo(costoTotal, kilos);

        List<FabricacionDetalle> nuevasLineas = construirDetalle(fabricacion, formula, veces);
        boolean sinExistencias = evaluarSinExistencias(idGranja, nuevasLineas);

        fabricacion.getDetalles().clear();
        fabricacion.getDetalles().addAll(nuevasLineas);
        fabricacion.setFormula(formula);
        fabricacion.setCodigoFormulaSnapshot(formula.getCodigoFormula());
        fabricacion.setDescripcionFormulaSnapshot(formula.getDescripcionFormula());
        fabricacion.setCostoUnitarioFormulaSnapshot(costoUnitario);
        fabricacion.setCantidadFabricacion(kilos);
        fabricacion.setCostoTotalFabricacion(costoTotal);
        fabricacion.setCostoPorKilo(costoPorKilo);
        fabricacion.setSinExistencias(sinExistencias);
        fabricacion.setEstado(EstadoFabricacion.REGISTRADA);

        fabricacionRepository.saveAndFlush(fabricacion);

        nuevasLineas.forEach(d -> materiasAfectadas.add(d.getMateriaPrima().getId()));
        inventarioRecalculoService.recalcularPorMaterias(idGranja, materiasAfectadas);

        return FabricacionCompletaResponse.from(fabricacion);
    }

    @Transactional
    public void eliminar(String idUsuario, String idGranja, String idFabricacion) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        Fabricacion fabricacion = obtenerFabricacion(idGranja, idFabricacion);

        Set<Long> materiasAfectadas = new HashSet<>();
        fabricacion.getDetalles().forEach(d -> materiasAfectadas.add(d.getMateriaPrima().getId()));

        fabricacion.setActivo(false);
        fabricacion.setFechaEliminacion(Instant.now());
        fabricacion.setEliminadoPor(idUsuario);
        fabricacionRepository.saveAndFlush(fabricacion);

        if (!materiasAfectadas.isEmpty()) {
            inventarioRecalculoService.recalcularPorMaterias(idGranja, materiasAfectadas);
        }
    }

    private List<FabricacionDetalle> construirDetalle(
            Fabricacion fabricacion, FormulaCabecera formula, double veces) {
        return formula.getDetalles().stream()
                .map(fd -> FabricacionDetalle.builder()
                        .id(IdGenerator.newId())
                        .fabricacion(fabricacion)
                        .materiaPrima(fd.getMateriaPrima())
                        .cantidadUsada(FabricacionCalculo.cantidadUsada(fd.getCantidadKg(), veces))
                        .precioUnitario(fd.getPrecioUnitarioMomentoCreacion())
                        .costoParcial(FabricacionCalculo.redondear(fd.getCostoParcial() * veces))
                        .codigoMpSnapshot(fd.getMateriaPrima().getCodigoMateriaPrima())
                        .nombreMpSnapshot(fd.getMateriaPrima().getNombreMateriaPrima())
                        .build())
                .toList();
    }

    private boolean evaluarSinExistencias(String idGranja, List<FabricacionDetalle> lineas) {
        for (FabricacionDetalle linea : lineas) {
            InventarioValoresCalculados valores =
                    inventarioRecalculoService.calcularValores(idGranja, linea.getMateriaPrima().getId());
            if (valores.cantidadReal() < linea.getCantidadUsada()) {
                return true;
            }
        }
        return false;
    }

    private void validarFormulaCompleta(FormulaCabecera formula) {
        if (formula.getDetalles().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "La formula no tiene ingredientes cargados");
        }
        double sumaKg = FormulaCalculo.redondear(
                formula.getDetalles().stream().mapToDouble(FormulaDetalle::getCantidadKg).sum());
        if (!FormulaCalculo.sumaKgCompleta(sumaKg)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "La formula debe estar completa ("
                            + FormulaCalculo.PESO_LOTE_KG
                            + " kg) para fabricar. Suma actual: "
                            + sumaKg);
        }
    }

    private Fabricacion obtenerFabricacion(String idGranja, String idFabricacion) {
        return fabricacionRepository
                .findByIdAndGranjaIdAndActivoTrue(idFabricacion, idGranja)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fabricacion no encontrada"));
    }

    private void validarFechaNoFutura(LocalDate fecha) {
        if (fecha.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha no puede ser futura");
        }
    }

    private void validarLimitePlan(String idUsuario, String idGranja) {
        int limite = planService.limiteFabricaciones(planService.obtenerPlanEfectivo(idUsuario));
        if (limite == Integer.MAX_VALUE) {
            return;
        }
        long actuales = fabricacionRepository.countByGranjaIdAndActivoTrue(idGranja);
        if (actuales >= limite) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Limite de fabricaciones del plan alcanzado (" + limite + ")");
        }
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        return t.isEmpty() ? null : t;
    }
}
