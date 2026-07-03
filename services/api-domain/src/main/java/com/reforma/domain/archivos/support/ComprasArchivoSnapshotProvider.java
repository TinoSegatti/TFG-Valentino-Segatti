package com.reforma.domain.archivos.support;

import com.reforma.domain.archivos.domain.TipoModuloArchivo;
import com.reforma.domain.compras.dto.CompraCompletaResponse;
import com.reforma.domain.compras.repository.CompraCabeceraRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Archiva las compras activas con su detalle completo (mismo contenido que el listado del
 * módulo más las líneas de cada compra). Incluye BORRADOR y REGISTRADA con su estado: el
 * archivo refleja lo que la pantalla mostraba en ese momento.
 */
@Component
@RequiredArgsConstructor
class ComprasArchivoSnapshotProvider implements ArchivoSnapshotProvider {

    private final CompraCabeceraRepository compraCabeceraRepository;

    @Override
    public TipoModuloArchivo tipo() {
        return TipoModuloArchivo.COMPRAS;
    }

    @Override
    public ArchivoSnapshot capturar(String idTenant, String idGranja) {
        List<CompraCompletaResponse> compras = compraCabeceraRepository
                .findByGranjaIdAndActivoTrueOrderByFechaCompraDesc(idGranja)
                .stream()
                .map(CompraCompletaResponse::from)
                .toList();
        return new ArchivoSnapshot(compras, compras.size());
    }
}
