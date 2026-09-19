import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { ConfiguracionService } from '../../../core/services/configuracion.service';
import { EmpleadoService } from '../../../core/services/empleado.service';
import { NotificationService } from '../../../core/services/notification.service';
import { VacacionesService } from '../../../core/services/vacaciones.service';
import { VacacionesAdmin } from './vacaciones-admin';

describe('VacacionesAdmin', () => {
  let vacacionesServiceMock: {
    listarTodasPendientes: ReturnType<typeof vi.fn>;
    aprobar: ReturnType<typeof vi.fn>;
    rechazar: ReturnType<typeof vi.fn>;
    obtenerResumenComoAdmin: ReturnType<typeof vi.fn>;
    obtenerCalendarioComoAdmin: ReturnType<typeof vi.fn>;
    listarSolicitudesComoAdmin: ReturnType<typeof vi.fn>;
    asignarDeOficio: ReturnType<typeof vi.fn>;
  };
  let empleadoServiceMock: {
    listar: ReturnType<typeof vi.fn>;
    obtener: ReturnType<typeof vi.fn>;
    actualizarDiasVacacionesAnuales: ReturnType<typeof vi.fn>;
  };
  let configuracionServiceMock: { obtenerVacaciones: ReturnType<typeof vi.fn>; actualizarVacaciones: ReturnType<typeof vi.fn> };
  let notificationServiceMock: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  const empleadosVacio = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 100 };
  const pendientesVacio = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
  const resumenVacio = { total: 23, aprobados: 0, pendientes: 0, disponibles: 23 };
  const configuracionVacaciones = { diasAnualesDefecto: 23, updatedAt: '2026-01-01T00:00:00Z' };

  const empleadoBase = {
    id: 3,
    nombre: 'Ana',
    apellidos: 'Lopez',
    email: 'ana@test.com',
    dniNie: '12345678A',
    telefono: null,
    puesto: null,
    fechaAlta: '2026-01-01',
    fechaBaja: null,
    activo: true,
    tipoUsuario: { codigo: 'EMPLEADO', nombre: 'Empleado' },
    horasJornadaDiaria: null,
    diasVacacionesAnuales: null as number | null,
  };

  function crearComponente(): VacacionesAdmin {
    const fixture = TestBed.createComponent(VacacionesAdmin);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    vacacionesServiceMock = {
      listarTodasPendientes: vi.fn().mockReturnValue(of(pendientesVacio)),
      aprobar: vi.fn().mockReturnValue(of({})),
      rechazar: vi.fn().mockReturnValue(of({})),
      obtenerResumenComoAdmin: vi.fn().mockReturnValue(of(resumenVacio)),
      obtenerCalendarioComoAdmin: vi.fn().mockReturnValue(of([])),
      listarSolicitudesComoAdmin: vi.fn().mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })),
      asignarDeOficio: vi.fn().mockReturnValue(of({})),
    };
    empleadoServiceMock = {
      listar: vi.fn().mockReturnValue(of(empleadosVacio)),
      obtener: vi.fn().mockReturnValue(of(empleadoBase)),
      actualizarDiasVacacionesAnuales: vi.fn().mockReturnValue(of(empleadoBase)),
    };
    configuracionServiceMock = {
      obtenerVacaciones: vi.fn().mockReturnValue(of(configuracionVacaciones)),
      actualizarVacaciones: vi.fn().mockReturnValue(of(configuracionVacaciones)),
    };
    notificationServiceMock = { success: vi.fn(), error: vi.fn() };

    TestBed.configureTestingModule({
      imports: [VacacionesAdmin],
      providers: [
        { provide: VacacionesService, useValue: vacacionesServiceMock },
        { provide: EmpleadoService, useValue: empleadoServiceMock },
        { provide: ConfiguracionService, useValue: configuracionServiceMock },
        { provide: NotificationService, useValue: notificationServiceMock },
        provideTranslateService(),
      ],
    });
  });

  it('al cargar, pide la lista de empleados, la bandeja de pendientes y la configuracion global', () => {
    crearComponente();

    expect(empleadoServiceMock.listar).toHaveBeenCalledWith(0, 100);
    expect(vacacionesServiceMock.listarTodasPendientes).toHaveBeenCalledWith(0, 20);
    expect(configuracionServiceMock.obtenerVacaciones).toHaveBeenCalled();
  });

  it('aprobarDesdePendientes(): aprueba, notifica y refresca la bandeja', () => {
    const componente = crearComponente();
    vacacionesServiceMock.listarTodasPendientes.mockClear();

    componente.aprobarDesdePendientes(10);

    expect(vacacionesServiceMock.aprobar).toHaveBeenCalledWith(10);
    expect(notificationServiceMock.success).toHaveBeenCalled();
    expect(vacacionesServiceMock.listarTodasPendientes).toHaveBeenCalled();
  });

  it('abrirRechazo()/cerrarRechazo() gestionan el estado del modal', () => {
    const componente = crearComponente();

    componente.abrirRechazo(10);
    expect(componente.solicitudARechazar()).toBe(10);

    componente.cerrarRechazo();
    expect(componente.solicitudARechazar()).toBeNull();
  });

  it('guardarRechazo(): sin motivo, no llama al backend', () => {
    const componente = crearComponente();
    componente.abrirRechazo(10);

    componente.guardarRechazo();

    expect(vacacionesServiceMock.rechazar).not.toHaveBeenCalled();
  });

  it('guardarRechazo(): con motivo, rechaza y cierra el modal', () => {
    const componente = crearComponente();
    componente.abrirRechazo(10);
    componente.rechazoForm.controls.motivo.setValue('FALTA_DISPONIBILIDAD');

    componente.guardarRechazo();

    expect(vacacionesServiceMock.rechazar).toHaveBeenCalledWith(10, 'FALTA_DISPONIBILIDAD');
    expect(componente.solicitudARechazar()).toBeNull();
  });

  it('al elegir un empleado en "Por empleado", carga su resumen, calendario y solicitudes', () => {
    const componente = crearComponente();

    componente.porEmpleadoForm.controls.empleadoId.setValue('5');

    expect(vacacionesServiceMock.obtenerResumenComoAdmin).toHaveBeenCalledWith(5, componente.anioMostrado());
    expect(vacacionesServiceMock.obtenerCalendarioComoAdmin).toHaveBeenCalledWith(5, componente.anioMostrado());
    expect(vacacionesServiceMock.listarSolicitudesComoAdmin).toHaveBeenCalledWith(5, null, 0, 20);
    expect(componente.resumenEmpleado()).toEqual(resumenVacio);
  });

  it('filtroEstadoControl: al elegir un estado concreto filtra las solicitudes; "Todos" (sentinel) equivale a sin filtro', () => {
    const componente = crearComponente();
    componente.porEmpleadoForm.controls.empleadoId.setValue('5');
    vacacionesServiceMock.listarSolicitudesComoAdmin.mockClear();

    componente.filtroEstadoControl.setValue('PENDIENTE');
    expect(vacacionesServiceMock.listarSolicitudesComoAdmin).toHaveBeenCalledWith(5, 'PENDIENTE', 0, 20);

    componente.filtroEstadoControl.setValue('TODOS');
    expect(vacacionesServiceMock.listarSolicitudesComoAdmin).toHaveBeenCalledWith(5, null, 0, 20);
  });

  it('guardarAsignacionOficio(): sin empleado seleccionado, no llama al backend', () => {
    const componente = crearComponente();
    componente.asignarOficioForm.setValue({ fechaInicio: '2026-01-05', fechaFin: '2026-01-09' });

    componente.guardarAsignacionOficio();

    expect(vacacionesServiceMock.asignarDeOficio).not.toHaveBeenCalled();
  });

  it('guardarAsignacionOficio(): con empleado y fechas validas, asigna de oficio', () => {
    const componente = crearComponente();
    componente.porEmpleadoForm.controls.empleadoId.setValue('5');
    componente.asignarOficioForm.setValue({ fechaInicio: '2026-01-05', fechaFin: '2026-01-09' });

    componente.guardarAsignacionOficio();

    expect(vacacionesServiceMock.asignarDeOficio).toHaveBeenCalledWith({
      empleadoId: 5,
      fechaInicio: '2026-01-05',
      fechaFin: '2026-01-09',
    });
    expect(componente.mostrarAsignarOficio()).toBe(false);
  });

  it('guardarConfiguracionGlobal(): guarda el nuevo valor global y actualiza el techo del override', () => {
    const componente = crearComponente();
    configuracionServiceMock.actualizarVacaciones.mockReturnValue(of({ diasAnualesDefecto: 25, updatedAt: '2026-02-01T00:00:00Z' }));
    componente.configuracionGlobalForm.controls.diasAnualesDefecto.setValue('25');

    componente.guardarConfiguracionGlobal();

    expect(configuracionServiceMock.actualizarVacaciones).toHaveBeenCalledWith({ diasAnualesDefecto: 25 });
    expect(componente.diasAnualesGlobal()).toBe(25);
  });

  it('guardarConfiguracionGlobal(): si baja el techo por debajo de un valor ya escrito en el override, lo revalida al momento', () => {
    const componente = crearComponente();
    componente.overrideForm.controls.dias.setValue('20');
    expect(componente.overrideForm.controls.dias.valid).toBe(true);
    configuracionServiceMock.actualizarVacaciones.mockReturnValue(of({ diasAnualesDefecto: 15, updatedAt: '2026-02-01T00:00:00Z' }));
    componente.configuracionGlobalForm.controls.diasAnualesDefecto.setValue('15');

    componente.guardarConfiguracionGlobal();

    expect(componente.overrideForm.controls.dias.hasError('fueraDeRango')).toBe(true);
  });

  it('al elegir un empleado en la sobrescritura, precarga su valor propio (o vacio si sigue el global)', () => {
    empleadoServiceMock.obtener.mockReturnValue(of({ ...empleadoBase, diasVacacionesAnuales: 15 }));
    const componente = crearComponente();

    componente.overrideForm.controls.empleadoId.setValue('3');

    expect(empleadoServiceMock.obtener).toHaveBeenCalledWith(3);
    expect(componente.overrideForm.controls.dias.value).toBe('15');
  });

  it('guardarOverride(): con un valor por encima del global, no guarda y marca el campo invalido', () => {
    const componente = crearComponente();
    componente.overrideForm.setValue({ empleadoId: '3', dias: '30' });

    componente.guardarOverride();

    expect(empleadoServiceMock.actualizarDiasVacacionesAnuales).not.toHaveBeenCalled();
    expect(componente.overrideForm.controls.dias.hasError('fueraDeRango')).toBe(true);
  });

  it('guardarOverride(): con un valor valido, lo persiste', () => {
    const componente = crearComponente();
    componente.overrideForm.setValue({ empleadoId: '3', dias: '15' });

    componente.guardarOverride();

    expect(empleadoServiceMock.actualizarDiasVacacionesAnuales).toHaveBeenCalledWith(3, 15);
  });

  it('guardarOverride(): con el campo vacio, envia null (vuelve a usar el valor global)', () => {
    const componente = crearComponente();
    componente.overrideForm.setValue({ empleadoId: '3', dias: '' });

    componente.guardarOverride();

    expect(empleadoServiceMock.actualizarDiasVacacionesAnuales).toHaveBeenCalledWith(3, null);
  });
});
