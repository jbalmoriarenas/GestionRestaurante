import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ApiError } from '../../../core/models/api-error.model';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Footer } from '../../../layout/footer/footer';
import { UiButton } from '../../../shared/ui/ui-button/ui-button';
import { UiInput } from '../../../shared/ui/ui-input/ui-input';

function passwordsCoincidenValidator(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirmarPassword = group.get('confirmarPassword')?.value;
  return password === confirmarPassword ? null : { passwordsNoCoinciden: true };
}

@Component({
  selector: 'app-restablecer-password',
  imports: [ReactiveFormsModule, RouterLink, UiInput, UiButton, Footer, TranslatePipe],
  templateUrl: './restablecer-password.html',
  styleUrl: './restablecer-password.scss',
})
export class RestablecerPassword implements OnInit {
  private static readonly PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;

  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly translateService = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private token: string | null = null;

  readonly enlaceInvalido = signal(false);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.formBuilder.nonNullable.group(
    {
      password: ['', [Validators.required, Validators.pattern(RestablecerPassword.PASSWORD_PATTERN)]],
      confirmarPassword: ['', Validators.required],
    },
    { validators: passwordsCoincidenValidator },
  );

  passwordErrorMessage(): string | null {
    const control = this.form.controls.password;
    if (!control.touched || control.valid) return null;
    const key = control.hasError('required')
      ? 'empleados.form.errors.passwordRequired'
      : 'empleados.form.errors.passwordFormat';
    return this.translateService.instant(key);
  }

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token');
    if (!this.token) {
      this.enlaceInvalido.set(true);
    }
  }

  submit(): void {
    if (this.form.invalid || this.loading() || !this.token) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.restablecerPassword(this.token, this.form.getRawValue().password).subscribe({
      next: () => {
        this.notificationService.success(this.translateService.instant('restablecerPassword.exito'));
        this.router.navigateByUrl('/login');
      },
      error: (error: unknown) => {
        this.loading.set(false);
        const mensaje =
          error instanceof HttpErrorResponse ? (error.error as ApiError | null)?.message : null;
        this.errorMessage.set(mensaje ?? this.translateService.instant('restablecerPassword.errorGenerico'));
      },
    });
  }
}
