import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { Proveedor, ProveedorCreate, TipoProveedor } from '../models/proveedor.model';

const BASE_URL = `${API_BASE_URL}/proveedores`;

@Injectable({ providedIn: 'root' })
export class ProveedorService {
  constructor(private readonly http: HttpClient) {}

  listar(tipo: TipoProveedor | null): Observable<Proveedor[]> {
    let params = new HttpParams();
    if (tipo !== null) {
      params = params.set('tipo', tipo);
    }
    return this.http.get<Proveedor[]>(BASE_URL, { params });
  }

  crear(dto: ProveedorCreate): Observable<Proveedor> {
    return this.http.post<Proveedor>(BASE_URL, dto);
  }
}
