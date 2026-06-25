package com.reforma.domain.compras.service;

import com.reforma.domain.common.util.IdGenerator;
import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.dto.CompraCabeceraRequest;
import com.reforma.domain.compras.dto.CompraCompletaResponse;
import com.reforma.domain.compras.dto.CompraDetalleLineRequest;
import com.reforma.domain.compras.dto.CompraResumenResponse;
import com.reforma.domain.compras.dto.GuardarCompraDetalleRequest;
import com.reforma.domain.compras.entity.CompraCabecera;
import com.reforma.domain.compras.entity.CompraDetalle;
import com.reforma.domain.compras.repository.CompraCabeceraRepository;
import com.reforma.domain.compras.support.CompraCalculo;
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
public class CompraService {

    private final CompraCabeceraRepository compraCabeceraRepository;
    private final ProveedorRepository proveedorRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final GranjaRepository granjaRepository;
    private final UsuarioRepository usuarioRepository;
    private final GranjaAccesoService granjaAccesoService;
    private final CompraPrecioMateriaPrimaService compraPrecioMateriaPrimaService;

    @Transactional(readOnly = true)
    public List<CompraResumenResponse> listar(String idUsuario, String idGranja) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        return compraCabeceraRepository.findByGranjaIdAndActivoTrueOrderByFechaCompraDesc(idGranja).stream()
                .map(CompraResumenResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompraCompletaResponse obtener(String idUsuario, String idGranja, String idCompra) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        return CompraCompletaResponse.from(obtenerCabecera(idGranja, idCompra));
    }

    @Transactional
    public CompraCompletaResponse crearCabecera(
            String idUsuario, String idGranja, CompraCabeceraRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        validarFechaNoFutura(request.fechaCompra());

        Proveedor proveedor = proveedorRepository
                .findByIdAndGranjaId(request.idProveedor(), idGranja)
                .filter(Proveedor::getActivo)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proveedor no válido o inactivo"));

        Granja granja = granjaRepository.findById(idGranja).orElseThrow();
        Usuario usuario = usuarioRepository.findById(idUsuario).orElseThrow();

        Instant fechaCompra = request.fechaCompra().atStartOfDay(ZoneOffset.UTC).toInstant();
        CompraCabecera cabecera = CompraCabecera.builder()
                .id(IdGenerator.newId())
                .granja(granja)
                .usuario(usuario)
                .proveedor(proveedor)
                .numeroFactura(request.numeroFactura().trim())
                .fechaCompra(fechaCompra)
                .totalFactura(CompraCalculo.redondear(request.totalFactura()))
                .observaciones(normalizar(request.observaciones()))
                .activo(true)
                .codigoProveedorSnapshot(proveedor.getCodigoProveedor())
                .nombreProveedorSnapshot(proveedor.getNombreProveedor())
                .estado(EstadoCompra.BORRADOR)
                .build();

        return CompraCompletaResponse.from(compraCabeceraRepository.save(cabecera));
    }

    @Transactional
    public CompraCompletaResponse actualizarCabecera(
            String idUsuario, String idGranja, String idCompra, CompraCabeceraRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        validarFechaNoFutura(request.fechaCompra());

        CompraCabecera cabecera = obtenerCabecera(idGranja, idCompra);
        Proveedor proveedor = proveedorRepository
                .findByIdAndGranjaId(request.idProveedor(), idGranja)
                .filter(Proveedor::getActivo)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proveedor no válido o inactivo"));

        double nuevoTotal = CompraCalculo.redondear(request.totalFactura());
        boolean totalCambiado =
                CompraCalculo.redondear(cabecera.getTotalFactura()) != nuevoTotal;
        boolean teniaLineas = !cabecera.getDetalles().isEmpty();
        boolean requiereAjusteDetalle = false;

        cabecera.setProveedor(proveedor);
        cabecera.setCodigoProveedorSnapshot(proveedor.getCodigoProveedor());
        cabecera.setNombreProveedorSnapshot(proveedor.getNombreProveedor());
        cabecera.setNumeroFactura(request.numeroFactura().trim());
        cabecera.setFechaCompra(request.fechaCompra().atStartOfDay(ZoneOffset.UTC).toInstant());
        cabecera.setTotalFactura(nuevoTotal);
        cabecera.setObservaciones(normalizar(request.observaciones()));

        if (totalCambiado && (teniaLineas || cabecera.getEstado() == EstadoCompra.REGISTRADA)) {
            cabecera.setEstado(EstadoCompra.BORRADOR);
            requiereAjusteDetalle = true;
        }

        CompraCabecera guardada = compraCabeceraRepository.save(cabecera);
        if (!requiereAjusteDetalle && guardada.getEstado() == EstadoCompra.REGISTRADA) {
            compraPrecioMateriaPrimaService.aplicarTrasActualizarCabecera(guardada);
        }

        return CompraCompletaResponse.from(guardada, requiereAjusteDetalle);
    }

