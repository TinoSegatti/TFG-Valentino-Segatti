package com.reforma.domain.inventario.service;

import com.reforma.domain.common.util.IdGenerator;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.inventario.dto.ActualizarCantidadRealRequest;
import com.reforma.domain.inventario.dto.InicializarInventarioRequest;
import com.reforma.domain.inventario.dto.InventarioInicialLineRequest;
import com.reforma.domain.inventario.dto.InventarioListadoResponse;
import com.reforma.domain.inventario.dto.InventarioResponse;
import com.reforma.domain.inventario.entity.Inventario;
import com.reforma.domain.inventario.entity.InventarioInicial;
import com.reforma.domain.inventario.repository.InventarioInicialRepository;
import com.reforma.domain.inventario.repository.InventarioRepository;
import com.reforma.domain.inventario.support.InventarioCalculo;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class InventarioService {

    private final InventarioRepository inventarioRepository;
    private final InventarioInicialRepository inventarioInicialRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final GranjaAccesoService granjaAccesoService;
    private final InventarioRecalculoService inventarioRecalculoService;

    @Transactional(readOnly = true)
    public InventarioListadoResponse listar(String idUsuario, String idGranja) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        boolean inicializado = inventarioInicialRepository.countByGranjaId(idGranja) > 0;

        Map<Long, Inventario> porMateria = new HashMap<>();
        for (Inventario inv :
                inventarioRepository.findByGranjaIdOrderByMateriaPrimaCodigoMateriaPrimaAsc(idGranja)) {
            porMateria.put(inv.getMateriaPrima().getId(), inv);
        }

        List<InventarioResponse> items = materiaPrimaRepository
                .findByGranjaIdAndActivaTrueOrderByNombreMateriaPrimaAsc(idGranja)
                .stream()
                .map(mp -> {
                    Inventario inv = porMateria.get(mp.getId());
                    if (inv != null) {
                        return InventarioResponse.from(inv);
                    }
                    return InventarioResponse.vista(
                            mp, inventarioRecalculoService.calcularValores(idGranja, mp.getId()));
                })
                .toList();

        return new InventarioListadoResponse(inicializado, items);
    }

    @Transactional
    public List<InventarioResponse> inicializar(
            String idUsuario, String idGranja, InicializarInventarioRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        if (inventarioInicialRepository.countByGranjaId(idGranja) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "El inventario ya esta inicializado. Vacie primero.");
        }
        validarLineas(request.lineas());

        Instant ahora = Instant.now();
        List<InventarioResponse> resultado = new ArrayList<>();
        for (InventarioInicialLineRequest linea : request.lineas()) {
            MateriaPrima mp = materiaPrimaRepository
                    .findByIdAndGranjaId(linea.idMateriaPrima(), idGranja)
                    .filter(MateriaPrima::getActiva)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Materia prima no valida o inactiva: " + linea.idMateriaPrima()));

            double cantidad = InventarioCalculo.redondear(linea.cantidadInicial());
            double precio = InventarioCalculo.redondear(linea.precioInicial());

            inventarioInicialRepository.save(InventarioInicial.builder()
                    .id(IdGenerator.newId())
                    .granja(mp.getGranja())
                    .materiaPrima(mp)
                    .cantidadInicial(cantidad)
                    .precioInicial(precio)
                    .fechaRegistro(ahora)
                    .build());

            // El precio inicial alimenta el catalogo si no hay compras todavia.
            if (precio > 0.0 && (mp.getPrecioPorKilo() == null || mp.getPrecioPorKilo() == 0.0)) {
                mp.setPrecioPorKilo(precio);
                mp.setFechaUltimaActualizacion(ahora);
            }

            Inventario inv = inventarioRecalculoService.recalcular(idGranja, mp.getId());
            inv.setObservaciones("Inicializacion de inventario");
            inventarioRepository.save(inv);
            resultado.add(InventarioResponse.from(inv));
        }
        return resultado;
    }

    @Transactional
    public InventarioResponse actualizarCantidadReal(
            String idUsuario,
            String idGranja,
            Long idMateriaPrima,
            ActualizarCantidadRealRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        materiaPrimaRepository
                .findByIdAndGranjaId(idMateriaPrima, idGranja)
                .filter(MateriaPrima::getActiva)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Materia prima no encontrada o inactiva"));

        Inventario inv = inventarioRepository
                .findByGranjaIdAndMateriaPrimaId(idGranja, idMateriaPrima)
                .orElseGet(() -> inventarioRecalculoService.recalcular(idGranja, idMateriaPrima));

        double cantidadReal = InventarioCalculo.redondear(request.cantidadReal());
        double precioVigente = inv.getMateriaPrima().getPrecioPorKilo() != null
                ? inv.getMateriaPrima().getPrecioPorKilo()
                : 0.0;

        inv.setCantidadReal(cantidadReal);
        inv.setMerma(InventarioCalculo.merma(inv.getCantidadSistema(), cantidadReal));
        inv.setValorStock(InventarioCalculo.valorStock(cantidadReal, precioVigente));
        inv.setFechaUltimaActualizacion(Instant.now());
        if (request.observaciones() != null && !request.observaciones().isBlank()) {
            inv.setObservaciones(request.observaciones().trim());
        }
        return InventarioResponse.from(inventarioRepository.save(inv));
    }

    @Transactional
    public InventarioListadoResponse recalcularTodo(String idUsuario, String idGranja) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        var materias = materiaPrimaRepository.findByGranjaIdAndActivaTrueOrderByNombreMateriaPrimaAsc(idGranja);
        for (MateriaPrima mp : materias) {
            inventarioRecalculoService.recalcular(idGranja, mp.getId());
        }
        return listar(idUsuario, idGranja);
    }

    @Transactional
    public void vaciar(String idUsuario, String idGranja) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        inventarioInicialRepository.deleteByGranjaId(idGranja);
        inventarioRepository.deleteByGranjaId(idGranja);
    }

    private void validarLineas(List<InventarioInicialLineRequest> lineas) {
        Set<Long> vistas = new HashSet<>();
        for (InventarioInicialLineRequest linea : lineas) {
            if (!vistas.add(linea.idMateriaPrima())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Materia prima repetida en la inicializacion");
            }
        }
    }
}
