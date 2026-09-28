import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ApiError } from '../../../core/models/api-error.model';
import { AuthService } from '../../../core/services/auth.service';
import { Footer } from '../../../layout/footer/footer';
import { UiButton } from '../../../shared/ui/ui-button/ui-button';
import { UiInput } from '../../../shared/ui/ui-input/ui-input';
import { UiModal } from '../../../shared/ui/ui-modal/ui-modal';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, UiInput, UiButton, UiModal, Footer, TranslatePipe],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly translateService = inject(TranslateService);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  readonly mostrarModalRecuperacion = signal(false);
  readonly enviandoRecuperacion = signal(false);
  readonly recuperacionEnviada = signal(false);
  readonly errorRecuperacion = signal<string | null>(null);

  readonly formRecuperacion = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  submit(): void {
    if (this.form.invalid || this.loading()) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.login(this.form.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: (error: unknown) => {
        this.loading.set(false);
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.errorMessage.set(this.translateService.instant('login.invalidCredentials'));
        } else {
          this.errorMessage.set(this.translateService.instant('login.genericError'));
        }
      },
    });
  }

  abrirModalRecuperacion(): void {
    this.recuperacionEnviada.set(false);
    this.errorRecuperacion.set(null);
    this.formRecuperacion.reset();
    this.mostrarModalRecuperacion.set(true);
  }

  cerrarModalRecuperacion(): void {
    this.mostrarModalRecuperacion.set(false);
  }

  enviarRecuperacion(): void {
    if (this.formRecuperacion.invalid || this.enviandoRecuperacion()) {
      return;
    }

    this.enviandoRecuperacion.set(true);
    this.errorRecuperacion.set(null);

    const { email } = this.formRecuperacion.getRawValue();
    this.authService.solicitarRecuperacion(email).subscribe({
      next: () => {
        this.enviandoRecuperacion.set(false);
        this.recuperacionEnviada.set(true);
      },
      error: (error: unknown) => {
        this.enviandoRecuperacion.set(false);
        const mensaje =
          error instanceof HttpErrorResponse ? (error.error as ApiError | null)?.message : null;
        this.errorRecuperacion.set(mensaje ?? this.translateService.instant('login.genericError'));
      },
    });
  }
}
