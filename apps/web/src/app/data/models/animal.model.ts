export interface Animal {
  id: number;
  idGranja: string;
  codigoAnimal: string;
  descripcionAnimal: string;
  categoriaAnimal: string | null;
  observaciones: string | null;
  activo: boolean;
  fechaCreacion: string;
  fechaUltimaActualizacion: string;
}

export interface AnimalRequest {
  codigoAnimal: string;
  descripcionAnimal: string;
  categoriaAnimal?: string | null;
  observaciones?: string | null;
}
