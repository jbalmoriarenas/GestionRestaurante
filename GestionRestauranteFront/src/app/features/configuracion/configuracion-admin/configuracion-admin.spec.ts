import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { ConfiguracionService } from '../../../core/services/configuracion.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ConfiguracionAdmin } from './configuracion-admin';

describe('ConfiguracionAdmin', () => {
  let configuracionServiceMock: {
    obtenerEmpresa: ReturnType<typeof vi.fn>;
    actualizarEmpresa: ReturnType<typeof vi.fn>;
    obtenerLogo: ReturnType<typeof vi.fn>;
    obtenerUbicacion: ReturnType<typeof vi.fn>;
    actualizarUbicacion: ReturnType<typeof vi.fn>;
    obtenerVacaciones: ReturnType<typeof vi.fn>;
    actualizarVacaciones: ReturnType<typeof vi.fn>;
  };
  let notificationServiceMock: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  const empresaSinLogo = { nombreEmpresa: 'Sidrería La Zapica', logoUrl: null };
  const ubicacionConfigurada = { lat: 43.5, lng: -5.6, radioMetros: 150, updatedAt: '2026-01-01T00:00:00Z' };
  const vacacionesConfiguradas = { diasAnualesDefecto: 23, updatedAt: '2026-01-01T00:00:00Z' };

  function crearComponente(): ConfiguracionAdmin {
    const fixture = TestBed.createComponent(ConfiguracionAdmin);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    // jsdom no siempre implementa createObjectURL/revokeObjectURL; se mockean para que la
    // previsualizacion del logo (URL.createObjectURL) sea determinista en el test.
    URL.createObjectURL = vi.fn(() => 'blob:mock-url');
    URL.revokeObjectURL = vi.fn();

    configuracionServiceMock = {
      obtenerEmpresa: vi.fn().mockReturnValue(of(empresaSinLogo)),
      actualizarEmpresa: vi.fn().mockReturnValue(of(empresaSinLogo)),
      obtenerLogo: vi.fn().mockReturnValue(of(new Blob())),
      obtenerUbicacion: vi.fn().mockReturnValue(of(ubicacionConfigurada)),
      actualizarUbicacion: vi.fn().mockReturnValue(of(ubicacionConfigurada)),
      obtenerVacaciones: vi.fn().mockReturnValue(of(vacacionesConfiguradas)),
      actualizarVacaciones: vi.fn().mockReturnValue(of(vacacionesConfiguradas)),
    };
    notificationServiceMock = { success: vi.fn(), error: vi.fn() };

    TestBed.configureTestingModule({
      imports: [ConfiguracionAdmin],
      providers: [
        { provide: ConfiguracionService, useValue: configuracionServiceMock },
        { provide: NotificationService, useValue: notificationServiceMock },
        provideTranslateService(),
      ],
    });
  });

  it('al cargar, pide los datos de empresa, la ubicacion y los dias de vacaciones por defecto', () => {
    crearComponente();

    expect(configuracionServiceMock.obtenerEmpresa).toHaveBeenCalled();
    expect(configuracionServiceMock.obtenerUbicacion).toHaveBeenCalled();
    expect(configuracionServiceMock.obtenerVacaciones).toHaveBeenCalled();
  });

  it('al cargar con la empresa sin logo, no pide el logo (evita un 404 a ciegas)', () => {
    crearComponente();

    expect(configuracionServiceMock.obtenerLogo).not.toHaveBeenCalled();
  });

  it('al cargar con la empresa con logo, pide el logo y lo muestra como previsualizacion', () => {
    configuracionServiceMock.obtenerEmpresa.mockReturnValue(
      of({ nombreEmpresa: 'Sidrería La Zapica', logoUrl: '/api/configuracion/empresa/logo' }),
    );
    const componente = crearComponente();

    expect(configuracionServiceMock.obtenerLogo).toHaveBeenCalled();
    expect(componente.logoPreviewUrl()).toBe('blob:mock-url');
  });

  it('al seleccionar un archivo nuevo, lo previsualiza localmente sin llamar al backend', () => {
    const componente = crearComponente();
    configuracionServiceMock.obtenerLogo.mockClear();
    const archivo = new File(['contenido'], 'logo.png', { type: 'image/png' });

    componente.logoControl.setValue(archivo);

    expect(componente.logoPreviewUrl()).toBe('blob:mock-url');
    expect(configuracionServiceMock.obtenerLogo).not.toHaveBeenCalled();
  });

  it('guardarEmpresa(): con el formulario invalido, no llama al servicio', () => {
    const componente = crearComponente();
    componente.empresaForm.controls.nombreEmpresa.setValue('');

    componente.guardarEmpresa();

    expect(configuracionServiceMock.actualizarEmpresa).not.toHaveBeenCalled();
  });

  it('guardarEmpresa(): con exito, notifica y limpia el archivo seleccionado', () => {
    const componente = crearComponente();
    const archivo = new File(['contenido'], 'logo.png', { type: 'image/png' });
    componente.empresaForm.controls.nombreEmpresa.setValue('Nuevo Nombre');
    componente.logoControl.setValue(archivo);

    componente.guardarEmpresa();

    expect(configuracionServiceMock.actualizarEmpresa).toHaveBeenCalledWith('Nuevo Nombre', archivo);
    expect(componente.guardandoEmpresa()).toBe(false);
    expect(componente.logoControl.value).toBeNull();
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
    expect(componente.ubicacionSinConfigurar()).toBe(false);
    expect(notificationServiceMock.success).toHaveBeenCalled();
  });

  it('guardarVacaciones(): con exito, notifica', () => {
    configuracionServiceMock.actualizarVacaciones.mockReturnValue(of({ diasAnualesDefecto: 25, updatedAt: '2026-02-01T00:00:00Z' }));
    const componente = crearComponente();
    componente.vacacionesForm.controls.diasAnualesDefecto.setValue('25');

    componente.guardarVacaciones();

    expect(configuracionServiceMock.actualizarVacaciones).toHaveBeenCalledWith({ diasAnualesDefecto: 25 });
    expect(componente.guardandoVacaciones()).toBe(false);
    expect(notificationServiceMock.success).toHaveBeenCalled();
  });

  it('guardarVacaciones(): con el formulario invalido, no llama al servicio', () => {
    const componente = crearComponente();
    componente.vacacionesForm.controls.diasAnualesDefecto.setValue('0');

    componente.guardarVacaciones();

    expect(configuracionServiceMock.actualizarVacaciones).not.toHaveBeenCalled();
  });
});
