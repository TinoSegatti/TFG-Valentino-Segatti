export interface MateriaPrima {
  id: string;
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
