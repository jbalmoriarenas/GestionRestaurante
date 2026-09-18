import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../api-base-url';
import { ConfiguracionService } from './configuracion.service';

describe('ConfiguracionService', () => {
  let service: ConfiguracionService;
  let httpMock: HttpTestingController;

  const BASE_URL = `${API_BASE_URL}/configuracion`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ConfiguracionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('obtenerUbicacion hace GET a /configuracion/ubicacion', () => {
    service.obtenerUbicacion().subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/ubicacion`);
    expect(req.request.method).toBe('GET');
    req.flush({ lat: 0, lng: 0, radioMetros: 150, updatedAt: '2026-01-01T00:00:00Z' });
  });

  it('actualizarUbicacion hace PUT a /configuracion/ubicacion con el body', () => {
    const dto = { lat: 43.5, lng: -5.6, radioMetros: 150 };
    service.actualizarUbicacion(dto).subscribe();

    const req = httpMock.expectOne(`${BASE_URL}/ubicacion`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(dto);
    req.flush({ ...dto, updatedAt: '2026-01-01T00:00:00Z' });
  });
});
