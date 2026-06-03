export interface MateriaPrima {
  /** PK autoincremental (BD). Distinto del código de negocio. */
  id: number;
  idGranja: string;
  codigoMateriaPrima: string;
  nombreMateriaPrima: string;
  precioPorKilo: number;
  activa: boolean;
  fechaCreacion: string;
  fechaUltimaActualizacion: string;
}

export interface MateriaPrimaRequest {
  codigoMateriaPrima: string;
  nombreMateriaPrima: string;
  precioPorKilo: number;
}
