import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { Login } from './login';

describe('Login', () => {
  let authServiceMock: { login: ReturnType<typeof vi.fn>; solicitarRecuperacion: ReturnType<typeof vi.fn> };
  let routerMock: { navigateByUrl: ReturnType<typeof vi.fn> };

  const loginResponse = {
    token: 'jwt',
    empleadoId: 1,
    nombre: 'Ana',
    apellidos: 'Test',
    tipoUsuario: { codigo: 'EMPLEADO', nombre: 'Empleado' },
    permisos: [],
  };

  function crearComponente(): Login {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    authServiceMock = {
      login: vi.fn().mockReturnValue(of(loginResponse)),
      solicitarRecuperacion: vi.fn().mockReturnValue(of(undefined)),
    };
    routerMock = { navigateByUrl: vi.fn() };

    TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: Router, useValue: routerMock },
        provideTranslateService(),
      ],
    });
  });

  it('submit(): con el formulario invalido, no llama al servicio', () => {
    const componente = crearComponente();
    componente.form.controls.email.setValue('no-es-un-email');

    componente.submit();

    expect(authServiceMock.login).not.toHaveBeenCalled();
  });

  it('submit(): con exito, navega a la raiz', () => {
    const componente = crearComponente();
    componente.form.setValue({ email: 'ana@test.com', password: 'clave123' });

    componente.submit();

    expect(routerMock.navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('submit(): con 401 muestra el mensaje de credenciales invalidas y apaga el loading', () => {
    authServiceMock.login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    const componente = crearComponente();
    componente.form.setValue({ email: 'ana@test.com', password: 'mala' });

    componente.submit();

    expect(componente.loading()).toBe(false);
    expect(componente.errorMessage()).not.toBeNull();
  });

  it('abrirModalRecuperacion(): abre el modal y resetea el estado de un intento anterior', () => {
    const componente = crearComponente();
    componente.recuperacionEnviada.set(true);
    componente.errorRecuperacion.set('error anterior');

    componente.abrirModalRecuperacion();

    expect(componente.mostrarModalRecuperacion()).toBe(true);
    expect(componente.recuperacionEnviada()).toBe(false);
    expect(componente.errorRecuperacion()).toBeNull();
  });

  it('cerrarModalRecuperacion(): cierra el modal', () => {
    const componente = crearComponente();
    componente.mostrarModalRecuperacion.set(true);

    componente.cerrarModalRecuperacion();

    expect(componente.mostrarModalRecuperacion()).toBe(false);
  });

  it('enviarRecuperacion(): con el formulario invalido, no llama al servicio', () => {
    const componente = crearComponente();
    componente.formRecuperacion.controls.email.setValue('no-es-un-email');

    componente.enviarRecuperacion();

    expect(authServiceMock.solicitarRecuperacion).not.toHaveBeenCalled();
  });

  it('enviarRecuperacion(): con exito, marca el envio sin cerrar el modal', () => {
    const componente = crearComponente();
    componente.mostrarModalRecuperacion.set(true);
    componente.formRecuperacion.controls.email.setValue('ana@test.com');

    componente.enviarRecuperacion();

    expect(authServiceMock.solicitarRecuperacion).toHaveBeenCalledWith('ana@test.com');
    expect(componente.recuperacionEnviada()).toBe(true);
    expect(componente.enviandoRecuperacion()).toBe(false);
    expect(componente.mostrarModalRecuperacion()).toBe(true);
  });

  it('enviarRecuperacion(): con error del backend, muestra ese mensaje sin cerrar el modal ni marcar el envio', () => {
    authServiceMock.solicitarRecuperacion.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { message: 'No existe ningun empleado con ese email' } })),
    );
    const componente = crearComponente();
    componente.mostrarModalRecuperacion.set(true);
    componente.formRecuperacion.controls.email.setValue('no-existe@test.com');

    componente.enviarRecuperacion();

    expect(componente.errorRecuperacion()).toBe('No existe ningun empleado con ese email');
    expect(componente.recuperacionEnviada()).toBe(false);
    expect(componente.mostrarModalRecuperacion()).toBe(true);
  });
});
