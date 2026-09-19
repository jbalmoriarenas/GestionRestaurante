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
  let empleadoServiceMock: {
    listar: ReturnType<typeof vi.fn>;
    obtener: ReturnType<typeof vi.fn>;
    actualizarHorarioTeorico: ReturnType<typeof vi.fn>;
  };
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
    empleadoServiceMock = {
      listar: vi.fn().mockReturnValue(of(empleadosVacio)),
      obtener: vi.fn().mockReturnValue(of({ id: 0, horasJornadaDiaria: null })),
      actualizarHorarioTeorico: vi.fn().mockReturnValue(of({ id: 0, horasJornadaDiaria: null })),
    };
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

  it('consultar() en la pestaña diario carga el resumen diario (totales, no cada fichaje) del empleado', () => {
    const componente = crearComponente();
    componente.filtros.controls.empleadoId.setValue('2');

    componente.consultar();

    expect(fichajeServiceMock.obtenerResumenDiario).toHaveBeenCalledWith(
      2,
      componente.filtros.controls.desde.value,
      componente.filtros.controls.hasta.value,
    );
    expect(fichajeServiceMock.listarComoAdmin).not.toHaveBeenCalled();
  });

  it('consultar() en diario expone horasTeoricas y cumpleMinimo del resumen para pintar la tabla', () => {
    fichajeServiceMock.obtenerResumenDiario.mockReturnValue(
      of([{ fecha: '2026-01-05', horasCalculadas: 4, horasTeoricas: 8, cumpleMinimo: false }]),
    );
    const componente = crearComponente();
    componente.filtros.controls.empleadoId.setValue('2');

    componente.consultar();

    expect(componente.resumenDiario()).toEqual([
      { fecha: '2026-01-05', horasCalculadas: 4, horasTeoricas: 8, cumpleMinimo: false },
    ]);
  });

  it('abrirDetalleDia(): pide a listarComoAdmin los fichajes de ese dia concreto', () => {
    const fichajesDelDia = [
      { id: 1, fecha: '2026-01-05', horaEntrada: '2026-01-05T09:00:00', horaSalida: '2026-01-05T13:00:00', horasCalculadas: 4, observaciones: null },
    ];
    fichajeServiceMock.listarComoAdmin.mockReturnValue(of({ content: fichajesDelDia, totalElements: 1, totalPages: 1, number: 0, size: 100 }));
    const componente = crearComponente();
    componente.filtros.controls.empleadoId.setValue('2');

    componente.abrirDetalleDia('2026-01-05');

    expect(fichajeServiceMock.listarComoAdmin).toHaveBeenCalledWith(
      { empleadoId: 2, desde: '2026-01-05', hasta: '2026-01-05' },
      0,
      100,
    );
    expect(componente.detalleDia()).toEqual({ fecha: '2026-01-05', fichajes: fichajesDelDia });

    componente.cerrarDetalleDia();
    expect(componente.detalleDia()).toBeNull();
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

  it('abrirCorreccion(): cierra el detalle del día si estaba abierto, para no apilar dos modales', () => {
    const componente = crearComponente();
    componente.detalleDia.set({ fecha: '2026-01-05', fichajes: [] });

    componente.abrirCorreccion(fichaje);

    expect(componente.detalleDia()).toBeNull();
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

  const empleadoConHorarioPropio = {
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
    horasJornadaDiaria: 6,
  };

  it('al elegir empleado en la pestaña horarios, precarga su horasJornadaDiaria (o el valor por defecto si no tiene)', () => {
    empleadoServiceMock.obtener.mockReturnValue(of(empleadoConHorarioPropio));
    const componente = crearComponente();

    componente.horarioTeoricoForm.controls.empleadoId.setValue('3');

    expect(empleadoServiceMock.obtener).toHaveBeenCalledWith(3);
    expect(componente.horarioTeoricoForm.controls.horas.value).toBe('6');
    expect(componente.horarioTeoricoEmpleado()).toEqual(empleadoConHorarioPropio);
  });

  it('al elegir un empleado sin horario propio, precarga el valor por defecto (8h)', () => {
    empleadoServiceMock.obtener.mockReturnValue(of({ ...empleadoConHorarioPropio, horasJornadaDiaria: null }));
    const componente = crearComponente();

    componente.horarioTeoricoForm.controls.empleadoId.setValue('3');

    expect(componente.horarioTeoricoForm.controls.horas.value).toBe('8');
  });

  it('guardarHorarioTeorico(): con exito, actualiza el empleado y notifica', () => {
    const empleadoActualizado = { ...empleadoConHorarioPropio, horasJornadaDiaria: 7 };
    empleadoServiceMock.actualizarHorarioTeorico.mockReturnValue(of(empleadoActualizado));
    const componente = crearComponente();
    componente.horarioTeoricoForm.setValue({ empleadoId: '3', horas: '7' });

    componente.guardarHorarioTeorico();

    expect(empleadoServiceMock.actualizarHorarioTeorico).toHaveBeenCalledWith(3, 7);
    expect(componente.horarioTeoricoEmpleado()).toEqual(empleadoActualizado);
    expect(notificationServiceMock.success).toHaveBeenCalled();
  });

  it('guardarHorarioTeorico(): sin empleado seleccionado, no llama al backend', () => {
    const componente = crearComponente();

    componente.guardarHorarioTeorico();

    expect(empleadoServiceMock.actualizarHorarioTeorico).not.toHaveBeenCalled();
  });

  it('guardarHorarioTeorico(): acepta un decimal con coma ("8,5") y lo envia como 8.5', () => {
    empleadoServiceMock.actualizarHorarioTeorico.mockReturnValue(of(empleadoConHorarioPropio));
    const componente = crearComponente();
    componente.horarioTeoricoForm.setValue({ empleadoId: '3', horas: '8,5' });

    componente.guardarHorarioTeorico();

    expect(empleadoServiceMock.actualizarHorarioTeorico).toHaveBeenCalledWith(3, 8.5);
  });

  it('guardarHorarioTeorico(): acepta el formato horas:minutos ("8:30") y lo envia como 8.5', () => {
    empleadoServiceMock.actualizarHorarioTeorico.mockReturnValue(of(empleadoConHorarioPropio));
    const componente = crearComponente();
    componente.horarioTeoricoForm.setValue({ empleadoId: '3', horas: '8:30' });

    componente.guardarHorarioTeorico();

    expect(empleadoServiceMock.actualizarHorarioTeorico).toHaveBeenCalledWith(3, 8.5);
  });

  it('guardarHorarioTeorico(): con un formato que no es ni decimal ni horas:minutos, no guarda y marca el campo invalido', () => {
    const componente = crearComponente();
    componente.horarioTeoricoForm.setValue({ empleadoId: '3', horas: '8h30' });

    componente.guardarHorarioTeorico();

    expect(empleadoServiceMock.actualizarHorarioTeorico).not.toHaveBeenCalled();
    expect(componente.horarioTeoricoForm.controls.horas.hasError('formatoInvalido')).toBe(true);
    expect(componente.horarioTeoricoHorasErrorMessage()).toBeTruthy();
  });

  it('guardarHorarioTeorico(): con una jornada fuera de 1-12h, no guarda', () => {
    const componente = crearComponente();
    componente.horarioTeoricoForm.setValue({ empleadoId: '3', horas: '15' });

    componente.guardarHorarioTeorico();

    expect(empleadoServiceMock.actualizarHorarioTeorico).not.toHaveBeenCalled();
    expect(componente.horarioTeoricoForm.controls.horas.hasError('fueraDeRango')).toBe(true);
  });
});
