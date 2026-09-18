import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { ConfiguracionService } from '../../../core/services/configuracion.service';
import { EmpleadoService } from '../../../core/services/empleado.service';
import { FichajeService } from '../../../core/services/fichaje.service';
import { NotificationService } from '../../../core/services/notification.service';
import { FichajesAdmin } from './fichajes-admin';

describe('FichajesAdmin', () => {
  let fichajeServiceMock: {
    listarComoAdmin: ReturnType<typeof vi.fn>;
    obtenerResumenDiario: ReturnType<typeof vi.fn>;
    obtenerResumenSemanal: ReturnType<typeof vi.fn>;
    obtenerResumenMensual: ReturnType<typeof vi.fn>;
    corregir: ReturnType<typeof vi.fn>;
  };
  let empleadoServiceMock: { listar: ReturnType<typeof vi.fn> };
  let configuracionServiceMock: { obtenerUbicacion: ReturnType<typeof vi.fn>; actualizarUbicacion: ReturnType<typeof vi.fn> };
  let notificationServiceMock: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  const empleadosVacio = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 100 };
  const ubicacionConfigurada = { lat: 43.5, lng: -5.6, radioMetros: 150, updatedAt: '2026-01-01T00:00:00Z' };

  function crearComponente(): FichajesAdmin {
    const fixture = TestBed.createComponent(FichajesAdmin);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    fichajeServiceMock = {
      listarComoAdmin: vi.fn().mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })),
      obtenerResumenDiario: vi.fn().mockReturnValue(of([])),
      obtenerResumenSemanal: vi.fn().mockReturnValue(of([])),
      obtenerResumenMensual: vi.fn().mockReturnValue(of([])),
      corregir: vi.fn(),
    };
    empleadoServiceMock = { listar: vi.fn().mockReturnValue(of(empleadosVacio)) };
    configuracionServiceMock = {
      obtenerUbicacion: vi.fn().mockReturnValue(of(ubicacionConfigurada)),
      actualizarUbicacion: vi.fn(),
    };
    notificationServiceMock = { success: vi.fn(), error: vi.fn() };

    TestBed.configureTestingModule({
      imports: [FichajesAdmin],
      providers: [
        { provide: FichajeService, useValue: fichajeServiceMock },
        { provide: EmpleadoService, useValue: empleadoServiceMock },
        { provide: ConfiguracionService, useValue: configuracionServiceMock },
        { provide: NotificationService, useValue: notificationServiceMock },
        provideTranslateService(),
      ],
    });
  });

  it('al cargar, pide el listado de empleados y la ubicacion actual', () => {
    crearComponente();

    expect(empleadoServiceMock.listar).toHaveBeenCalledWith(0, 100);
    expect(configuracionServiceMock.obtenerUbicacion).toHaveBeenCalled();
  });

  it('consultar(): con el formulario invalido (sin empleado), no llama al backend', () => {
    const componente = crearComponente();

    componente.consultar();

    expect(fichajeServiceMock.listarComoAdmin).not.toHaveBeenCalled();
  });

  it('consultar() en la pestaña diario carga el listado y el resumen diario del empleado', () => {
    const componente = crearComponente();
    componente.filtros.controls.empleadoId.setValue('2');

    componente.consultar();

    expect(fichajeServiceMock.listarComoAdmin).toHaveBeenCalledWith(
      { empleadoId: 2, desde: componente.filtros.controls.desde.value, hasta: componente.filtros.controls.hasta.value },
      0,
      20,
    );
    expect(fichajeServiceMock.obtenerResumenDiario).toHaveBeenCalled();
  });

  it('esDiaPorDebajoDelMinimo() refleja los dias marcados cumpleMinimo=false del resumen diario', () => {
    fichajeServiceMock.obtenerResumenDiario.mockReturnValue(
      of([{ fecha: '2026-01-05', horasCalculadas: 4, cumpleMinimo: false }]),
    );
    const componente = crearComponente();
    componente.filtros.controls.empleadoId.setValue('2');

    componente.consultar();

    expect(componente.esDiaPorDebajoDelMinimo('2026-01-05')).toBe(true);
    expect(componente.esDiaPorDebajoDelMinimo('2026-01-06')).toBe(false);
  });

  it('cambiarPestana(): tras haber consultado ya, cambiar de pestaña vuelve a consultar', () => {
    const componente = crearComponente();
    componente.filtros.controls.empleadoId.setValue('2');
    componente.consultar();
    fichajeServiceMock.obtenerResumenSemanal.mockClear();

    componente.cambiarPestana('semanal');

    expect(fichajeServiceMock.obtenerResumenSemanal).toHaveBeenCalled();
  });

  it('cambiarPestana(): sin haber consultado antes, no dispara ninguna llamada', () => {
    const componente = crearComponente();

    componente.cambiarPestana('semanal');

    expect(fichajeServiceMock.obtenerResumenSemanal).not.toHaveBeenCalled();
  });

  const fichaje = {
    id: 5,
    fecha: '2026-01-05',
    horaEntrada: '2026-01-05T09:00:00',
    horaSalida: '2026-01-05T17:00:00',
    horasCalculadas: 8,
    observaciones: null,
  };

  it('abrirCorreccion()/cerrarCorreccion() gestionan el estado del modal', () => {
    const componente = crearComponente();

    componente.abrirCorreccion(fichaje);
    expect(componente.fichajeACorregir()).toEqual(fichaje);
    expect(componente.correccionForm.controls.horaEntrada.value).toBe('2026-01-05T09:00');

    componente.cerrarCorreccion();
    expect(componente.fichajeACorregir()).toBeNull();
  });

  it('guardarCorreccion(): con exito, cierra el modal, notifica y refresca la consulta', () => {
    fichajeServiceMock.corregir.mockReturnValue(of({ ...fichaje, horasCalculadas: 7.5 }));
    const componente = crearComponente();
    componente.filtros.controls.empleadoId.setValue('2');
    componente.abrirCorreccion(fichaje);

    componente.guardarCorreccion();

    expect(fichajeServiceMock.corregir).toHaveBeenCalledWith(5, {
      horaEntrada: '2026-01-05T09:00',
      horaSalida: '2026-01-05T17:00',
      observaciones: null,
    });
    expect(componente.fichajeACorregir()).toBeNull();
    expect(componente.guardandoCorreccion()).toBe(false);
    expect(notificationServiceMock.success).toHaveBeenCalled();
  });

  it('ubicacionSinConfigurar() es true cuando lat y lng siguen en el valor semilla (0,0)', () => {
    configuracionServiceMock.obtenerUbicacion.mockReturnValue(of({ lat: 0, lng: 0, radioMetros: 150, updatedAt: '2026-01-01T00:00:00Z' }));
    const componente = crearComponente();

    expect(componente.ubicacionSinConfigurar()).toBe(true);
  });

  it('guardarUbicacion(): con exito, actualiza la ubicacion actual y notifica', () => {
    const nuevaUbicacion = { lat: 43.51, lng: -5.69, radioMetros: 200, updatedAt: '2026-02-01T00:00:00Z' };
    configuracionServiceMock.actualizarUbicacion.mockReturnValue(of(nuevaUbicacion));
    const componente = crearComponente();
    componente.ubicacionForm.setValue({ lat: '43.51', lng: '-5.69', radioMetros: '200' });

    componente.guardarUbicacion();

    expect(configuracionServiceMock.actualizarUbicacion).toHaveBeenCalledWith({ lat: 43.51, lng: -5.69, radioMetros: 200 });
    expect(componente.ubicacionActual()).toEqual(nuevaUbicacion);
    expect(notificationServiceMock.success).toHaveBeenCalled();
  });
});
