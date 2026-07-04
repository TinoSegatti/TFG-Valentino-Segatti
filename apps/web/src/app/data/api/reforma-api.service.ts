import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, EmpleadoResponse, Perfil, RolEmpleado } from '../models/usuario.model';
import { Granja } from '../models/granja.model';
import { MateriaPrima, MateriaPrimaRequest } from '../models/materia-prima.model';
import { Proveedor, ProveedorRequest } from '../models/proveedor.model';
import { Animal, AnimalRequest } from '../models/animal.model';
import {
  CompraCabeceraRequest,
  CompraCompleta,
  CompraResumen,
  GuardarCompraDetalleRequest,
  MateriaPrimaComprada,
} from '../models/compra.model';
import {
  FormulaCabeceraRequest,
  FormulaCompleta,
  FormulaResumen,
  GuardarFormulaDetalleRequest,
  MateriaPrimaUso,
} from '../models/formula.model';
import {
  ActualizarCantidadRealRequest,
  InicializarInventarioRequest,
  InventarioItem,
  InventarioListadoResponse,
} from '../models/inventario.model';
import {
  FabricacionCabeceraRequest,
  FabricacionCompleta,
  FabricacionResumen,
  GuardarFabricacionDetalleRequest,
  MateriaPrimaConsumo,
} from '../models/fabricacion.model';
import { CsvImportResult } from '../models/csv.model';
import { FiltrosAuditoria, PaginaAuditoria } from '../models/auditoria.model';
import {
  AnomaliaEvaluacion,
  AnomaliaHistorial,
  EvaluarAnomaliaRequest,
} from '../models/anomalia.model';
import { PrediccionStock, PrediccionStockDetalle } from '../models/prediccion.model';
import {
  ArchivoCrearRequest,
  ArchivoDetalle,
  ArchivoResumen,
  TipoModuloArchivo,
} from '../models/archivo.model';
import { InformeEstado, SeccionInformeCsv } from '../models/informe.model';
import { PreferenciasUi } from '../models/preferencias.model';

