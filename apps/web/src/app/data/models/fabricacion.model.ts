export type EstadoFabricacion = 'BORRADOR' | 'REGISTRADA';

export interface FabricacionResumen {
  id: string;
  codigoFabricacion: string;
  fechaFabricacion: string;
  descripcionFabricacion: string;
  estado: EstadoFabricacion;
  codigoFormula: string | null;
  veces: number;
  costoTotalFabricacion: number;
  sinExistencias: boolean;
}

export interface FabricacionCabeceraRequest {
  codigoFabricacion: string;
  fechaFabricacion: string;
  descripcionFabricacion: string;
  observaciones?: string;
}

export interface GuardarFabricacionDetalleRequest {
  idFormula: string;
  veces: number;
}

export interface FabricacionDetalleLine {
  id: string;
  idMateriaPrima: number;
  codigoMateriaPrima: string;
  nombreMateriaPrima: string;
  cantidadUsada: number;
  precioUnitario: number;
  costoParcial: number;
}

export interface FabricacionCompleta {
  id: string;
  codigoFabricacion: string;
  fechaFabricacion: string;
  descripcionFabricacion: string;
  observaciones: string | null;
  estado: EstadoFabricacion;
  idFormula: string | null;
  codigoFormula: string | null;
  descripcionFormula: string | null;
  veces: number;
  kilosProducidos: number;
  costoUnitarioFormula: number | null;
  costoTotalFabricacion: number;
  costoPorKilo: number;
  sinExistencias: boolean;
  lineas: FabricacionDetalleLine[];
}

export function hoyIsoFabricacion(): string {
  return new Date().toISOString().slice(0, 10);
}

export function textoConfirmacionEliminarFabricacion(codigo: string): string {
  return `ELIMINAR FABRICACION ${codigo}`;
}
