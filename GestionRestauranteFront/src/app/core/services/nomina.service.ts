import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { EstadoNomina, NominaAccionResponse, NominaListado, ProcesamientoZip } from '../models/nomina.model';
import { Page } from '../models/page.model';

const BASE_URL = `${API_BASE_URL}/nominas`;

@Injectable({ providedIn: 'root' })
export class NominaService {
  constructor(private readonly http: HttpClient) {}

  listar(
    empleadoId: number | null,
    periodoMes: number,
    periodoAnio: number,
    estado: EstadoNomina | null,
    page: number,
    size: number,
  ): Observable<Page<NominaListado>> {
    let params = new HttpParams().set('periodoMes', periodoMes).set('periodoAnio', periodoAnio).set('page', page).set('size', size);
    if (empleadoId !== null) {
      params = params.set('empleadoId', empleadoId);
    }
    if (estado !== null) {
      params = params.set('estado', estado);
    }
    return this.http.get<Page<NominaListado>>(BASE_URL, { params });
  }

  subir(empleadoId: number, periodoMes: number, periodoAnio: number, archivo: File): Observable<NominaAccionResponse> {
    const formData = new FormData();
    formData.set('empleadoId', String(empleadoId));
    formData.set('periodoMes', String(periodoMes));
    formData.set('periodoAnio', String(periodoAnio));
    formData.set('archivo', archivo);
    return this.http.post<NominaAccionResponse>(BASE_URL, formData);
  }

  descargar(nominaId: number): Observable<Blob> {
    return this.http.get(`${BASE_URL}/${nominaId}/descargar`, { responseType: 'blob' });
  }

  subirZip(archivo: File): Observable<ProcesamientoZip> {
    const formData = new FormData();
    formData.set('archivo', archivo);
    return this.http.post<ProcesamientoZip>(`${BASE_URL}/zip`, formData);
  }

  asignarPendiente(
    tempId: string,
    empleadoId: number,
    periodoMes: number,
    periodoAnio: number,
  ): Observable<NominaAccionResponse> {
    return this.http.post<NominaAccionResponse>(`${BASE_URL}/pendientes/${tempId}/asignar`, {
      empleadoId,
      periodoMes,
      periodoAnio,
    });
  }

  enviar(nominaId: number): Observable<NominaAccionResponse> {
    return this.http.post<NominaAccionResponse>(`${BASE_URL}/${nominaId}/enviar`, {});
  }
}
