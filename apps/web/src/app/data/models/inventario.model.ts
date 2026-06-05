export interface InventarioItem {
  id: string | null;
  idMateriaPrima: number;
  codigoMateriaPrima: string;
  nombreMateriaPrima: string;
  precioPorKilo: number;
  cantidadAcumulada: number;
  cantidadSistema: number;
  cantidadReal: number;
  merma: number;
  precioAlmacen: number;
  valorStock: number;
  version: number;
  fechaUltimaActualizacion: string | null;
}

export interface InventarioListadoResponse {
  inicializado: boolean;
  items: InventarioItem[];
}

export interface InventarioInicialLineRequest {
  idMateriaPrima: number;
  cantidadInicial: number;
  precioInicial: number;
}

export interface InicializarInventarioRequest {
  lineas: InventarioInicialLineRequest[];
}

export interface ActualizarCantidadRealRequest {
  cantidadReal: number;
  observaciones?: string;
}
