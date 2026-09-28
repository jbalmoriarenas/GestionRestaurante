import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { RestablecerPassword } from './restablecer-password';

describe('RestablecerPassword', () => {
  let authServiceMock: { restablecerPassword: ReturnType<typeof vi.fn> };
  let notificationServiceMock: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };
  let routerMock: { navigateByUrl: ReturnType<typeof vi.fn> };
  let activatedRouteMock: { snapshot: { queryParamMap: ReturnType<typeof convertToParamMap> } };

  function crearComponente(): RestablecerPassword {
    const fixture = TestBed.createComponent(RestablecerPassword);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function configurarModulo(token: string | null): void {
    activatedRouteMock = {
      snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) },
    };

    TestBed.configureTestingModule({
      imports: [RestablecerPassword],
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: NotificationService, useValue: notificationServiceMock },
        { provide: Router, useValue: routerMock },
        { provide: ActivatedRoute, useValue: activatedRouteMock },
        provideTranslateService(),
      ],
    });
  }

  beforeEach(() => {
    authServiceMock = { restablecerPassword: vi.fn().mockReturnValue(of(undefined)) };
    notificationServiceMock = { success: vi.fn(), error: vi.fn() };
    routerMock = { navigateByUrl: vi.fn() };
  });

  it('sin token en la URL, muestra el enlace invalido sin llamar al backend', () => {
    configurarModulo(null);

    const componente = crearComponente();

    expect(componente.enlaceInvalido()).toBe(true);
    expect(authServiceMock.restablecerPassword).not.toHaveBeenCalled();
  });

  it('con token en la URL, no muestra el enlace invalido', () => {
    configurarModulo('token-valido');

    const componente = crearComponente();

    expect(componente.enlaceInvalido()).toBe(false);
  });

  it('submit(): con contraseñas que no coinciden, el formulario es invalido y no llama al servicio', () => {
    configurarModulo('token-valido');
    const componente = crearComponente();
    componente.form.controls.password.setValue('ClaveSegura99');
    componente.form.controls.confirmarPassword.setValue('OtraClave99');

    componente.submit();

    expect(componente.form.invalid).toBe(true);
    expect(authServiceMock.restablecerPassword).not.toHaveBeenCalled();
  });

  it('submit(): con una contraseña que no cumple el patron, no llama al servicio', () => {
    configurarModulo('token-valido');
    const componente = crearComponente();
    componente.form.controls.password.setValue('todaminuscula');
    componente.form.controls.confirmarPassword.setValue('todaminuscula');

    componente.submit();

    expect(authServiceMock.restablecerPassword).not.toHaveBeenCalled();
  });

  it('submit(): con exito, notifica y navega al login', () => {
    configurarModulo('token-valido');
    const componente = crearComponente();
    componente.form.controls.password.setValue('ClaveSegura99');
    componente.form.controls.confirmarPassword.setValue('ClaveSegura99');

    componente.submit();

    expect(authServiceMock.restablecerPassword).toHaveBeenCalledWith('token-valido', 'ClaveSegura99');
    expect(notificationServiceMock.success).toHaveBeenCalled();
    expect(routerMock.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('submit(): con token invalido/caducado, muestra el mensaje del backend y apaga el loading', () => {
    authServiceMock.restablecerPassword.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { message: 'El enlace de recuperación no es válido o ha caducado. Solicita uno nuevo.' },
          }),
      ),
    );
    configurarModulo('token-caducado');
    const componente = crearComponente();
    componente.form.controls.password.setValue('ClaveSegura99');
    componente.form.controls.confirmarPassword.setValue('ClaveSegura99');

    componente.submit();

    expect(componente.loading()).toBe(false);
    expect(componente.errorMessage()).toBe('El enlace de recuperación no es válido o ha caducado. Solicita uno nuevo.');
    expect(routerMock.navigateByUrl).not.toHaveBeenCalled();
  });

  it('passwordErrorMessage(): sin tocar el campo, no muestra error aunque este vacio', () => {
    configurarModulo('token-valido');
    const componente = crearComponente();

    expect(componente.passwordErrorMessage()).toBeNull();
  });

  it('passwordErrorMessage(): tocado y vacio, distingue el mensaje de obligatorio', () => {
    configurarModulo('token-valido');
    const componente = crearComponente();
    componente.form.controls.password.markAsTouched();

    expect(componente.passwordErrorMessage()).not.toBeNull();
  });
});
