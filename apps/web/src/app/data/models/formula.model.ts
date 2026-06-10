export const PESO_LOTE_FORMULA_KG = 1000;
export const DECIMALES_FORMULA = 3;
export const TOLERANCIA_KG_FORMULA = 0.001;

export interface FormulaResumen {
  id: string;
  codigoFormula: string;
  descripcionFormula: string;
  idAnimal: number;
  codigoAnimal: string;
  descripcionAnimal: string;
  costoTotalFormula: number;
  completa: boolean;
}

export interface FormulaCabeceraRequest {
  codigoFormula: string;
  descripcionFormula: string;
  idAnimal: number;
}

export interface FormulaDetalleLine {
  id?: string;
  idMateriaPrima: number;
  codigoMateriaPrima: string;
  nombreMateriaPrima: string;
  cantidadKg: number;
  porcentajeFormula: number;
  precioPorKilo: number;
  costoParcial: number;
}

export interface FormulaCompleta {
  id: string;
  codigoFormula: string;
  descripcionFormula: string;
  idAnimal: number;
  codigoAnimal: string;
  descripcionAnimal: string;
  pesoTotalFormula: number;
  costoTotalFormula: number;
  sumaKg: number;
  kilosFaltantes: number;
  completa: boolean;
  lineas: FormulaDetalleLine[];
}

export interface FormulaDetalleLineRequest {
  idMateriaPrima: number;
  cantidadKg: number;
}

export interface GuardarFormulaDetalleRequest {
  lineas: FormulaDetalleLineRequest[];
}

export interface LineaFormulaUi {
  idMateriaPrima: number | null;
  codigo: string;
  nombre: string;
  cantidadKg: number;
  precioPorKilo: number;
  costoParcial: number;
  advertenciaLinea?: string;
}

export function textoConfirmacionEliminarFormula(codigoFormula: string): string {
  return `ELIMINAR FORMULA ${codigoFormula}`;
}

export function redondearFormula(valor: number): number {
  const f = Math.pow(10, DECIMALES_FORMULA);
  return Math.round(valor * f) / f;
}

export function calcularCostoParcialFormula(cantidadKg: number, precioPorKilo: number): number {
  return redondearFormula(cantidadKg * precioPorKilo);
}

export function sumaKgCompleta(sumaKg: number): boolean {
  return Math.abs(sumaKg - PESO_LOTE_FORMULA_KG) <= TOLERANCIA_KG_FORMULA + 1e-9;
}

export function kilosFaltantes(sumaKg: number): number {
  return redondearFormula(Math.max(0, PESO_LOTE_FORMULA_KG - sumaKg));
}

export function lineaFormulaVacia(): LineaFormulaUi {
  return {
    idMateriaPrima: null,
    codigo: '',
    nombre: '',
    cantidadKg: 0,
    precioPorKilo: 0,
    costoParcial: 0,
  };
}

export function lineaDesdeFormula(linea: FormulaDetalleLine): LineaFormulaUi {
  return {
    idMateriaPrima: linea.idMateriaPrima,
    codigo: linea.codigoMateriaPrima,
    nombre: linea.nombreMateriaPrima,
    cantidadKg: linea.cantidadKg,
    precioPorKilo: linea.precioPorKilo,
    costoParcial: linea.costoParcial,
  };
}

export function recalcularLineaFormula(linea: LineaFormulaUi): LineaFormulaUi {
  if (!linea.idMateriaPrima || linea.cantidadKg <= 0) {
    return { ...linea, costoParcial: 0, advertenciaLinea: undefined };
  }
  const costo = calcularCostoParcialFormula(linea.cantidadKg, linea.precioPorKilo);
  return { ...linea, costoParcial: costo };
}
