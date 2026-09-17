import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { TipoUsuario } from '../models/empleado.model';

@Injectable({ providedIn: 'root' })
export class TipoUsuarioService {
  constructor(private readonly http: HttpClient) {}

  listar(): Observable<TipoUsuario[]> {
    return this.http.get<TipoUsuario[]>(`${API_BASE_URL}/tipos-usuarios`);
  }
}
