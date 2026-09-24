import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { VacacionesService } from '../../../core/services/vacaciones.service';
import { NotificationService } from '../../../core/services/notification.service';
import { MisVacaciones } from './mis-vacaciones';

/** yyyy-MM-dd en LOCAL, no UTC (mismo criterio que hoyLocalISO() de produccion) - usado para
 * construir fechas de prueba relativas a "hoy" en vez de literales fijos, que dejan de ser
 * validos (fechaPasada) en cuanto el calendario avanza mas alla de la fecha hardcodeada. */
function fechaISO(fecha: Date): string {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

function diasDesdeHoy(dias: number): string {
  const fecha = new Date();
  fecha.setDate(fecha.getDate() + dias);
  return fechaISO(fecha);
}

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

  it('el formulario es invalido si el rango cruza dos años (aunque uno de ellos sea el actual)', () => {
    const componente = crearComponente();

    componente.form.setValue({ fechaInicio: '2026-12-29', fechaFin: '2027-01-03' });

    expect(componente.form.hasError('distintoAnio')).toBe(true);
  });

  it('el formulario es invalido si el rango cae en un año que no es el actual, aunque inicio y fin coincidan entre si', () => {
    const componente = crearComponente();
    // Tiene que ser un año FUTURO, no pasado: un año pasado siempre cae tambien antes de hoy,
    // asi que el formulario devolveria fechaPasada en vez de distintoAnio (fechaPasada se
    // comprueba primero, ver fechaFinNoAnteriorAInicio en mis-vacaciones.ts).
    const anioFuturo = new Date().getFullYear() + 1;

    componente.form.setValue({ fechaInicio: `${anioFuturo}-06-01`, fechaFin: `${anioFuturo}-06-05` });

    expect(componente.form.hasError('distintoAnio')).toBe(true);
  });

  it('fechaFinMax() limita el selector de fecha al 31 de diciembre del año en curso', () => {
    const componente = crearComponente();
    const anioActual = new Date().getFullYear();

    expect(componente.fechaFinMax()).toBe(`${anioActual}-12-31`);
  });

  it('fechaFinMin() usa fechaInicio si ya esta rellena, o hoy si no', () => {
    const componente = crearComponente();

    expect(componente.fechaFinMin()).toBe(diasDesdeHoy(0));

    const fechaFutura = diasDesdeHoy(10);
    componente.form.controls.fechaInicio.setValue(fechaFutura);
    expect(componente.fechaFinMin()).toBe(fechaFutura);
  });

  it('fechaInicioMin() esta siempre anclado a hoy (no se pueden pedir vacaciones en el pasado)', () => {
    const componente = crearComponente();

    expect(componente.fechaInicioMin()).toBe(diasDesdeHoy(0));
  });

  it('fechaInicioMax() usa fechaFin si ya esta rellena, o el 31 de diciembre del año en curso si no', () => {
    const componente = crearComponente();
    const anioActual = new Date().getFullYear();

    expect(componente.fechaInicioMax()).toBe(`${anioActual}-12-31`);

    componente.form.controls.fechaFin.setValue(`${anioActual}-06-15`);
    expect(componente.fechaInicioMax()).toBe(`${anioActual}-06-15`);
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
    const fechaInicio = diasDesdeHoy(1);
    const fechaFin = diasDesdeHoy(5);
    componente.form.setValue({ fechaInicio, fechaFin });
    vacacionesServiceMock.obtenerResumen.mockClear();

    componente.solicitar();

    expect(vacacionesServiceMock.solicitar).toHaveBeenCalledWith({ fechaInicio, fechaFin });
    expect(componente.enviandoSolicitud()).toBe(false);
    expect(componente.form.controls.fechaInicio.value).toBe('');
    expect(notificationServiceMock.success).toHaveBeenCalled();
    expect(vacacionesServiceMock.obtenerResumen).toHaveBeenCalled();
  });

  it('solicitar(): si el backend falla (409 dias insuficientes), no resetea el formulario', () => {
    vacacionesServiceMock.solicitar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    const componente = crearComponente();
    const fechaInicio = diasDesdeHoy(1);
    componente.form.setValue({ fechaInicio, fechaFin: diasDesdeHoy(5) });

    componente.solicitar();

    expect(vacacionesServiceMock.solicitar).toHaveBeenCalled();
    expect(componente.enviandoSolicitud()).toBe(false);
    expect(componente.form.controls.fechaInicio.value).toBe(fechaInicio);
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

  it('motivoRechazoLabel(): sin motivo, muestra un guion', () => {
    const componente = crearComponente();

    expect(componente.motivoRechazoLabel(null)).toBe('—');
  });

  it('motivoRechazoLabel(): con un codigo conocido, pasa por el traductor en vez de devolverlo tal cual', () => {
    const componente = crearComponente();
    const translateService = TestBed.inject(TranslateService);
    const instantSpy = vi.spyOn(translateService, 'instant');

    const resultado = componente.motivoRechazoLabel('FALTA_DISPONIBILIDAD');

    expect(instantSpy).toHaveBeenCalledWith('vacaciones.motivosRechazo.FALTA_DISPONIBILIDAD');
    expect(resultado).not.toBe('FALTA_DISPONIBILIDAD');
  });

  it('motivoRechazoLabel(): con un motivo libre de antes de este cambio, lo muestra tal cual', () => {
    const componente = crearComponente();

    expect(componente.motivoRechazoLabel('no queda cupo')).toBe('no queda cupo');
  });
});