    @Transactional
    public void eliminarCabecera(String idUsuario, String idGranja, String idCompra) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        CompraCabecera cabecera = obtenerCabecera(idGranja, idCompra);
        var idsMaterias = cabecera.getDetalles().stream()
                .map(linea -> linea.getMateriaPrima().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        cabecera.getDetalles().clear();
        cabecera.setActivo(false);
        cabecera.setFechaEliminacion(Instant.now());
        cabecera.setEliminadoPor(idUsuario);
        compraCabeceraRepository.saveAndFlush(cabecera);
        compraPrecioMateriaPrimaService.aplicarTrasEliminarCompra(idGranja, idCompra, idsMaterias);
    }

    @Transactional
    public CompraCompletaResponse guardarDetalle(
            String idUsuario, String idGranja, String idCompra, GuardarCompraDetalleRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        CompraCabecera cabecera = obtenerCabecera(idGranja, idCompra);

        List<CompraDetalle> nuevasLineas = construirLineasValidadas(idGranja, cabecera, request.lineas());

        double sumaSubtotales = CompraCalculo.redondear(
                nuevasLineas.stream().mapToDouble(CompraDetalle::getSubtotal).sum());
        if (!CompraCalculo.dentroDeTolerancia(cabecera.getTotalFactura(), sumaSubtotales)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "La suma de subtotales ("
                            + sumaSubtotales
                            + ") no coincide con el total de la factura ("
                            + cabecera.getTotalFactura()
                            + ")");
        }

        var idsMaterias = cabecera.getDetalles().stream()
                .map(linea -> linea.getMateriaPrima().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (CompraDetalle linea : nuevasLineas) {
            idsMaterias.add(linea.getMateriaPrima().getId());
        }

        cabecera.getDetalles().clear();
        cabecera.getDetalles().addAll(nuevasLineas);
        cabecera.setEstado(EstadoCompra.REGISTRADA);
        compraCabeceraRepository.saveAndFlush(cabecera);

        compraPrecioMateriaPrimaService.aplicarTrasGuardarDetalle(cabecera, nuevasLineas, idsMaterias);

        return CompraCompletaResponse.from(cabecera);
    }

    private List<CompraDetalle> construirLineasValidadas(
            String idGranja, CompraCabecera cabecera, List<CompraDetalleLineRequest> lineasRequest) {
        List<CompraDetalle> nuevasLineas = new ArrayList<>();
        Set<Long> materiasVistas = new HashSet<>();

        for (CompraDetalleLineRequest linea : lineasRequest) {
            MateriaPrima mp = materiaPrimaRepository
                    .findByIdAndGranjaId(linea.idMateriaPrima(), idGranja)
                    .filter(MateriaPrima::getActiva)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Materia prima no válida o inactiva: id=" + linea.idMateriaPrima()));

            if (!materiasVistas.add(linea.idMateriaPrima())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La materia prima "
                                + mp.getCodigoMateriaPrima()
                                + " está repetida en el detalle: cada materia prima debe figurar una sola vez");
            }

            double cantidad = CompraCalculo.redondear(linea.cantidadKg());
            double precio = CompraCalculo.redondear(linea.precioPorKilo());
            double subtotal = CompraCalculo.redondear(linea.subtotal());
            double subtotalEsperado = CompraCalculo.calcularSubtotal(cantidad, precio);

            if (!CompraCalculo.dentroDeTolerancia(subtotalEsperado, subtotal)) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Línea de "
                                + mp.getCodigoMateriaPrima()
                                + ": cantidad × precio ("
                                + subtotalEsperado
                                + ") no coincide con el subtotal ("
                                + subtotal
                                + ") fuera de la tolerancia permitida");
            }

            if (cantidad <= 0 || precio < 0 || subtotal < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Cantidad y montos deben ser positivos");
            }

            nuevasLineas.add(CompraDetalle.builder()
                    .id(IdGenerator.newId())
                    .compra(cabecera)
                    .materiaPrima(mp)
                    .cantidadComprada(cantidad)
                    .precioUnitario(precio)
                    .subtotal(subtotal)
                    .precioAnteriorMateriaPrima(CompraCalculo.redondear(mp.getPrecioPorKilo()))
                    .codigoMpSnapshot(mp.getCodigoMateriaPrima())
                    .nombreMpSnapshot(mp.getNombreMateriaPrima())
                    .build());
        }
        return nuevasLineas;
    }

    private CompraCabecera obtenerCabecera(String idGranja, String idCompra) {
        return compraCabeceraRepository
                .findByIdAndGranjaIdAndActivoTrue(idCompra, idGranja)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada"));
    }

    private static void validarFechaNoFutura(LocalDate fecha) {
        if (fecha.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "La fecha de compra no puede ser posterior a hoy");
        }
    }

    private static String normalizar(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
