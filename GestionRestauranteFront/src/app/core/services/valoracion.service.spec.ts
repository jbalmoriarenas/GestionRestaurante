import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../api-base-url';
import { ValoracionService } from './valoracion.service';

describe('ValoracionService', () => {
  let service: ValoracionService;
  let httpMock: HttpTestingController;

  const BASE_URL = `${API_BASE_URL}/valoraciones`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ValoracionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('obtenerEstadoConexion hace GET a /valoraciones/conexion', () => {
    service.obtenerEstadoConexion().subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/conexion`);
    expect(req.request.method).toBe('GET');
    req.flush({ conectado: false, nombreUbicacion: null, fechaConexion: null, fechaUltimaSincronizacion: null, ultimoError: null });
  });

  it('obtenerUrlConexion hace GET a /valoraciones/conexion/url', () => {
    service.obtenerUrlConexion().subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/conexion/url`);
    expect(req.request.method).toBe('GET');
    req.flush({ urlConsentimiento: 'https://accounts.google.com/consent' });
  });

  it('desconectar hace DELETE a /valoraciones/conexion', () => {
    service.desconectar().subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/conexion`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('sincronizar hace POST a /valoraciones/sincronizar', () => {
    service.sincronizar().subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/sincronizar`);
    expect(req.request.method).toBe('POST');
    req.flush({ nuevas: 0, actualizadas: 0, total: 0, error: null });
  });

  it('listar sin puntuacion hace GET con page y size, sin el filtro de puntuacion', () => {
    service.listar(null, 0, 10).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}?page=0&size=10`);
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
  });

  it('listar con puntuacion la incluye como query param', () => {
    service.listar(5, 1, 10).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}?page=1&size=10&puntuacion=5`);
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 1, size: 10 });
  });

  it('obtenerResumen hace GET a /valoraciones/resumen', () => {
    service.obtenerResumen().subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/resumen`);
    expect(req.request.method).toBe('GET');
    req.flush({ media: null, total: 0, distribucion: {} });
  });
});
