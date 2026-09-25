import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { Page } from '../models/page.model';
import { ConexionEstado, ResultadoSincronizacion, ResumenValoraciones, Valoracion } from '../models/valoracion.model';

const BASE_URL = `${API_BASE_URL}/valoraciones`;

@Injectable({ providedIn: 'root' })
export class ValoracionService {
  constructor(private readonly http: HttpClient) {}

  obtenerEstadoConexion(): Observable<ConexionEstado> {
    return this.http.get<ConexionEstado>(`${BASE_URL}/conexion`);
  }

  obtenerUrlConexion(): Observable<{ urlConsentimiento: string }> {
    return this.http.get<{ urlConsentimiento: string }>(`${BASE_URL}/conexion/url`);
  }

  desconectar(): Observable<void> {
    return this.http.delete<void>(`${BASE_URL}/conexion`);
  }

  sincronizar(): Observable<ResultadoSincronizacion> {
    return this.http.post<ResultadoSincronizacion>(`${BASE_URL}/sincronizar`, {});
  }

  listar(puntuacion: number | null, page: number, size: number): Observable<Page<Valoracion>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (puntuacion !== null) {
      params = params.set('puntuacion', puntuacion);
    }
    return this.http.get<Page<Valoracion>>(BASE_URL, { params });
  }

  obtenerResumen(): Observable<ResumenValoraciones> {
    return this.http.get<ResumenValoraciones>(`${BASE_URL}/resumen`);
  }
}
