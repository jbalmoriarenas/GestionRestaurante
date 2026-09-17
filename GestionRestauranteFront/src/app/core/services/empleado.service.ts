import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { Empleado, EmpleadoCreateRequest, EmpleadoUpdateRequest } from '../models/empleado.model';
import { Page } from '../models/page.model';

const BASE_URL = `${API_BASE_URL}/empleados`;

@Injectable({ providedIn: 'root' })
export class EmpleadoService {
  constructor(private readonly http: HttpClient) {}

  listar(page: number, size: number, activo?: boolean): Observable<Page<Empleado>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (activo !== undefined) {
      params = params.set('activo', activo);
    }
    return this.http.get<Page<Empleado>>(BASE_URL, { params });
  }

  obtener(id: number): Observable<Empleado> {
    return this.http.get<Empleado>(`${BASE_URL}/${id}`);
  }

  crear(request: EmpleadoCreateRequest): Observable<Empleado> {
    return this.http.post<Empleado>(BASE_URL, request);
  }

  actualizar(id: number, request: EmpleadoUpdateRequest): Observable<Empleado> {
    return this.http.put<Empleado>(`${BASE_URL}/${id}`, request);
  }

  darDeBaja(id: number): Observable<void> {
    return this.http.patch<void>(`${BASE_URL}/${id}/baja`, {});
  }

  resetearPassword(id: number, password: string): Observable<void> {
    return this.http.patch<void>(`${BASE_URL}/${id}/password`, { password });
  }
}
