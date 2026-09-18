import { GeolocationError, GeolocationService } from './geolocation.service';

describe('GeolocationService', () => {
  let service: GeolocationService;
  const geolocationOriginal = navigator.geolocation;

  beforeEach(() => {
    service = new GeolocationService();
  });

  afterEach(() => {
    Object.defineProperty(navigator, 'geolocation', { value: geolocationOriginal, configurable: true });
  });

  function mockGeolocation(valor: unknown): void {
    Object.defineProperty(navigator, 'geolocation', { value: valor, configurable: true });
  }

  it('resuelve con las coordenadas cuando el navegador concede el permiso', async () => {
    mockGeolocation({
      getCurrentPosition: (success: PositionCallback) =>
        success({ coords: { latitude: 43.5, longitude: -5.6 } } as GeolocationPosition),
    });

    await expect(service.obtenerPosicionActual()).resolves.toEqual({ lat: 43.5, lng: -5.6 });
  });

  it('rechaza con tipo "permisoDenegado" cuando el navegador deniega el permiso', async () => {
    mockGeolocation({
      getCurrentPosition: (_success: PositionCallback, error: PositionErrorCallback) =>
        error({ code: 1, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError),
    });

    await expect(service.obtenerPosicionActual()).rejects.toBeInstanceOf(GeolocationError);
    await expect(service.obtenerPosicionActual()).rejects.toMatchObject({ tipo: 'permisoDenegado' });
  });

  it('rechaza con tipo "tiempoAgotado" cuando se agota el tiempo de espera', async () => {
    mockGeolocation({
      getCurrentPosition: (_success: PositionCallback, error: PositionErrorCallback) =>
        error({ code: 3, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError),
    });

    await expect(service.obtenerPosicionActual()).rejects.toMatchObject({ tipo: 'tiempoAgotado' });
  });

  it('rechaza con tipo "posicionNoDisponible" para cualquier otro codigo de error', async () => {
    mockGeolocation({
      getCurrentPosition: (_success: PositionCallback, error: PositionErrorCallback) =>
        error({ code: 2, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError),
    });

    await expect(service.obtenerPosicionActual()).rejects.toMatchObject({ tipo: 'posicionNoDisponible' });
  });

  it('rechaza con tipo "posicionNoDisponible" si el navegador no soporta geolocalizacion', async () => {
    mockGeolocation(undefined);

    await expect(service.obtenerPosicionActual()).rejects.toMatchObject({ tipo: 'posicionNoDisponible' });
  });
});
