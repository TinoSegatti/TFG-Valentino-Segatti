export type EstadoCompra = 'BORRADOR' | 'REGISTRADA';

export interface CompraCabeceraRequest {
  idProveedor: number;
  numeroFactura: string;
  fechaCompra: string;
  totalFactura: number;
  observaciones?: string;
}

export interface CompraDetalleLineRequest {
  idMateriaPrima: number;
  cantidadKg: number;
  precioPorKilo: number;
  subtotal: number;
}

export interface GuardarCompraDetalleRequest {
  lineas: CompraDetalleLineRequest[];
}

export interface CompraDetalleLine {
  id?: string;
  idMateriaPrima: number;
  codigoMateriaPrima: string;
  nombreMateriaPrima: string;
  cantidadKg: number;
  precioPorKilo: number;
  subtotal: number;
  precioAnteriorMateriaPrima?: number;
  codigoMpSnapshot?: string;
  nombreMpSnapshot?: string;
}

export interface CompraResumen {
  id: string;
  numeroFactura: string;
  fechaCompra: string;
  totalFactura: number;
  idProveedor: number;
  codigoProveedor: string;
  nombreProveedor: string;
  estado: EstadoCompra;
  cantidadLineas: number;
  sumaSubtotales: number;
}

export interface CompraCompleta extends CompraResumen {
  observaciones?: string | null;
  lineas: CompraDetalleLine[];
  requiereAjusteDetalle?: boolean;
}

/** Línea editable en pantalla de detalle (UI). */
export interface LineaDetalleUi {
  idMateriaPrima: number | null;
  codigo: string;
  nombre: string;
  cantidadKg: number | null;
  precioPorKilo: number | null;
  subtotal: number | null;
  ultimoPrecioCatalogo: number | null;
  advertenciaLinea: string | null;
  ultimoCampoEditado: 'cantidad' | 'precio' | 'subtotal' | null;
}

export const COMPRA_DECIMALES = 3;
export const COMPRA_TOLERANCIA = 0.5;

export function redondearCompra(valor: number): number {
  const factor = 10 ** COMPRA_DECIMALES;
  return Math.round(valor * factor) / factor;
}

export function dentroToleranciaCompra(esperado: number, actual: number): boolean {
  return Math.abs(esperado - actual) <= COMPRA_TOLERANCIA + 1e-9;
}

export function calcularSubtotalCompra(cantidad: number, precio: number): number {
  return redondearCompra(cantidad * precio);
}

export function calcularCantidadCompra(subtotal: number, precio: number): number {
  if (!precio) return 0;
  return redondearCompra(subtotal / precio);
}

export function calcularPrecioCompra(subtotal: number, cantidad: number): number {
  if (!cantidad) return 0;
  return redondearCompra(subtotal / cantidad);
}

export function recalcularLineaDetalle(linea: LineaDetalleUi): void {
  const c = linea.cantidadKg;
  const p = linea.precioPorKilo;
  const s = linea.subtotal;

  switch (linea.ultimoCampoEditado) {
    case 'cantidad':
    case 'precio':
      if (c != null && p != null) {
        linea.subtotal = calcularSubtotalCompra(c, p);
      }
      break;
    case 'subtotal':
      if (s != null && p != null && p > 0) {
        linea.cantidadKg = calcularCantidadCompra(s, p);
      } else if (s != null && c != null && c > 0) {
        linea.precioPorKilo = calcularPrecioCompra(s, c);
      }
      break;
    default:
      break;
  }
  actualizarAdvertenciaLinea(linea);
}

function actualizarAdvertenciaLinea(linea: LineaDetalleUi): void {
  if (linea.cantidadKg == null || linea.precioPorKilo == null || linea.subtotal == null) {
    linea.advertenciaLinea = null;
    return;
  }
  const esperado = calcularSubtotalCompra(linea.cantidadKg, linea.precioPorKilo);
  if (!dentroToleranciaCompra(esperado, linea.subtotal)) {
    linea.advertenciaLinea = `Cantidad × precio (${esperado}) difiere del subtotal (${linea.subtotal}) fuera de tolerancia`;
  } else if (Math.abs(esperado - linea.subtotal) > 1e-9) {
    linea.advertenciaLinea = `Posible error de redondeo (± $${COMPRA_TOLERANCIA} permitido)`;
  } else {
    linea.advertenciaLinea = null;
  }
}

export function lineaDetalleVacia(): LineaDetalleUi {
  return {
    idMateriaPrima: null,
    codigo: '',
    nombre: '',
    cantidadKg: null,
    precioPorKilo: null,
    subtotal: null,
    ultimoPrecioCatalogo: null,
    advertenciaLinea: null,
    ultimoCampoEditado: null,
  };
}

export function textoConfirmacionEliminarFactura(codigoFactura: string): string {
  return `Deseo eliminar la factura de código: ${codigoFactura}`;
}

export function lineaDesdeCompra(linea: CompraDetalleLine): LineaDetalleUi {
  return {
    idMateriaPrima: linea.idMateriaPrima,
    codigo: linea.codigoMateriaPrima,
    nombre: linea.nombreMateriaPrima,
    cantidadKg: linea.cantidadKg,
    precioPorKilo: linea.precioPorKilo,
    subtotal: linea.subtotal,
    ultimoPrecioCatalogo: linea.precioAnteriorMateriaPrima ?? null,
    advertenciaLinea: null,
    ultimoCampoEditado: null,
  };
}

export function hoyIso(): string {
  return new Date().toISOString().slice(0, 10);
}
