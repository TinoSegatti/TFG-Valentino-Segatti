import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse } from '../models/usuario.model';
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

@Injectable({ providedIn: 'root' })
export class ReformaApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  // === Auth ===
  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/api/usuarios/login`, { email, password });
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

  vaciarInventario(idGranja: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/inventario/${idGranja}`);
  }
}
