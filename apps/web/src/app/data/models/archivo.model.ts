import { CompraCompleta } from './compra.model';
import { FormulaCompleta } from './formula.model';
import { InventarioListadoResponse } from './inventario.model';

/** Módulos que admiten crear un archivo (snapshot inmutable) de sus registros. */
export type TipoModuloArchivo = 'INVENTARIO' | 'COMPRAS' | 'FORMULAS';

export const TIPO_ARCHIVO_LABEL: Record<TipoModuloArchivo, string> = {
  INVENTARIO: 'Inventario',
  COMPRAS: 'Compras',
  FORMULAS: 'Fórmulas',
};

/** Prefijo del código sugerido al crear un archivo de cada módulo. */
export const TIPO_ARCHIVO_PREFIJO: Record<TipoModuloArchivo, string> = {
  INVENTARIO: 'INV',
  COMPRAS: 'CMP',
  FORMULAS: 'FOR',
};

export interface ArchivoCrearRequest {
  tipo: TipoModuloArchivo;
  codigoArchivo: string;
  descripcion?: string;
}

/** Cabecera de un archivo (sin el snapshot): lo que ve el explorador. */
export interface ArchivoResumen {
  id: number;
  tipo: TipoModuloArchivo;
  codigoArchivo: string;
  descripcion: string | null;
  fechaCreacion: string;
  creadoPorEmail: string;
  totalRegistros: number;
}

/**
 * Snapshot guardado según el tipo: el backend serializa los mismos DTOs de respuesta
 * que usa cada pantalla, por lo que se renderiza con los modelos existentes.
 */
export type ArchivoDatos = InventarioListadoResponse | CompraCompleta[] | FormulaCompleta[];

export interface ArchivoDetalle extends ArchivoResumen {
  datos: ArchivoDatos;
}
