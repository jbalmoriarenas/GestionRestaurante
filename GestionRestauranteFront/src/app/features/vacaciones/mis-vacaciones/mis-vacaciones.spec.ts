import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { VacacionesService } from '../../../core/services/vacaciones.service';
import { NotificationService } from '../../../core/services/notification.service';
import { MisVacaciones } from './mis-vacaciones';

describe('MisVacaciones', () => {
  let vacacionesServiceMock: {
    obtenerResumen: ReturnType<typeof vi.fn>;
    obtenerCalendario: ReturnType<typeof vi.fn>;
    solicitar: ReturnType<typeof vi.fn>;
    cancelar: ReturnType<typeof vi.fn>;
    listarMisSolicitudes: ReturnType<typeof vi.fn>;
  };
  let notificationServiceMock: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  const resumenVacio = { total: 23, aprobados: 0, pendientes: 0, disponibles: 23 };
  const paginaVacia = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 };

  function crearComponente(): MisVacaciones {
    const fixture = TestBed.createComponent(MisVacaciones);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    vacacionesServiceMock = {
      obtenerResumen: vi.fn().mockReturnValue(of(resumenVacio)),
      obtenerCalendario: vi.fn().mockReturnValue(of([])),
      solicitar: vi.fn(),
      cancelar: vi.fn(),
      listarMisSolicitudes: vi.fn().mockReturnValue(of(paginaVacia)),
    };
    notificationServiceMock = { success: vi.fn(), error: vi.fn() };

    TestBed.configureTestingModule({
      imports: [MisVacaciones],
      providers: [
        { provide: VacacionesService, useValue: vacacionesServiceMock },
        { provide: NotificationService, useValue: notificationServiceMock },
        provideTranslateService(),
      ],
    });
  });

  it('al cargar, pide el resumen y el calendario del anio actual', () => {
    const anioActual = new Date().getFullYear();
    crearComponente();

    expect(vacacionesServiceMock.obtenerResumen).toHaveBeenCalledWith(anioActual);
    expect(vacacionesServiceMock.obtenerCalendario).toHaveBeenCalledWith(anioActual);
  });

  it('el formulario es invalido si la fecha de fin es anterior a la de inicio', () => {
    const componente = crearComponente();

    componente.form.setValue({ fechaInicio: '2026-01-10', fechaFin: '2026-01-05' });

    expect(componente.form.hasError('rangoInvalido')).toBe(true);
  });

  it('el formulario es invalido si el rango cruza el año natural', () => {
    const componente = crearComponente();

    componente.form.setValue({ fechaInicio: '2026-12-29', fechaFin: '2027-01-03' });

    expect(componente.form.hasError('distintoAnio')).toBe(true);
  });

  it('fechaFinMax() limita el selector de fecha al 31 de diciembre del año de fechaInicio', () => {
    const componente = crearComponente();
    componente.form.controls.fechaInicio.setValue('2026-06-15');

    expect(componente.fechaFinMax()).toBe('2026-12-31');
  });

  it('solicitar(): con el formulario invalido, no llama al backend', () => {
    const componente = crearComponente();
    componente.form.setValue({ fechaInicio: '', fechaFin: '' });

    componente.solicitar();

    expect(vacacionesServiceMock.solicitar).not.toHaveBeenCalled();
  });

  it('solicitar(): con exito, resetea el formulario y refresca resumen/calendario/solicitudes', () => {
    vacacionesServiceMock.solicitar.mockReturnValue(of({ id: 1 }));
    const componente = crearComponente();
    componente.form.setValue({ fechaInicio: '2026-01-05', fechaFin: '2026-01-09' });
    vacacionesServiceMock.obtenerResumen.mockClear();

    componente.solicitar();

    expect(vacacionesServiceMock.solicitar).toHaveBeenCalledWith({
      fechaInicio: '2026-01-05',
      fechaFin: '2026-01-09',
    });
    expect(componente.enviandoSolicitud()).toBe(false);
    expect(componente.form.controls.fechaInicio.value).toBe('');
    expect(notificationServiceMock.success).toHaveBeenCalled();
    expect(vacacionesServiceMock.obtenerResumen).toHaveBeenCalled();
  });

  it('solicitar(): si el backend falla (409 dias insuficientes), no resetea el formulario', () => {
    vacacionesServiceMock.solicitar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    const componente = crearComponente();
    componente.form.setValue({ fechaInicio: '2026-01-05', fechaFin: '2026-01-09' });

    componente.solicitar();

    expect(componente.enviandoSolicitud()).toBe(false);
    expect(componente.form.controls.fechaInicio.value).toBe('2026-01-05');
  });

  const solicitudPendiente = {
    id: 7,
    fechaInicio: '2026-01-05',
    fechaFin: '2026-01-09',
    diasSolicitados: 5,
    estado: 'PENDIENTE' as const,
    fechaSolicitud: '2026-01-01T00:00:00Z',
    motivoRechazo: null,
  };

  it('confirmarCancelacion(): guarda la solicitud para mostrar el modal, sin llamar todavia al backend', () => {
    const componente = crearComponente();

    componente.confirmarCancelacion(solicitudPendiente);

    expect(componente.solicitudACancelar()).toEqual(solicitudPendiente);
    expect(vacacionesServiceMock.cancelar).not.toHaveBeenCalled();
  });

  it('cerrarModalCancelacion(): descarta la solicitud sin llamar al backend', () => {
    const componente = crearComponente();
    componente.confirmarCancelacion(solicitudPendiente);

    componente.cerrarModalCancelacion();

    expect(componente.solicitudACancelar()).toBeNull();
    expect(vacacionesServiceMock.cancelar).not.toHaveBeenCalled();
  });

  it('cancelar(): tras confirmar en el modal, llama al backend, notifica y refresca el listado', () => {
    vacacionesServiceMock.cancelar.mockReturnValue(of(undefined));
    const componente = crearComponente();
    componente.confirmarCancelacion(solicitudPendiente);

    componente.cancelar();

    expect(vacacionesServiceMock.cancelar).toHaveBeenCalledWith(7);
    expect(componente.solicitudACancelar()).toBeNull();
    expect(notificationServiceMock.success).toHaveBeenCalled();
  });
});
