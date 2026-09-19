import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { Page } from '../models/page.model';
import {
  AsignacionOficioRequest,
  DiaCalendario,
  EstadoSolicitud,
  ResumenVacaciones,
  SolicitudVacaciones,
  SolicitudVacacionesAdmin,
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

  obtenerCalendarioComoAdmin(empleadoId: number, anio: number): Observable<DiaCalendario[]> {
    const params = new HttpParams().set('empleadoId', empleadoId).set('anio', anio);
    return this.http.get<DiaCalendario[]>(`${BASE_URL}/admin/calendario`, { params });
  }

  obtenerResumenComoAdmin(empleadoId: number, anio: number): Observable<ResumenVacaciones> {
    const params = new HttpParams().set('empleadoId', empleadoId).set('anio', anio);
    return this.http.get<ResumenVacaciones>(`${BASE_URL}/admin/resumen`, { params });
  }

  listarSolicitudesComoAdmin(
    empleadoId: number,
    estado: EstadoSolicitud | null,
    page: number,
    size: number,
  ): Observable<Page<SolicitudVacaciones>> {
    let params = new HttpParams().set('empleadoId', empleadoId).set('page', page).set('size', size);
    if (estado !== null) {
      params = params.set('estado', estado);
    }
    return this.http.get<Page<SolicitudVacaciones>>(`${BASE_URL}/admin/solicitudes`, { params });
  }

  listarTodasPendientes(page: number, size: number): Observable<Page<SolicitudVacacionesAdmin>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<SolicitudVacacionesAdmin>>(`${BASE_URL}/admin/solicitudes/pendientes`, { params });
  }

  aprobar(id: number): Observable<SolicitudVacaciones> {
    return this.http.patch<SolicitudVacaciones>(`${BASE_URL}/admin/solicitudes/${id}/aprobar`, {});
  }

  rechazar(id: number, motivo: string): Observable<SolicitudVacaciones> {
    return this.http.patch<SolicitudVacaciones>(`${BASE_URL}/admin/solicitudes/${id}/rechazar`, { motivo });
  }

  asignarDeOficio(request: AsignacionOficioRequest): Observable<SolicitudVacaciones> {
    return this.http.post<SolicitudVacaciones>(`${BASE_URL}/admin/solicitudes`, request);
  }
}
