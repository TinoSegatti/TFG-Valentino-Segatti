/** Modelos del módulo IA — detección de anomalías de precio (RF-IA-ANOM-*). */

export type ClasificacionAnomalia =
  | 'SIN_HISTORIAL'
  | 'NORMAL'
  | 'ATENCION'
  | 'ANOMALIA_ALTA'
  | 'SIN_EVALUAR';

export interface EvaluarAnomaliaRequest {
  idMateriaPrima: number;
  precio: number;
  /** Mes (1-12) de la compra; habilita la comparación estacional. Opcional. */
  mesReferencia?: number;
}

/** Resultado de evaluar un precio (preview en el formulario de compra). */
export interface AnomaliaEvaluacion {
  clasificacion: ClasificacionAnomalia;
  mensaje: string;
  requiereConfirmacion: boolean;
  zScore: number | null;
  promedioHistorico: number | null;
  minHistorico: number | null;
  maxHistorico: number | null;
  desviacionPct: number | null;
  nMuestras: number;
}

/** Item del historial de anomalías (reportes / ficha de proveedor — RF-IA-ANOM-006). */
export interface AnomaliaHistorial {
  id: string;
  idMateriaPrima: number;
  codigoMateriaPrima: string;
  nombreMateriaPrima: string;
  idCompra: string | null;
  numeroFactura: string | null;
  precioIngresado: number;
  precioPromedioHistorico: number | null;
  zScore: number | null;
  desviacionPct: number | null;
  clasificacion: ClasificacionAnomalia;
  usuarioConfirmo: boolean | null;
  detectadoEn: string;
}

/** ¿La clasificación amerita mostrar un aviso en pantalla? */
export function anomaliaEsRelevante(c: ClasificacionAnomalia): boolean {
  return c === 'ATENCION' || c === 'ANOMALIA_ALTA';
}
