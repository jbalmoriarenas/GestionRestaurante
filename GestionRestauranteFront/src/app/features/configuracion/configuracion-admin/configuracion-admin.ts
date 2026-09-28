import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ConfiguracionUbicacion } from '../../../core/models/configuracion-ubicacion.model';
import { ConfiguracionService } from '../../../core/services/configuracion.service';
import { NotificationService } from '../../../core/services/notification.service';
import { UiButton } from '../../../shared/ui/ui-button/ui-button';
import { UiCard } from '../../../shared/ui/ui-card/ui-card';
import { UiFileInput } from '../../../shared/ui/ui-file-input/ui-file-input';
import { UiInput } from '../../../shared/ui/ui-input/ui-input';

const ACEPTA_LOGO = 'image/jpeg,image/png,image/webp';

@Component({
  selector: 'app-configuracion-admin',
  imports: [ReactiveFormsModule, UiCard, UiInput, UiButton, UiFileInput, TranslatePipe],
  templateUrl: './configuracion-admin.html',
  styleUrl: './configuracion-admin.scss',
})
export class ConfiguracionAdmin implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly configuracionService = inject(ConfiguracionService);
  private readonly notificationService = inject(NotificationService);
  private readonly translateService = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  readonly acceptaLogo = ACEPTA_LOGO;

  // ----- Datos de empresa -----
  readonly cargandoEmpresa = signal(true);
  readonly guardandoEmpresa = signal(false);
  readonly logoPreviewUrl = signal<string | null>(null);
  readonly empresaForm = this.formBuilder.nonNullable.group({
    nombreEmpresa: ['', Validators.required],
    // Opcional: en blanco, EmailServiceImpl usa spring.mail.username (la cuenta SMTP
    // autenticada) por defecto - ver back/fase5-1-back.md.
    emailRemitente: ['', Validators.email],
  });
  readonly logoControl = new FormControl<File | null>(null);

  // Object URLs creados por este componente (previsualizacion local o logo ya guardado
  // descargado como blob, front/fase5-1-front.md): se revocan al reemplazarlos o al destruir
  // el componente para no acumular memoria.
  private ultimaLogoObjectUrl: string | null = null;

  // ----- Ubicacion de fichaje -----
  readonly cargandoUbicacion = signal(true);
  readonly guardandoUbicacion = signal(false);
  readonly ubicacionActual = signal<ConfiguracionUbicacion | null>(null);
  readonly ubicacionForm = this.formBuilder.nonNullable.group({
    lat: ['', [Validators.required, Validators.min(-90), Validators.max(90)]],
    lng: ['', [Validators.required, Validators.min(-180), Validators.max(180)]],
    radioMetros: ['', [Validators.required, Validators.min(10), Validators.max(2000)]],
  });

  // Unica fuente de verdad de "sin configurar" (revision de codigo: antes se escribia a mano
  // en dos sitios, cargarUbicacion() y guardarUbicacion(), con riesgo de desincronizarse).
  readonly ubicacionSinConfigurar = computed(() => {
    const actual = this.ubicacionActual();
    return actual !== null && actual.lat === 0 && actual.lng === 0;
  });

  // ----- Vacaciones: dias anuales por defecto -----
  readonly cargandoVacaciones = signal(true);
  readonly guardandoVacaciones = signal(false);
  readonly vacacionesForm = this.formBuilder.nonNullable.group({
    diasAnualesDefecto: ['', [Validators.required, Validators.min(1), Validators.max(365)]],
  });

  ngOnInit(): void {
    this.cargarEmpresa();
    this.cargarUbicacion();
    this.cargarVacaciones();

    this.logoControl.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((archivo) => {
      if (archivo) {
        this.mostrarPreview(URL.createObjectURL(archivo));
      } else {
        // Sin archivo nuevo seleccionado: vuelve a mostrar el logo ya guardado (si hay uno).
        this.cargarLogoActual();
      }
    });

    this.destroyRef.onDestroy(() => {
      if (this.ultimaLogoObjectUrl) {
        URL.revokeObjectURL(this.ultimaLogoObjectUrl);
      }
    });
  }

  guardarEmpresa(): void {
    if (this.empresaForm.invalid) {
      this.empresaForm.markAllAsTouched();
      return;
    }
    const { nombreEmpresa, emailRemitente } = this.empresaForm.getRawValue();
    this.guardandoEmpresa.set(true);
    this.configuracionService.actualizarEmpresa(nombreEmpresa, this.logoControl.value, emailRemitente || null).subscribe({
      next: () => {
        this.guardandoEmpresa.set(false);
        this.logoControl.setValue(null);
        this.notificationService.success(this.translateService.instant('configuracion.admin.empresa.exito'));
      },
      error: () => this.guardandoEmpresa.set(false),
    });
  }

  guardarUbicacion(): void {
    if (this.ubicacionForm.invalid) {
      this.ubicacionForm.markAllAsTouched();
      return;
    }
    const raw = this.ubicacionForm.getRawValue();
    this.guardandoUbicacion.set(true);
    this.configuracionService
      .actualizarUbicacion({ lat: Number(raw.lat), lng: Number(raw.lng), radioMetros: Number(raw.radioMetros) })
      .subscribe({
        next: (ubicacion) => {
          this.guardandoUbicacion.set(false);
          this.ubicacionActual.set(ubicacion);
          this.notificationService.success(this.translateService.instant('configuracion.admin.ubicacion.exito'));
        },
        error: () => this.guardandoUbicacion.set(false),
      });
  }

  guardarVacaciones(): void {
    if (this.vacacionesForm.invalid) {
      this.vacacionesForm.markAllAsTouched();
      return;
    }
    const dias = Number(this.vacacionesForm.getRawValue().diasAnualesDefecto);
    this.guardandoVacaciones.set(true);
    this.configuracionService.actualizarVacaciones({ diasAnualesDefecto: dias }).subscribe({
      next: () => {
        this.guardandoVacaciones.set(false);
        this.notificationService.success(this.translateService.instant('configuracion.admin.vacaciones.exito'));
      },
      error: () => this.guardandoVacaciones.set(false),
    });
  }

  private cargarEmpresa(): void {
    this.cargandoEmpresa.set(true);
    this.configuracionService.obtenerEmpresa().subscribe({
      next: (empresa) => {
        this.empresaForm.controls.nombreEmpresa.setValue(empresa.nombreEmpresa);
        this.empresaForm.controls.emailRemitente.setValue(empresa.emailRemitente ?? '');
        this.cargandoEmpresa.set(false);
        if (empresa.logoUrl !== null) {
          this.cargarLogoActual();
        }
      },
      error: () => this.cargandoEmpresa.set(false),
    });
  }

  // Solo se llama cuando ya se sabe (por la respuesta de obtenerEmpresa()) que hay un logo
  // configurado: el endpoint devuelve 404 si no lo hay, y no tiene sentido pedirlo a ciegas.
  private cargarLogoActual(): void {
    this.configuracionService.obtenerLogo().subscribe((blob) => this.mostrarPreview(URL.createObjectURL(blob)));
  }

  private mostrarPreview(objectUrl: string): void {
    if (this.ultimaLogoObjectUrl) {
      URL.revokeObjectURL(this.ultimaLogoObjectUrl);
    }
    this.ultimaLogoObjectUrl = objectUrl;
    this.logoPreviewUrl.set(objectUrl);
  }

  private cargarUbicacion(): void {
    this.cargandoUbicacion.set(true);
    this.configuracionService.obtenerUbicacion().subscribe({
      next: (ubicacion) => {
        this.ubicacionForm.setValue({
          lat: String(ubicacion.lat),
          lng: String(ubicacion.lng),
          radioMetros: String(ubicacion.radioMetros),
        });
        this.ubicacionActual.set(ubicacion);
        this.cargandoUbicacion.set(false);
      },
      error: () => this.cargandoUbicacion.set(false),
    });
  }

  private cargarVacaciones(): void {
    this.cargandoVacaciones.set(true);
    this.configuracionService.obtenerVacaciones().subscribe({
      next: (configuracion) => {
        this.vacacionesForm.controls.diasAnualesDefecto.setValue(String(configuracion.diasAnualesDefecto));
        this.cargandoVacaciones.set(false);
      },
      error: () => this.cargandoVacaciones.set(false),
    });
  }
}
