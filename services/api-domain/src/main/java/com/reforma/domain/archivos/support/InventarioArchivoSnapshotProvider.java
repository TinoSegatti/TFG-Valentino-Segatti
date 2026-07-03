package com.reforma.domain.archivos.support;

import com.reforma.domain.archivos.domain.TipoModuloArchivo;
import com.reforma.domain.inventario.dto.InventarioListadoResponse;
import com.reforma.domain.inventario.service.InventarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Archiva el listado de inventario tal como lo devuelve la pantalla del módulo. */
@Component
@RequiredArgsConstructor
class InventarioArchivoSnapshotProvider implements ArchivoSnapshotProvider {

    private final InventarioService inventarioService;

    @Override
    public TipoModuloArchivo tipo() {
        return TipoModuloArchivo.INVENTARIO;
    }

    @Override
    public ArchivoSnapshot capturar(String idTenant, String idGranja) {
        InventarioListadoResponse listado = inventarioService.listar(idTenant, idGranja);
        return new ArchivoSnapshot(listado, listado.items().size());
    }
}
