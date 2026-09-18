import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { ConfiguracionUbicacion, ConfiguracionUbicacionRequest } from '../models/configuracion-ubicacion.model';

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
}
