import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { Fichaje, FichajeActual, FichajeResumenHoy } from '../models/fichaje.model';
import { Page } from '../models/page.model';

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
}
