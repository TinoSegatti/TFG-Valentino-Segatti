import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse } from '../models/usuario.model';
import { Granja } from '../models/granja.model';
import { MateriaPrima, MateriaPrimaRequest } from '../models/materia-prima.model';
import { Proveedor, ProveedorRequest } from '../models/proveedor.model';
import { Animal, AnimalRequest } from '../models/animal.model';

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
}
