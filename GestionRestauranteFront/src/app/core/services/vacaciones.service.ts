import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { Page } from '../models/page.model';
import {
  DiaCalendario,
  ResumenVacaciones,
  SolicitudVacaciones,
  SolicitudVacacionesRequest,
} from '../models/vacaciones.model';

const BASE_URL = `${API_BASE_URL}/vacaciones`;

@Injectable({ providedIn: 'root' })
export class VacacionesService {
  constructor(private readonly http: HttpClient) {}

  obtenerResumen(anio: number): Observable<ResumenVacaciones> {
    const params = new HttpParams().set('anio', anio);
    return this.http.get<ResumenVacaciones>(`${BASE_URL}/resumen`, { params });
  }

  obtenerCalendario(anio: number): Observable<DiaCalendario[]> {
    const params = new HttpParams().set('anio', anio);
    return this.http.get<DiaCalendario[]>(`${BASE_URL}/calendario`, { params });
  }

  solicitar(dto: SolicitudVacacionesRequest): Observable<SolicitudVacaciones> {
    return this.http.post<SolicitudVacaciones>(`${BASE_URL}/solicitudes`, dto);
  }

  cancelar(id: number): Observable<void> {
    return this.http.delete<void>(`${BASE_URL}/solicitudes/${id}`);
  }

  listarMisSolicitudes(page: number, size: number): Observable<Page<SolicitudVacaciones>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<SolicitudVacaciones>>(`${BASE_URL}/solicitudes`, { params });
  }
}
