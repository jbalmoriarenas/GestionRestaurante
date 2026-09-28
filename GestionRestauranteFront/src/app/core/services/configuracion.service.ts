import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { ConfiguracionEmpresa } from '../models/configuracion-empresa.model';
import { ConfiguracionUbicacion, ConfiguracionUbicacionRequest } from '../models/configuracion-ubicacion.model';
import { ConfiguracionVacaciones, ConfiguracionVacacionesRequest } from '../models/configuracion-vacaciones.model';

const BASE_URL = `${API_BASE_URL}/configuracion`;

@Injectable({ providedIn: 'root' })
export class ConfiguracionService {
  constructor(private readonly http: HttpClient) {}

  obtenerUbicacion(): Observable<ConfiguracionUbicacion> {
    return this.http.get<ConfiguracionUbicacion>(`${BASE_URL}/ubicacion`);
  }

  actualizarUbicacion(request: ConfiguracionUbicacionRequest): Observable<ConfiguracionUbicacion> {
    return this.http.put<ConfiguracionUbicacion>(`${BASE_URL}/ubicacion`, request);
  }

  obtenerVacaciones(): Observable<ConfiguracionVacaciones> {
    return this.http.get<ConfiguracionVacaciones>(`${BASE_URL}/vacaciones`);
  }

  actualizarVacaciones(request: ConfiguracionVacacionesRequest): Observable<ConfiguracionVacaciones> {
    return this.http.put<ConfiguracionVacaciones>(`${BASE_URL}/vacaciones`, request);
  }

  obtenerEmpresa(): Observable<ConfiguracionEmpresa> {
    return this.http.get<ConfiguracionEmpresa>(`${BASE_URL}/empresa`);
  }

  actualizarEmpresa(nombreEmpresa: string, logo: File | null): Observable<ConfiguracionEmpresa> {
    const formData = new FormData();
    formData.set('nombreEmpresa', nombreEmpresa);
    if (logo !== null) {
      formData.set('logo', logo);
    }
    return this.http.put<ConfiguracionEmpresa>(`${BASE_URL}/empresa`, formData);
  }

  /**
   * El logo se pide siempre como blob (nunca como <img src> directo): el endpoint exige un
   * usuario autenticado y el interceptor de JWT solo se aplica a peticiones HttpClient, no a
   * la carga nativa de un <img> (front/fase5-1-front.md).
   */
  obtenerLogo(): Observable<Blob> {
    return this.http.get(`${BASE_URL}/empresa/logo`, { responseType: 'blob' });
  }
}
