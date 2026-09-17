import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { LoginRequest, LoginResponse } from '../models/auth.model';
import { CodigoVista, NivelAcceso } from '../models/permiso.model';

const STORAGE_KEY = 'gestion-restaurante.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly currentUser = signal<LoginResponse | null>(this.readStoredSession());
  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  constructor(private readonly http: HttpClient) {}

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${API_BASE_URL}/auth/login`, request).pipe(
      tap((response) => {
        this.currentUser.set(response);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(response));
      }),
    );
  }

  logout(): void {
    this.currentUser.set(null);
    localStorage.removeItem(STORAGE_KEY);
  }

  tieneAcceso(vista: CodigoVista): boolean {
    return this.currentUser()?.permisos.some((p) => p.vista === vista) ?? false;
  }

  puedeEditar(vista: CodigoVista): boolean {
    return this.nivelDeAcceso(vista) === 'EDICION';
  }

  nivelDeAcceso(vista: CodigoVista): NivelAcceso | null {
    return this.currentUser()?.permisos.find((p) => p.vista === vista)?.nivelAcceso ?? null;
  }

  private readStoredSession(): LoginResponse | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as LoginResponse;
    } catch {
      return null;
    }
  }
}
