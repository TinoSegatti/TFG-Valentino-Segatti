import { InventarioItem } from './inventario.model';

/** Informe de estado (RF-REP-001/003) — espejo de InformeEstadoResponse del backend. */

export interface ResumenGeneralInforme {
  compras: number;
  gastoTotal: number;
  valorStock: number;
  fabricaciones: number;
  kgProducidos: number;
  mermaTotal: number;
}

export interface ProveedorInforme {
  codigo: string;
  nombre: string;
  compras: number;
  monto: number;
  kg: number;
  materiaPrincipal: string | null;
}

export interface PuntoMensualInforme {
  mes: string; // "yyyy-MM"
  monto: number;
  kg: number;
}

export interface MateriaCompradaInforme {
  codigo: string;
  nombre: string;
  kg: number;
  monto: number;
  precioMin: number;
  precioMax: number;
  precioPromedio: number;
}

export interface FormulaConsumoInforme {
  codigo: string;
  descripcion: string;
  fabricaciones: number;
  kgProducidos: number;
  costoTotal: number;
}

export interface MateriaConsumidaInforme {
  codigo: string;
  nombre: string;
  kgConsumidos: number;
  costo: number;
}

export interface AnomaliaInforme {
  numeroFactura: string;
  fechaCompra: string;
  codigoMateriaPrima: string;
  nombreMateriaPrima: string;
  precioIngresado: number;
  precioPromedioHistorico: number | null;
  zScore: number | null;
  clasificacion: string | null;
  usuarioConfirmo: boolean | null;
}

export interface PrediccionInforme {
  codigoMateriaPrima: string;
  nombreMateriaPrima: string;
  diasRestantes: number | null;
  fechaAgotamiento: string | null;
  nivelAlerta: string | null;
}

export interface InformeEstado {
  idGranja: string;
  desde: string;
  hasta: string;
  resumen: ResumenGeneralInforme;
  proveedores: { proveedores: ProveedorInforme[] };
  inventario: { items: InventarioItem[]; valorTotal: number; mermaTotal: number };
  compras: { evolucionMensual: PuntoMensualInforme[]; materias: MateriaCompradaInforme[] };
  consumos: { formulas: FormulaConsumoInforme[]; materias: MateriaConsumidaInforme[] };
  ia: {
    anomalias: AnomaliaInforme[];
    prediccionesDisponibles: boolean;
    predicciones: PrediccionInforme[];
  };
}

/** Secciones exportables a CSV (RF-REP-002); coincide con SeccionCsv del backend. */
export type SeccionInformeCsv =
  | 'proveedores'
  | 'inventario'
  | 'compras'
  | 'consumos'
  | 'anomalias';
