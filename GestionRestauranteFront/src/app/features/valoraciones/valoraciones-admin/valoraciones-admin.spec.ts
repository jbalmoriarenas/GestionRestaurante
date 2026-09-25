import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { NotificationService } from '../../../core/services/notification.service';
import { ValoracionService } from '../../../core/services/valoracion.service';
import { ValoracionesAdmin } from './valoraciones-admin';

describe('ValoracionesAdmin', () => {
  let valoracionServiceMock: {
    obtenerEstadoConexion: ReturnType<typeof vi.fn>;
    obtenerUrlConexion: ReturnType<typeof vi.fn>;
    desconectar: ReturnType<typeof vi.fn>;
    sincronizar: ReturnType<typeof vi.fn>;
    listar: ReturnType<typeof vi.fn>;
    obtenerResumen: ReturnType<typeof vi.fn>;
  };
  let notificationServiceMock: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };
  let routerMock: { navigate: ReturnType<typeof vi.fn> };
  let activatedRouteMock: { snapshot: { queryParamMap: ReturnType<typeof convertToParamMap> } };

  const desconectado = {
    conectado: false,
    nombreUbicacion: null,
    fechaConexion: null,
    fechaUltimaSincronizacion: null,
    ultimoError: null,
  };
  const conectado = {
    conectado: true,
    nombreUbicacion: 'Sidreria La Zapica',
    fechaConexion: '2026-01-01T00:00:00',
    fechaUltimaSincronizacion: null,
    ultimoError: null,
  };
  const paginaVacia = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 };

  function crearComponente(): ValoracionesAdmin {
    const fixture = TestBed.createComponent(ValoracionesAdmin);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    valoracionServiceMock = {
      obtenerEstadoConexion: vi.fn().mockReturnValue(of(desconectado)),
      obtenerUrlConexion: vi.fn().mockReturnValue(of({ urlConsentimiento: 'https://accounts.google.com/consent' })),
      desconectar: vi.fn().mockReturnValue(of(undefined)),
      sincronizar: vi.fn().mockReturnValue(of({ nuevas: 0, actualizadas: 0, total: 0, error: null })),
      listar: vi.fn().mockReturnValue(of(paginaVacia)),
      obtenerResumen: vi.fn().mockReturnValue(of({ media: null, total: 0, distribucion: { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 } })),
    };
    notificationServiceMock = { success: vi.fn(), error: vi.fn() };
    routerMock = { navigate: vi.fn() };
    activatedRouteMock = { snapshot: { queryParamMap: convertToParamMap({}) } };

    TestBed.configureTestingModule({
      imports: [ValoracionesAdmin],
      providers: [
        { provide: ValoracionService, useValue: valoracionServiceMock },
        { provide: NotificationService, useValue: notificationServiceMock },
        { provide: Router, useValue: routerMock },
        { provide: ActivatedRoute, useValue: activatedRouteMock },
        provideTranslateService(),
      ],
    });
  });

  it('al cargar sin conexion, no pide ni el resumen ni la tabla', () => {
    crearComponente();

    expect(valoracionServiceMock.obtenerEstadoConexion).toHaveBeenCalled();
    expect(valoracionServiceMock.obtenerResumen).not.toHaveBeenCalled();
    expect(valoracionServiceMock.listar).not.toHaveBeenCalled();
  });

  it('al cargar con conexion activa, pide el resumen y la primera pagina de la tabla', () => {
    valoracionServiceMock.obtenerEstadoConexion.mockReturnValue(of(conectado));

    crearComponente();

    expect(valoracionServiceMock.obtenerResumen).toHaveBeenCalled();
    expect(valoracionServiceMock.listar).toHaveBeenCalledWith(null, 0, 10);
  });

  it('distribucionOrdenada(): ordena de 5 a 1 estrellas y completa a 0 las puntuaciones sin reseñas', () => {
    valoracionServiceMock.obtenerEstadoConexion.mockReturnValue(of(conectado));
    valoracionServiceMock.obtenerResumen.mockReturnValue(of({ media: 4.33, total: 3, distribucion: { 5: 2, 3: 1 } }));
    const componente = crearComponente();

    expect(componente.distribucionOrdenada()).toEqual([
      { puntuacion: 5, cantidad: 2 },
      { puntuacion: 4, cantidad: 0 },
      { puntuacion: 3, cantidad: 1 },
      { puntuacion: 2, cantidad: 0 },
      { puntuacion: 1, cantidad: 0 },
    ]);
  });

  it('conectar(): pide la URL de consentimiento a Google', () => {
    const componente = crearComponente();

    componente.conectar();

    expect(valoracionServiceMock.obtenerUrlConexion).toHaveBeenCalled();
  });

  it('abrirModalDesconectar()/cerrarModalDesconectar() gestionan el estado del modal', () => {
    const componente = crearComponente();

    componente.abrirModalDesconectar();
    expect(componente.mostrarModalDesconectar()).toBe(true);

    componente.cerrarModalDesconectar();
    expect(componente.mostrarModalDesconectar()).toBe(false);
  });

  it('confirmarDesconectar(): desconecta, notifica exito, cierra el modal y refresca el estado', () => {
    valoracionServiceMock.obtenerEstadoConexion.mockReturnValue(of(conectado));
    const componente = crearComponente();
    componente.abrirModalDesconectar();
    valoracionServiceMock.obtenerEstadoConexion.mockClear();

    componente.confirmarDesconectar();

    expect(valoracionServiceMock.desconectar).toHaveBeenCalled();
    expect(notificationServiceMock.success).toHaveBeenCalled();
    expect(componente.mostrarModalDesconectar()).toBe(false);
    expect(valoracionServiceMock.obtenerEstadoConexion).toHaveBeenCalled();
  });

  it('sincronizarAhora(): sin error del backend, notifica exito', () => {
    valoracionServiceMock.sincronizar.mockReturnValue(of({ nuevas: 2, actualizadas: 1, total: 3, error: null }));
    const componente = crearComponente();

    componente.sincronizarAhora();

    expect(notificationServiceMock.success).toHaveBeenCalled();
    expect(notificationServiceMock.error).not.toHaveBeenCalled();
  });

  it('sincronizarAhora(): con error del backend (p.ej. token revocado), notifica error en vez de exito', () => {
    valoracionServiceMock.sincronizar.mockReturnValue(of({ nuevas: 0, actualizadas: 0, total: 0, error: 'Google no responde' }));
    const componente = crearComponente();

    componente.sincronizarAhora();

    expect(notificationServiceMock.error).toHaveBeenCalled();
    expect(notificationServiceMock.success).not.toHaveBeenCalled();
  });

  it('al cargar con conectado=true en la URL (vuelta del callback OAuth), notifica exito y limpia los query params sin dejar entrada en el historial', () => {
    activatedRouteMock.snapshot.queryParamMap = convertToParamMap({ conectado: 'true' });

    crearComponente();

    expect(notificationServiceMock.success).toHaveBeenCalled();
    expect(routerMock.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: {}, replaceUrl: true }));
  });

  it('al cargar con error en la URL (p.ej. consentimiento cancelado), notifica error', () => {
    activatedRouteMock.snapshot.queryParamMap = convertToParamMap({ error: 'access_denied' });

    crearComponente();

    expect(notificationServiceMock.error).toHaveBeenCalled();
  });
});
