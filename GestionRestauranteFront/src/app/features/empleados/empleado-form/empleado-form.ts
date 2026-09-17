import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { EmpleadoService } from '../../../core/services/empleado.service';
import { NotificationService } from '../../../core/services/notification.service';
import { TipoUsuarioService } from '../../../core/services/tipo-usuario.service';
import { TipoUsuario } from '../../../core/models/empleado.model';
import { UiButton } from '../../../shared/ui/ui-button/ui-button';
import { UiCard } from '../../../shared/ui/ui-card/ui-card';
import { UiInput } from '../../../shared/ui/ui-input/ui-input';
import { UiSelect, UiSelectOption } from '../../../shared/ui/ui-select/ui-select';

@Component({
  selector: 'app-empleado-form',
  imports: [ReactiveFormsModule, RouterLink, UiCard, UiInput, UiSelect, UiButton, TranslatePipe],
  templateUrl: './empleado-form.html',
  styleUrl: './empleado-form.scss',
})
export class EmpleadoForm implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly empleadoService = inject(EmpleadoService);
  private readonly tipoUsuarioService = inject(TipoUsuarioService);
  private readonly notificationService = inject(NotificationService);
  private readonly translateService = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private empleadoId: number | null = null;

  readonly loading = signal(false);
  readonly tipoUsuarioOptions = signal<UiSelectOption[]>([]);
  readonly esEdicion = signal(false);

  readonly form = this.formBuilder.nonNullable.group({
    nombre: ['', Validators.required],
    apellidos: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    dniNie: ['', Validators.required],
    telefono: [''],
    puesto: [''],
    fechaAlta: [new Date().toISOString().substring(0, 10), Validators.required],
    tipoUsuarioCodigo: ['', Validators.required],
    password: [''],
  });

  ngOnInit(): void {
    this.tipoUsuarioService.listar().subscribe((tipos: TipoUsuario[]) => {
      this.tipoUsuarioOptions.set(tipos.map((t) => ({ value: t.codigo, label: t.nombre })));
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.empleadoId = Number(idParam);
      this.esEdicion.set(true);
      this.form.controls.password.clearValidators();

      this.loading.set(true);
      this.empleadoService.obtener(this.empleadoId).subscribe({
        next: (empleado) => {
          this.form.patchValue({
            nombre: empleado.nombre,
            apellidos: empleado.apellidos,
            email: empleado.email,
            dniNie: empleado.dniNie,
            telefono: empleado.telefono ?? '',
            puesto: empleado.puesto ?? '',
            fechaAlta: empleado.fechaAlta,
            tipoUsuarioCodigo: empleado.tipoUsuario.codigo,
          });
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
    } else {
      this.form.controls.password.addValidators([Validators.required, Validators.minLength(6)]);
    }
  }

  submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    this.loading.set(true);

    const request$ = this.esEdicion() && this.empleadoId !== null
      ? this.empleadoService.actualizar(this.empleadoId, value)
      : this.empleadoService.crear(value);

    request$.subscribe({
      next: () => {
        const key = this.esEdicion() ? 'empleados.notifications.updated' : 'empleados.notifications.created';
        this.notificationService.success(this.translateService.instant(key));
        this.router.navigateByUrl('/empleados');
      },
      error: () => this.loading.set(false),
    });
  }
}
