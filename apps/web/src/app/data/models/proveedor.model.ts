export interface Proveedor {
  id: number;
  idGranja: string;
  codigoProveedor: string;
  nombreProveedor: string;
  telefono: string | null;
  email: string | null;
  cuit: string | null;
  direccion: string | null;
  localidad: string | null;
  notas: string | null;
  activo: boolean;
  fechaCreacion: string;
  fechaUltimaActualizacion: string;
}

export interface ProveedorRequest {
  codigoProveedor: string;
  nombreProveedor: string;
  telefono?: string | null;
  email?: string | null;
  cuit?: string | null;
  direccion?: string | null;
  localidad?: string | null;
  notas?: string | null;
}
