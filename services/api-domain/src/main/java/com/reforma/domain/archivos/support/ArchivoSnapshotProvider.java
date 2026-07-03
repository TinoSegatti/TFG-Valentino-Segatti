package com.reforma.domain.archivos.support;

import com.reforma.domain.archivos.domain.TipoModuloArchivo;

/**
 * Captura el estado actual de un módulo para archivarlo. Una implementación por
 * {@link TipoModuloArchivo}; sumar un módulo archivable = agregar una implementación.
 * El acceso a la granja ya fue validado por {@code ArchivoService}; la captura se ejecuta
 * dentro de su transacción (las colecciones lazy de las entidades son navegables).
 */
public interface ArchivoSnapshotProvider {

    TipoModuloArchivo tipo();

    ArchivoSnapshot capturar(String idTenant, String idGranja);
}
