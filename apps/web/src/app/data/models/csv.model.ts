/**
 * Resultado de un import CSV devuelto por el backend (RF-MP-004 / RF-ANI-003).
 * Refleja exactamente el record `CsvImportResult` en api-domain.
 */
export interface CsvImportResult {
  filasOk: number;
  filasError: number;
  errores: CsvImportError[];
}

export interface CsvImportError {
  linea: number;
  codigo: string | null;
  mensaje: string;
}

/** Dispara la descarga del blob como un archivo con el nombre indicado. */
export function descargarBlobComoArchivo(blob: Blob, nombreArchivo: string): void {
  const url = URL.createObjectURL(blob);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombreArchivo;
  document.body.appendChild(enlace);
  enlace.click();
  document.body.removeChild(enlace);
  URL.revokeObjectURL(url);
}
