import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import {
  Fichaje,
  FichajeActual,
  FichajeCorreccionRequest,
  FichajeResumenDia,
  FichajeResumenHoy,
  FichajeResumenMes,
  FichajeResumenSemana,
} from '../models/fichaje.model';
import { Page } from '../models/page.model';

export interface FiltrosFichajesAdmin {
  empleadoId: number | null;
  desde: string;
  hasta: string;
}

const BASE_URL = `${API_BASE_URL}/fichajes`;

@Injectable({ providedIn: 'root' })
export class FichajeService {
  constructor(private readonly http: HttpClient) {}

  obtenerActual(): Observable<FichajeActual> {
    return this.http.get<FichajeActual>(`${BASE_URL}/actual`);
  }

  iniciar(lat: number, lng: number): Observable<Fichaje> {
    return this.http.post<Fichaje>(`${BASE_URL}/entrada`, { lat, lng });
  }

  finalizar(): Observable<Fichaje> {
    return this.http.post<Fichaje>(`${BASE_URL}/salida`, {});
  }

  listarMios(page: number, size: number): Observable<Page<Fichaje>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Fichaje>>(`${BASE_URL}/mios`, { params });
  }

  obtenerResumenHoy(): Observable<FichajeResumenHoy> {
    return this.http.get<FichajeResumenHoy>(`${BASE_URL}/mios/hoy`);
  }

  listarComoAdmin(filtros: FiltrosFichajesAdmin, page: number, size: number): Observable<Page<Fichaje>> {
    let params = new HttpParams()
      .set('desde', filtros.desde)
      .set('hasta', filtros.hasta)
      .set('page', page)
      .set('size', size);
    if (filtros.empleadoId !== null) {
      params = params.set('empleadoId', filtros.empleadoId);
    }
    return this.http.get<Page<Fichaje>>(`${BASE_URL}/admin`, { params });
  }

  obtenerResumenDiario(empleadoId: number, desde: string, hasta: string): Observable<FichajeResumenDia[]> {
    const params = new HttpParams().set('empleadoId', empleadoId).set('desde', desde).set('hasta', hasta);
    return this.http.get<FichajeResumenDia[]>(`${BASE_URL}/admin/resumen/diario`, { params });
  }

  obtenerResumenSemanal(empleadoId: number, desde: string, hasta: string): Observable<FichajeResumenSemana[]> {
    const params = new HttpParams().set('empleadoId', empleadoId).set('desde', desde).set('hasta', hasta);
    return this.http.get<FichajeResumenSemana[]>(`${BASE_URL}/admin/resumen/semanal`, { params });
  }

  obtenerResumenMensual(empleadoId: number, anio: number): Observable<FichajeResumenMes[]> {
    const params = new HttpParams().set('empleadoId', empleadoId).set('anio', anio);
    return this.http.get<FichajeResumenMes[]>(`${BASE_URL}/admin/resumen/mensual`, { params });
  }

  corregir(id: number, dto: FichajeCorreccionRequest): Observable<Fichaje> {
    return this.http.put<Fichaje>(`${BASE_URL}/admin/${id}`, dto);
  }
}
