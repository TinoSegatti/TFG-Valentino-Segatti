import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse } from '../models/usuario.model';
import { Granja } from '../models/granja.model';
import { MateriaPrima, MateriaPrimaRequest } from '../models/materia-prima.model';

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
    idMateriaPrima: string,
    request: MateriaPrimaRequest,
  ): Observable<MateriaPrima> {
    return this.http.put<MateriaPrima>(
      `${this.base}/api/materias-primas/${idGranja}/${idMateriaPrima}`,
      request,
    );
  }

  desactivarMateriaPrima(idGranja: string, idMateriaPrima: string): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/api/materias-primas/${idGranja}/${idMateriaPrima}`,
    );
  }
}
