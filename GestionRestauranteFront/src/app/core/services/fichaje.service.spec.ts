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

  it('listarComoAdmin hace GET a /fichajes/admin con los filtros como query params', () => {
    service.listarComoAdmin({ empleadoId: 2, desde: '2026-01-01', hasta: '2026-01-31' }, 0, 20).subscribe();

    const req = httpMock.expectOne(
      `${BASE_URL}/admin?desde=2026-01-01&hasta=2026-01-31&page=0&size=20&empleadoId=2`,
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [] });
  });

  it('listarComoAdmin omite empleadoId del query si es null', () => {
    service.listarComoAdmin({ empleadoId: null, desde: '2026-01-01', hasta: '2026-01-31' }, 0, 20).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/admin?desde=2026-01-01&hasta=2026-01-31&page=0&size=20`);
    expect(req.request.method).toBe('GET');
    req.flush({ content: [] });
  });

  it('obtenerResumenDiario hace GET a /fichajes/admin/resumen/diario', () => {
    service.obtenerResumenDiario(2, '2026-01-01', '2026-01-31').subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/admin/resumen/diario?empleadoId=2&desde=2026-01-01&hasta=2026-01-31`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('obtenerResumenSemanal hace GET a /fichajes/admin/resumen/semanal', () => {
    service.obtenerResumenSemanal(2, '2026-01-01', '2026-01-31').subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/admin/resumen/semanal?empleadoId=2&desde=2026-01-01&hasta=2026-01-31`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('obtenerResumenMensual hace GET a /fichajes/admin/resumen/mensual', () => {
    service.obtenerResumenMensual(2, 2026).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/admin/resumen/mensual?empleadoId=2&anio=2026`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('corregir hace PUT a /fichajes/admin/{id}', () => {
    const dto = { horaEntrada: '2026-01-05T09:00:00', horaSalida: '2026-01-05T17:00:00', observaciones: null };
    service.corregir(10, dto).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/admin/10`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(dto);
    req.flush({ id: 10 });
  });
});
