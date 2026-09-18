import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../api-base-url';
import { VacacionesService } from './vacaciones.service';

describe('VacacionesService', () => {
  let service: VacacionesService;
  let httpMock: HttpTestingController;

  const BASE_URL = `${API_BASE_URL}/vacaciones`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(VacacionesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('obtenerResumen hace GET a /vacaciones/resumen con el anio como query param', () => {
    service.obtenerResumen(2026).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/resumen?anio=2026`);
    expect(req.request.method).toBe('GET');
    req.flush({ total: 23, aprobados: 0, pendientes: 0, disponibles: 23 });
  });

  it('obtenerCalendario hace GET a /vacaciones/calendario con el anio como query param', () => {
    service.obtenerCalendario(2026).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/calendario?anio=2026`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('solicitar hace POST a /vacaciones/solicitudes con las fechas en el body', () => {
    service.solicitar({ fechaInicio: '2026-01-05', fechaFin: '2026-01-09' }).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/solicitudes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ fechaInicio: '2026-01-05', fechaFin: '2026-01-09' });
    req.flush({ id: 1 });
  });

  it('cancelar hace DELETE a /vacaciones/solicitudes/{id}', () => {
    service.cancelar(7).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/solicitudes/7`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('listarMisSolicitudes hace GET a /vacaciones/solicitudes con page y size como query params', () => {
    service.listarMisSolicitudes(0, 20).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/solicitudes?page=0&size=20`);
    expect(req.request.method).toBe('GET');
    req.flush({ content: [] });
  });
});
