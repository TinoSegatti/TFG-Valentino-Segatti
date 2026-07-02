/** Modelos del módulo IA — predicción de agotamiento de stock (RF-IA-PRED). */

export type NivelAlertaStock =
  | 'SIN_DATOS'
  | 'SIN_RIESGO'
  | 'NORMAL'
  | 'ATENCION'
  | 'ALERTA'
  | 'CRITICO';

export type TendenciaStock = 'CRECIENTE' | 'DECRECIENTE' | 'ESTABLE';

/** Resumen de la predicción de una MP (alimenta el indicador de riesgo de la tabla). */
export interface PrediccionStock {
  idMateriaPrima: number;
  codigoMateriaPrima: string;
  nombreMateriaPrima: string;
  nivelAlerta: NivelAlertaStock;
  tendencia: TendenciaStock;
  stockActual: number;
  diasRestantes: number | null;
  fechaAgotamiento: string | null; // ISO date
  netoPromedio: number;
  consumoPromedio: number;
  ingresoPromedio: number;
  nMeses: number;
  modeloUsado: string | null;
}

export interface PuntoSerieStock {
  mes: string; // "YYYY-MM"
  existencias: number;
}

/** Predicción de una MP con las series para el gráfico del popup. */
export interface PrediccionStockDetalle {
  resumen: PrediccionStock;
  serieHistorica: PuntoSerieStock[];
  serieProyeccion: PuntoSerieStock[];
}

/** Etiqueta legible del nivel de alerta. */
export function nivelAlertaLabel(n: NivelAlertaStock): string {
  switch (n) {
    case 'CRITICO':
      return 'Crítico';
    case 'ALERTA':
      return 'Alerta';
    case 'ATENCION':
      return 'Atención';
    case 'NORMAL':
      return 'Normal';
    case 'SIN_RIESGO':
      return 'Sin riesgo';
    default:
      return 'Sin datos';
  }
}

/** Color del badge/indicador de riesgo según el nivel. */
export function nivelAlertaColor(n: NivelAlertaStock): string {
  switch (n) {
    case 'CRITICO':
      return '#f87171';
    case 'ALERTA':
      return '#fb923c';
    case 'ATENCION':
      return '#fbbf24';
    case 'NORMAL':
      return '#34d399';
    case 'SIN_RIESGO':
      return '#06b6d4';
    default:
      return '#6b7280';
  }
}

/** RD-03: la predicción de agotamiento es exclusiva de los planes BUSINESS/ENTERPRISE. */
export function planPermitePrediccion(plan: string | undefined | null): boolean {
  return plan === 'BUSINESS' || plan === 'ENTERPRISE';
}
