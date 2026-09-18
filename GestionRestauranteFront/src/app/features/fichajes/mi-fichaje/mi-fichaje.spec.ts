import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { FichajeService } from '../../../core/services/fichaje.service';
import { GeolocationError, GeolocationService } from '../../../core/services/geolocation.service';
import { NotificationService } from '../../../core/services/notification.service';
import { MiFichaje } from './mi-fichaje';

describe('MiFichaje', () => {
  let fichajeServiceMock: {
    obtenerActual: ReturnType<typeof vi.fn>;
    iniciar: ReturnType<typeof vi.fn>;
    finalizar: ReturnType<typeof vi.fn>;
    listarMios: ReturnType<typeof vi.fn>;
    obtenerResumenHoy: ReturnType<typeof vi.fn>;
  };
  let geolocationServiceMock: { obtenerPosicionActual: ReturnType<typeof vi.fn> };
  let notificationServiceMock: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  const resumenVacio = { fichajes: [], horasImputadas: 0, horasTeoricas: 8 };

  function crearComponente(): MiFichaje {
    const fixture = TestBed.createComponent(MiFichaje);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    fichajeServiceMock = {
      obtenerActual: vi.fn().mockReturnValue(of({ enCurso: false, horaEntrada: null })),
      iniciar: vi.fn(),
      finalizar: vi.fn(),
      listarMios: vi.fn(),
      obtenerResumenHoy: vi.fn().mockReturnValue(of(resumenVacio)),
    };
    geolocationServiceMock = { obtenerPosicionActual: vi.fn() };
    notificationServiceMock = { success: vi.fn(), error: vi.fn() };

    TestBed.configureTestingModule({
      imports: [MiFichaje],
      providers: [
        { provide: FichajeService, useValue: fichajeServiceMock },
        { provide: GeolocationService, useValue: geolocationServiceMock },
        { provide: NotificationService, useValue: notificationServiceMock },
        provideTranslateService(),
      ],
    });
  });

  it('si no hay fichaje en curso al cargar, no arranca el cronometro', () => {
    const componente = crearComponente();

    expect(componente.enCurso()).toBe(false);
    expect(componente.segundosTranscurridos()).toBe(0);
  });

  it('si ya hay un fichaje en curso al cargar, reconstruye el cronometro desde la hora de entrada', () => {
    const haceCincoMinutos = new Date(Date.now() - 5 * 60 * 1000).toISOString();
    fichajeServiceMock.obtenerActual.mockReturnValue(of({ enCurso: true, horaEntrada: haceCincoMinutos }));

    const componente = crearComponente();

    expect(componente.enCurso()).toBe(true);
    expect(componente.segundosTranscurridos()).toBeGreaterThanOrEqual(299);
  });

  it('iniciar(): si la geolocalizacion falla, no llama al backend y notifica el error traducido', async () => {
    geolocationServiceMock.obtenerPosicionActual.mockImplementation(() =>
      Promise.reject(new GeolocationError('permisoDenegado')),
    );
    const componente = crearComponente();

    componente.iniciar();
    await Promise.resolve();
    await Promise.resolve();

    expect(fichajeServiceMock.iniciar).not.toHaveBeenCalled();
    expect(notificationServiceMock.error).toHaveBeenCalledWith('fichajes.miFichaje.errores.permisoDenegado');
    expect(componente.obteniendoUbicacion()).toBe(false);
  });

  it('iniciar(): con ubicacion y backend correctos, marca enCurso y arranca el cronometro desde cero', async () => {
    geolocationServiceMock.obtenerPosicionActual.mockReturnValue(Promise.resolve({ lat: 1, lng: 2 }));
    fichajeServiceMock.iniciar.mockReturnValue(of({ id: 1, horasCalculadas: null }));

    const componente = crearComponente();
    componente.iniciar();
    await Promise.resolve();
    await Promise.resolve();

    expect(fichajeServiceMock.iniciar).toHaveBeenCalledWith(1, 2);
    expect(componente.enCurso()).toBe(true);
    expect(componente.segundosTranscurridos()).toBe(0);
    expect(componente.obteniendoUbicacion()).toBe(false);
  });

  it('finalizar(): si el backend responde bien, apaga el cronometro y el estado de carga', () => {
    fichajeServiceMock.finalizar.mockReturnValue(of({ id: 1, horasCalculadas: 4 }));

    const componente = crearComponente();
    componente.enCurso.set(true);

    componente.finalizar();

    expect(componente.enCurso()).toBe(false);
    expect(componente.finalizandoTurno()).toBe(false);
    expect(notificationServiceMock.success).toHaveBeenCalled();
  });

  it('finalizar(): si el backend falla, apaga el estado de carga sin tocar enCurso (el turno sigue abierto de verdad)', () => {
    fichajeServiceMock.finalizar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));

    const componente = crearComponente();
    componente.enCurso.set(true);

    componente.finalizar();

    expect(componente.finalizandoTurno()).toBe(false);
    expect(componente.enCurso()).toBe(true);
  });
});