@Injectable({ providedIn: 'root' })
export class ReformaApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  // === Auth ===
  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/api/usuarios/login`, { email, password });
  }

  registrar(request: {
    email: string;
    password: string;
    nombreUsuario: string;
    apellidoUsuario: string;
  }): Observable<{ requiereVerificacion: boolean; emailEnviado: boolean }> {
    return this.http.post<{ requiereVerificacion: boolean; emailEnviado: boolean }>(
      `${this.base}/api/usuarios/registro`,
      request,
    );
  }

  verificarEmail(token: string): Observable<{ verificado: boolean }> {
    return this.http.post<{ verificado: boolean }>(
      `${this.base}/api/usuarios/verificar-email`,
      { token },
    );
  }

  reenviarVerificacion(email: string): Observable<{ enviado: boolean }> {
    return this.http.post<{ enviado: boolean }>(
      `${this.base}/api/usuarios/reenviar-verificacion`,
      { email },
    );
  }

  solicitarReset(email: string): Observable<{ enviado: boolean }> {
    return this.http.post<{ enviado: boolean }>(
      `${this.base}/api/usuarios/solicitar-reset`,
      { email },
    );
  }

  confirmarReset(token: string, nuevaPassword: string): Observable<{ restablecido: boolean }> {
    return this.http.post<{ restablecido: boolean }>(
      `${this.base}/api/usuarios/confirmar-reset`,
      { token, nuevaPassword },
    );
  }

  /** El empleado fija su contraseña con el token de invitación (público). */
  aceptarInvitacion(token: string, password: string): Observable<EmpleadoResponse> {
    return this.http.post<EmpleadoResponse>(
      `${this.base}/api/empleados/aceptar`,
      { token, password },
    );
  }

  /** Perfil del usuario autenticado (identidad, rol y permisos). */
  getPerfil(): Observable<Perfil> {
    return this.http.get<Perfil>(`${this.base}/api/usuarios/perfil`);
  }

  // === Preferencias de UI (Personalización: fondo + cortina) ===
  getPreferenciasUi(): Observable<PreferenciasUi> {
    return this.http.get<PreferenciasUi>(`${this.base}/api/usuarios/preferencias`);
  }

  putPreferenciasUi(prefs: PreferenciasUi): Observable<PreferenciasUi> {
    return this.http.put<PreferenciasUi>(`${this.base}/api/usuarios/preferencias`, prefs);
  }

  // === Empleados (gestión de equipo: dueño y jefe ADMIN) ===
  getEmpleados(): Observable<EmpleadoResponse[]> {
    return this.http.get<EmpleadoResponse[]>(`${this.base}/api/empleados`);
  }

  invitarEmpleado(request: {
    email: string;
    nombreUsuario: string;
    apellidoUsuario: string;
    rol: RolEmpleado;
  }): Observable<EmpleadoResponse> {
    return this.http.post<EmpleadoResponse>(`${this.base}/api/empleados`, request);
  }

  cambiarRolEmpleado(idEmpleado: string, rol: RolEmpleado): Observable<EmpleadoResponse> {
    return this.http.put<EmpleadoResponse>(`${this.base}/api/empleados/${idEmpleado}/rol`, { rol });
  }

  cambiarEstadoEmpleado(idEmpleado: string, activo: boolean): Observable<EmpleadoResponse> {
    return this.http.put<EmpleadoResponse>(`${this.base}/api/empleados/${idEmpleado}/activo`, {
      activo,
    });
  }

  // === Auditoría (consola de solo lectura: dueño y jefe ADMIN) ===
  getAuditoria(filtros: FiltrosAuditoria): Observable<PaginaAuditoria> {
    let params = new HttpParams()
      .set('pagina', String(filtros.pagina ?? 0))
      .set('tamano', String(filtros.tamano ?? 20));
    if (filtros.idGranja) params = params.set('idGranja', filtros.idGranja);
    if (filtros.idUsuario) params = params.set('idUsuario', filtros.idUsuario);
    if (filtros.accion) params = params.set('accion', filtros.accion);
    if (filtros.desde) params = params.set('desde', filtros.desde);
    if (filtros.hasta) params = params.set('hasta', filtros.hasta);
    return this.http.get<PaginaAuditoria>(`${this.base}/api/auditoria`, { params });
  }

  // === Granjas ===
  getGranjas(): Observable<Granja[]> {
    return this.http.get<Granja[]>(`${this.base}/api/granjas`);
  }

  crearGranja(nombreGranja: string, descripcion?: string): Observable<Granja> {
    return this.http.post<Granja>(`${this.base}/api/granjas`, { nombreGranja, descripcion });
  }

  getGranja(idGranja: string): Observable<Granja> {
    return this.http.get<Granja>(`${this.base}/api/granjas/${idGranja}`);
  }

  // === Materias primas ===
  getMateriasPrimas(idGranja: string): Observable<MateriaPrima[]> {
    return this.http.get<MateriaPrima[]>(`${this.base}/api/materias-primas/${idGranja}`);
  }

  crearMateriaPrima(idGranja: string, request: MateriaPrimaRequest): Observable<MateriaPrima> {
    return this.http.post<MateriaPrima>(`${this.base}/api/materias-primas/${idGranja}`, request);
  }

  actualizarMateriaPrima(
    idGranja: string,
    idMateriaPrima: number,
    request: MateriaPrimaRequest,
  ): Observable<MateriaPrima> {
    return this.http.put<MateriaPrima>(
      `${this.base}/api/materias-primas/${idGranja}/${idMateriaPrima}`,
      request,
    );
  }

  desactivarMateriaPrima(idGranja: string, idMateriaPrima: number): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/api/materias-primas/${idGranja}/${idMateriaPrima}`,
    );
  }

  exportarMateriasPrimasCsv(idGranja: string): Observable<Blob> {
    return this.http.get(`${this.base}/api/materias-primas/${idGranja}/csv`, {
      responseType: 'blob',
    });
  }

  importarMateriasPrimasCsv(idGranja: string, archivo: File): Observable<CsvImportResult> {
    const formData = new FormData();
    formData.append('archivo', archivo, archivo.name);
    return this.http.post<CsvImportResult>(
      `${this.base}/api/materias-primas/${idGranja}/csv`,
      formData,
    );
  }

  // === Proveedores ===
  getProveedores(idGranja: string, buscar?: string): Observable<Proveedor[]> {
    const params = buscar ? new HttpParams().set('buscar', buscar) : undefined;
    return this.http.get<Proveedor[]>(
      `${this.base}/api/proveedores/${idGranja}`,
      params ? { params } : {},
    );
  }

  crearProveedor(idGranja: string, request: ProveedorRequest): Observable<Proveedor> {
    return this.http.post<Proveedor>(`${this.base}/api/proveedores/${idGranja}`, request);
  }

  actualizarProveedor(
    idGranja: string,
    idProveedor: number,
    request: ProveedorRequest,
  ): Observable<Proveedor> {
    return this.http.put<Proveedor>(
      `${this.base}/api/proveedores/${idGranja}/${idProveedor}`,
      request,
    );
  }

  desactivarProveedor(idGranja: string, idProveedor: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/proveedores/${idGranja}/${idProveedor}`);
  }

  exportarProveedoresCsv(idGranja: string): Observable<Blob> {
    return this.http.get(`${this.base}/api/proveedores/${idGranja}/csv`, {
      responseType: 'blob',
    });
  }

  importarProveedoresCsv(idGranja: string, archivo: File): Observable<CsvImportResult> {
    const formData = new FormData();
    formData.append('archivo', archivo, archivo.name);
    return this.http.post<CsvImportResult>(
      `${this.base}/api/proveedores/${idGranja}/csv`,
      formData,
    );
  }

  // === Animales ===
  getAnimales(idGranja: string, buscar?: string): Observable<Animal[]> {
    const params = buscar ? new HttpParams().set('buscar', buscar) : undefined;
    return this.http.get<Animal[]>(
      `${this.base}/api/animales/${idGranja}`,
      params ? { params } : {},
    );
  }

  crearAnimal(idGranja: string, request: AnimalRequest): Observable<Animal> {
    return this.http.post<Animal>(`${this.base}/api/animales/${idGranja}`, request);
  }

  actualizarAnimal(
    idGranja: string,
    idAnimal: number,
    request: AnimalRequest,
  ): Observable<Animal> {
    return this.http.put<Animal>(
      `${this.base}/api/animales/${idGranja}/${idAnimal}`,
      request,
    );
  }

  desactivarAnimal(idGranja: string, idAnimal: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/animales/${idGranja}/${idAnimal}`);
  }

  exportarAnimalesCsv(idGranja: string): Observable<Blob> {
    return this.http.get(`${this.base}/api/animales/${idGranja}/csv`, {
      responseType: 'blob',
    });
  }

  importarAnimalesCsv(idGranja: string, archivo: File): Observable<CsvImportResult> {
    const formData = new FormData();
    formData.append('archivo', archivo, archivo.name);
    return this.http.post<CsvImportResult>(
      `${this.base}/api/animales/${idGranja}/csv`,
      formData,
    );
  }

  // === Compras ===
  getCompras(idGranja: string): Observable<CompraResumen[]> {
    return this.http.get<CompraResumen[]>(`${this.base}/api/compras/${idGranja}`);
  }

  /** Materias primas más compradas (kilos por MP) en las compras registradas de la granja. */
  getComprasMaterias(idGranja: string): Observable<MateriaPrimaComprada[]> {
    return this.http.get<MateriaPrimaComprada[]>(
      `${this.base}/api/compras/${idGranja}/materias-compradas`,
    );
  }

  getCompra(idGranja: string, idCompra: string): Observable<CompraCompleta> {
    return this.http.get<CompraCompleta>(`${this.base}/api/compras/${idGranja}/${idCompra}`);
  }

  crearCompraCabecera(idGranja: string, request: CompraCabeceraRequest): Observable<CompraCompleta> {
    return this.http.post<CompraCompleta>(`${this.base}/api/compras/${idGranja}`, request);
  }

  guardarCompraDetalle(
    idGranja: string,
    idCompra: string,
    request: GuardarCompraDetalleRequest,
  ): Observable<CompraCompleta> {
    return this.http.put<CompraCompleta>(
      `${this.base}/api/compras/${idGranja}/${idCompra}/detalle`,
      request,
    );
  }

  actualizarCompraCabecera(
    idGranja: string,
    idCompra: string,
    request: CompraCabeceraRequest,
  ): Observable<CompraCompleta> {
    return this.http.put<CompraCompleta>(
      `${this.base}/api/compras/${idGranja}/${idCompra}`,
      request,
    );
  }

  // --- IA: anomalías de precio (RF-IA-ANOM-*) ---

  evaluarAnomalia(idGranja: string, request: EvaluarAnomaliaRequest): Observable<AnomaliaEvaluacion> {
    return this.http.post<AnomaliaEvaluacion>(
      `${this.base}/api/ml/anomalias/${idGranja}/evaluar`,
      request,
    );
  }

  getAnomalias(idGranja: string, limite = 100): Observable<AnomaliaHistorial[]> {
    const params = new HttpParams().set('limite', limite);
    return this.http.get<AnomaliaHistorial[]>(`${this.base}/api/ml/anomalias/${idGranja}`, {
      params,
    });
  }

  getAnomaliasProveedor(
    idGranja: string,
    idProveedor: number,
    limite = 100,
  ): Observable<AnomaliaHistorial[]> {
    const params = new HttpParams().set('limite', limite);
    return this.http.get<AnomaliaHistorial[]>(
      `${this.base}/api/ml/anomalias/${idGranja}/proveedor/${idProveedor}`,
      { params },
    );
  }

  confirmarAnomalia(idGranja: string, idAnomalia: string, confirmo: boolean): Observable<void> {
    return this.http.put<void>(
      `${this.base}/api/ml/anomalias/${idGranja}/${idAnomalia}/confirmar`,
      { confirmo },
    );
  }

  // --- IA: predicción de agotamiento de stock (RF-IA-PRED) ---

  /** Resumen por MP para el indicador de riesgo de la tabla de inventario. */
  getPrediccionesInventario(idGranja: string): Observable<PrediccionStock[]> {
    return this.http.get<PrediccionStock[]>(`${this.base}/api/ml/prediccion/${idGranja}`);
  }

  /** Detalle con series (histórico + proyección) de una MP para el gráfico del popup. */
  getPrediccionStock(idGranja: string, idMateriaPrima: number): Observable<PrediccionStockDetalle> {
    return this.http.get<PrediccionStockDetalle>(
      `${this.base}/api/ml/prediccion/${idGranja}/materia-prima/${idMateriaPrima}`,
    );
  }

  eliminarCompra(idGranja: string, idCompra: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/compras/${idGranja}/${idCompra}`);
  }

  // === Formulas ===
  getFormulas(idGranja: string): Observable<FormulaResumen[]> {
    return this.http.get<FormulaResumen[]>(`${this.base}/api/formulas/${idGranja}`);
  }

  getFormula(idGranja: string, idFormula: string): Observable<FormulaCompleta> {
    return this.http.get<FormulaCompleta>(
      `${this.base}/api/formulas/${idGranja}/${idFormula}`,
    );
  }

  /** Materias primas más usadas en el total de fórmulas activas (kilos formulados por MP). */
  getFormulasUsoMaterias(idGranja: string): Observable<MateriaPrimaUso[]> {
    return this.http.get<MateriaPrimaUso[]>(
      `${this.base}/api/formulas/${idGranja}/uso-materias`,
    );
  }

  crearFormulaCabecera(
    idGranja: string,
    request: FormulaCabeceraRequest,
  ): Observable<FormulaCompleta> {
    return this.http.post<FormulaCompleta>(`${this.base}/api/formulas/${idGranja}`, request);
  }

  actualizarFormulaCabecera(
    idGranja: string,
    idFormula: string,
    request: FormulaCabeceraRequest,
  ): Observable<FormulaCompleta> {
    return this.http.put<FormulaCompleta>(
      `${this.base}/api/formulas/${idGranja}/${idFormula}`,
      request,
    );
  }

  guardarFormulaDetalle(
    idGranja: string,
    idFormula: string,
    request: GuardarFormulaDetalleRequest,
  ): Observable<FormulaCompleta> {
    return this.http.put<FormulaCompleta>(
      `${this.base}/api/formulas/${idGranja}/${idFormula}/detalle`,
      request,
    );
  }

  desactivarFormula(idGranja: string, idFormula: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/formulas/${idGranja}/${idFormula}`);
  }

  exportarFormulasCsv(idGranja: string): Observable<Blob> {
    return this.http.get(`${this.base}/api/formulas/${idGranja}/csv`, {
      responseType: 'blob',
    });
  }

  importarFormulasCsv(idGranja: string, archivo: File): Observable<CsvImportResult> {
    const formData = new FormData();
    formData.append('archivo', archivo, archivo.name);
    return this.http.post<CsvImportResult>(
      `${this.base}/api/formulas/${idGranja}/csv`,
      formData,
    );
  }

  // === Inventario ===
  getInventario(idGranja: string): Observable<InventarioListadoResponse> {
    return this.http.get<InventarioListadoResponse>(`${this.base}/api/inventario/${idGranja}`);
  }

  inicializarInventario(
    idGranja: string,
    request: InicializarInventarioRequest,
  ): Observable<InventarioItem[]> {
    return this.http.post<InventarioItem[]>(
      `${this.base}/api/inventario/${idGranja}/inicializar`,
      request,
    );
  }

  actualizarCantidadRealInventario(
    idGranja: string,
    idMateriaPrima: number,
    request: ActualizarCantidadRealRequest,
  ): Observable<InventarioItem> {
    return this.http.put<InventarioItem>(
      `${this.base}/api/inventario/${idGranja}/materia-prima/${idMateriaPrima}/cantidad-real`,
      request,
    );
  }

  recalcularInventario(idGranja: string): Observable<InventarioListadoResponse> {
    return this.http.post<InventarioListadoResponse>(
      `${this.base}/api/inventario/${idGranja}/recalcular`,
      {},
    );
  }

  // === Reportes (informe de estado, RF-REP) ===
  getInformeEstado(idGranja: string, desde?: string, hasta?: string): Observable<InformeEstado> {
    let params = new HttpParams();
    if (desde) params = params.set('desde', desde);
    if (hasta) params = params.set('hasta', hasta);
    return this.http.get<InformeEstado>(`${this.base}/api/reportes/${idGranja}/informe`, {
      params,
    });
  }

  exportarInformeCsv(
    idGranja: string,
    seccion: SeccionInformeCsv,
    desde?: string,
    hasta?: string,
  ): Observable<Blob> {
    let params = new HttpParams().set('seccion', seccion);
    if (desde) params = params.set('desde', desde);
    if (hasta) params = params.set('hasta', hasta);
    return this.http.get(`${this.base}/api/reportes/${idGranja}/informe/csv`, {
      params,
      responseType: 'blob',
    });
  }

  // === Archivos (snapshots inmutables de Inventario/Compras/Fórmulas) ===
  getArchivos(idGranja: string, tipo?: TipoModuloArchivo): Observable<ArchivoResumen[]> {
    let params = new HttpParams();
    if (tipo) params = params.set('tipo', tipo);
    return this.http.get<ArchivoResumen[]>(`${this.base}/api/archivos/${idGranja}`, { params });
  }

  getArchivoDetalle(idGranja: string, idArchivo: number): Observable<ArchivoDetalle> {
    return this.http.get<ArchivoDetalle>(`${this.base}/api/archivos/${idGranja}/${idArchivo}`);
  }

  crearArchivo(idGranja: string, request: ArchivoCrearRequest): Observable<ArchivoResumen> {
    return this.http.post<ArchivoResumen>(`${this.base}/api/archivos/${idGranja}`, request);
  }

  // === Fabricaciones ===
  getFabricaciones(idGranja: string): Observable<FabricacionResumen[]> {
    return this.http.get<FabricacionResumen[]>(`${this.base}/api/fabricaciones/${idGranja}`);
  }

  getFabricacion(idGranja: string, idFabricacion: string): Observable<FabricacionCompleta> {
    return this.http.get<FabricacionCompleta>(
      `${this.base}/api/fabricaciones/${idGranja}/${idFabricacion}`,
    );
  }

  /** Materias primas más consumidas en el total de fabricaciones registradas (kilos por MP). */
  getFabricacionesConsumoMaterias(idGranja: string): Observable<MateriaPrimaConsumo[]> {
    return this.http.get<MateriaPrimaConsumo[]>(
      `${this.base}/api/fabricaciones/${idGranja}/consumo-materias`,
    );
  }

  crearFabricacionCabecera(
    idGranja: string,
    request: FabricacionCabeceraRequest,
  ): Observable<FabricacionCompleta> {
    return this.http.post<FabricacionCompleta>(
      `${this.base}/api/fabricaciones/${idGranja}`,
      request,
    );
  }

  actualizarFabricacionCabecera(
    idGranja: string,
    idFabricacion: string,
    request: FabricacionCabeceraRequest,
  ): Observable<FabricacionCompleta> {
    return this.http.put<FabricacionCompleta>(
      `${this.base}/api/fabricaciones/${idGranja}/${idFabricacion}`,
      request,
    );
  }

  guardarFabricacionDetalle(
    idGranja: string,
    idFabricacion: string,
    request: GuardarFabricacionDetalleRequest,
  ): Observable<FabricacionCompleta> {
    return this.http.put<FabricacionCompleta>(
      `${this.base}/api/fabricaciones/${idGranja}/${idFabricacion}/detalle`,
      request,
    );
  }

  eliminarFabricacion(idGranja: string, idFabricacion: string): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/api/fabricaciones/${idGranja}/${idFabricacion}`,
    );
  }

  vaciarInventario(idGranja: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/inventario/${idGranja}`);
  }
}
