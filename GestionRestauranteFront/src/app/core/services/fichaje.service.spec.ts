import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../api-base-url';
import { FichajeService } from './fichaje.service';

describe('FichajeService', () => {
  let service: FichajeService;
  let httpMock: HttpTestingController;

  const BASE_URL = `${API_BASE_URL}/fichajes`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FichajeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('obtenerActual hace GET a /fichajes/actual', () => {
    service.obtenerActual().subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/actual`);
    expect(req.request.method).toBe('GET');
    req.flush({ enCurso: false, horaEntrada: null });
  });

  it('iniciar hace POST a /fichajes/entrada con lat/lng en el body', () => {
    service.iniciar(43.5, -5.6).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/entrada`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ lat: 43.5, lng: -5.6 });
    req.flush({ id: 1 });
  });

  it('finalizar hace POST a /fichajes/salida', () => {
    service.finalizar().subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/salida`);
    expect(req.request.method).toBe('POST');
    req.flush({ id: 1 });
  });

  it('listarMios hace GET a /fichajes/mios con page y size como query params', () => {
    service.listarMios(1, 20).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/mios?page=1&size=20`);
    expect(req.request.method).toBe('GET');
    req.flush({ content: [] });
  });

  it('obtenerResumenHoy hace GET a /fichajes/mios/hoy', () => {
    service.obtenerResumenHoy().subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/mios/hoy`);
    expect(req.request.method).toBe('GET');
    req.flush({ fichajes: [], horasImputadas: 0, horasTeoricas: 8 });
  });
});
