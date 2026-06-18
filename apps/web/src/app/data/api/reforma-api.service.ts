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
} from '../models/compra.model';
import {
  FormulaCabeceraRequest,
  FormulaCompleta,
  FormulaResumen,
  GuardarFormulaDetalleRequest,
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
} from '../models/fabricacion.model';
import { CsvImportResult } from '../models/csv.model';
import { FiltrosAuditoria, PaginaAuditoria } from '../models/auditoria.model';

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

  // === Fabricaciones ===
  getFabricaciones(idGranja: string): Observable<FabricacionResumen[]> {
    return this.http.get<FabricacionResumen[]>(`${this.base}/api/fabricaciones/${idGranja}`);
  }

  getFabricacion(idGranja: string, idFabricacion: string): Observable<FabricacionCompleta> {
    return this.http.get<FabricacionCompleta>(
      `${this.base}/api/fabricaciones/${idGranja}/${idFabricacion}`,
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
