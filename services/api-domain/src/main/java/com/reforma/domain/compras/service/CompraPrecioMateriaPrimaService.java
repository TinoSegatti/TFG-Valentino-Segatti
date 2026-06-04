package com.reforma.domain.compras.service;

import com.reforma.domain.common.util.IdGenerator;
import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.entity.CompraCabecera;
import com.reforma.domain.compras.entity.CompraDetalle;
import com.reforma.domain.compras.repository.CompraDetalleRepository;
import com.reforma.domain.compras.support.CompraCalculo;
import com.reforma.domain.materiasprimas.entity.RegistroPrecio;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.materiasprimas.repository.RegistroPrecioRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sincroniza el precio de catálogo de materias primas según compras REGISTRADAS y persiste
 * historial en {@code t_registro_precio} para consumo futuro de modelos ML.
 *
 * <p>Regla: el precio vigente en catálogo es el {@code precio_unitario} de la línea de compra
 * con {@code fecha_compra} más reciente (más próxima al día actual). Editar una factura antigua
 * no altera el catálogo si existen facturas posteriores para la misma MP.
 */
@Service
@RequiredArgsConstructor
public class CompraPrecioMateriaPrimaService {

    static final String ORIGEN_COMPRA = "COMPRA";

    private final CompraDetalleRepository compraDetalleRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final RegistroPrecioRepository registroPrecioRepository;

    @Transactional
    public void aplicarTrasGuardarDetalle(CompraCabecera cabecera, List<CompraDetalle> lineas) {
        if (lineas.isEmpty()) {
            return;
        }
        registroPrecioRepository.deleteByCompra_Id(cabecera.getId());
        Instant ahora = Instant.now();
        for (CompraDetalle linea : lineas) {
            registrarObservacionCompra(cabecera, linea, ahora);
        }
        recalcularCatalogo(cabecera.getGranja().getId(), idsMaterias(lineas));
    }

    @Transactional
    public void aplicarTrasEliminarCompra(String idGranja, String idCompra, Collection<Long> idsMaterias) {
        registroPrecioRepository.deleteByCompra_Id(idCompra);
        if (!idsMaterias.isEmpty()) {
            recalcularCatalogo(idGranja, idsMaterias);
        }
    }

    /**
     * Tras cambiar la cabecera de una compra ya REGISTRADA (p. ej. fecha de factura), alinea
     * {@code fecha_referencia} del historial y recalcula el catálogo vigente.
     */
    @Transactional
    public void aplicarTrasActualizarCabecera(CompraCabecera cabecera) {
        if (cabecera.getEstado() != EstadoCompra.REGISTRADA || cabecera.getDetalles().isEmpty()) {
            return;
        }
        Instant nuevaFecha = cabecera.getFechaCompra();
        for (RegistroPrecio registro : registroPrecioRepository.findByCompra_Id(cabecera.getId())) {
            registro.setFechaReferencia(nuevaFecha);
        }
        recalcularCatalogo(cabecera.getGranja().getId(), idsMaterias(cabecera.getDetalles()));
    }

    void recalcularCatalogo(String idGranja, Collection<Long> idsMaterias) {
        for (Long idMateria : idsMaterias) {
            sincronizarPrecioCatalogo(idGranja, idMateria);
        }
    }

    private void sincronizarPrecioCatalogo(String idGranja, Long idMateriaPrima) {
        var masReciente = compraDetalleRepository
                .findMasRecientePorMateriaPrima(
                        idGranja, idMateriaPrima, EstadoCompra.REGISTRADA, PageRequest.of(0, 1))
                .stream()
                .findFirst();
        if (masReciente.isEmpty()) {
            return;
        }
        double precioVigente = CompraCalculo.redondear(masReciente.get().getPrecioUnitario());
        materiaPrimaRepository.findById(idMateriaPrima).ifPresent(mp -> {
            mp.setPrecioPorKilo(precioVigente);
            mp.setFechaUltimaActualizacion(Instant.now());
        });
    }

    private void registrarObservacionCompra(CompraCabecera cabecera, CompraDetalle linea, Instant fechaCambio) {
        double precioNuevo = CompraCalculo.redondear(linea.getPrecioUnitario());
        double precioAnterior = CompraCalculo.redondear(linea.getPrecioAnteriorMateriaPrima());
        registroPrecioRepository.save(RegistroPrecio.builder()
                .id(IdGenerator.newId())
                .materiaPrima(linea.getMateriaPrima())
                .compra(cabecera)
                .precioAnterior(precioAnterior)
                .precioNuevo(precioNuevo)
                .diferenciaPorcentual(calcularDiferenciaPorcentual(precioAnterior, precioNuevo))
                .fechaReferencia(cabecera.getFechaCompra())
                .fechaCambio(fechaCambio)
                .origen(ORIGEN_COMPRA)
                .motivo("Compra " + cabecera.getNumeroFactura())
                .build());
    }

    static double calcularDiferenciaPorcentual(double precioAnterior, double precioNuevo) {
        if (precioAnterior == 0.0) {
            return 0.0;
        }
        return CompraCalculo.redondear(((precioNuevo - precioAnterior) / precioAnterior) * 100.0);
    }

    private static Set<Long> idsMaterias(List<CompraDetalle> lineas) {
        Set<Long> ids = new LinkedHashSet<>();
        for (CompraDetalle linea : lineas) {
            ids.add(linea.getMateriaPrima().getId());
        }
        return ids;
    }
}
