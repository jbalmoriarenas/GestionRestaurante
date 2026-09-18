import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ConfiguracionService } from '../../../core/services/configuracion.service';
import { EmpleadoService } from '../../../core/services/empleado.service';
import { FichajeService } from '../../../core/services/fichaje.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ConfiguracionUbicacion } from '../../../core/models/configuracion-ubicacion.model';
import { Fichaje, FichajeResumenMes, FichajeResumenSemana } from '../../../core/models/fichaje.model';
import { Page } from '../../../core/models/page.model';
import { UiButton } from '../../../shared/ui/ui-button/ui-button';
import { UiCard } from '../../../shared/ui/ui-card/ui-card';
import { UiInput } from '../../../shared/ui/ui-input/ui-input';
import { UiModal } from '../../../shared/ui/ui-modal/ui-modal';
import { UiSelect, UiSelectOption } from '../../../shared/ui/ui-select/ui-select';
import { UiTable } from '../../../shared/ui/ui-table/ui-table';

type Pestana = 'diario' | 'semanal' | 'mensual' | 'ubicacion';

const PAGE_SIZE = 20;

function hoyISO(): string {
  return new Date().toISOString().substring(0, 10);
}

function primerDiaDelMesActualISO(): string {
  const hoy = new Date();
  return new Date(hoy.getFullYear(), hoy.getMonth(), 1).toISOString().substring(0, 10);
}

@Component({
  selector: 'app-fichajes-admin',
  imports: [ReactiveFormsModule, UiCard, UiInput, UiSelect, UiButton, UiTable, UiModal, TranslatePipe],
  templateUrl: './fichajes-admin.html',
  styleUrl: './fichajes-admin.scss',
})
export class FichajesAdmin implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly fichajeService = inject(FichajeService);
  private readonly empleadoService = inject(EmpleadoService);
  private readonly configuracionService = inject(ConfiguracionService);
  private readonly notificationService = inject(NotificationService);
  private readonly translateService = inject(TranslateService);

  readonly pestana = signal<Pestana>('diario');
  readonly empleadoOptions = signal<UiSelectOption[]>([]);

  readonly filtros = this.formBuilder.nonNullable.group({
    empleadoId: ['', Validators.required],
    desde: [primerDiaDelMesActualISO(), Validators.required],
    hasta: [hoyISO(), Validators.required],
    anio: [String(new Date().getFullYear()), Validators.required],
  });

  readonly cargando = signal(false);
  readonly pageFichajes = signal<Page<Fichaje> | null>(null);
  private readonly diasSinMinimo = signal<ReadonlySet<string>>(new Set());
  readonly resumenSemanal = signal<FichajeResumenSemana[]>([]);
  readonly resumenMensual = signal<FichajeResumenMes[]>([]);
  readonly consultado = signal(false);

  readonly fichajeACorregir = signal<Fichaje | null>(null);
  readonly guardandoCorreccion = signal(false);
  readonly correccionForm = this.formBuilder.nonNullable.group({
    horaEntrada: ['', Validators.required],
    horaSalida: ['', Validators.required],
    observaciones: [''],
  });

  readonly cargandoUbicacion = signal(true);
  readonly guardandoUbicacion = signal(false);
  readonly ubicacionActual = signal<ConfiguracionUbicacion | null>(null);
  readonly ubicacionForm = this.formBuilder.nonNullable.group({
    lat: ['', [Validators.required, Validators.min(-90), Validators.max(90)]],
    lng: ['', [Validators.required, Validators.min(-180), Validators.max(180)]],
    radioMetros: ['', [Validators.required, Validators.min(10), Validators.max(2000)]],
  });

  readonly ubicacionSinConfigurar = computed(() => {
    const actual = this.ubicacionActual();
    return actual !== null && actual.lat === 0 && actual.lng === 0;
  });

  ngOnInit(): void {
    this.empleadoService.listar(0, 100).subscribe((pagina) => {
      this.empleadoOptions.set(pagina.content.map((e) => ({ value: String(e.id), label: `${e.nombre} ${e.apellidos}` })));
    });
    this.cargarUbicacion();
  }

  cambiarPestana(pestana: Pestana): void {
    this.pestana.set(pestana);
    if (this.consultado()) {
      this.consultar();
    }
  }

  consultar(): void {
    if (this.filtros.invalid) {
      this.filtros.markAllAsTouched();
      return;
    }
    this.consultado.set(true);
    const empleadoId = Number(this.filtros.controls.empleadoId.value);
    switch (this.pestana()) {
      case 'diario':
        this.cargarDiario(empleadoId, 0);
        break;
      case 'semanal':
        this.cargarSemanal(empleadoId);
        break;
      case 'mensual':
        this.cargarMensual(empleadoId);
        break;
    }
  }

  cambiarPaginaDiario(page: number): void {
    this.cargarDiario(Number(this.filtros.controls.empleadoId.value), page);
  }

  esDiaPorDebajoDelMinimo(fecha: string): boolean {
    return this.diasSinMinimo().has(fecha);
  }

  abrirCorreccion(fichaje: Fichaje): void {
    this.fichajeACorregir.set(fichaje);
    this.correccionForm.setValue({
      horaEntrada: fichaje.horaEntrada.substring(0, 16),
      horaSalida: fichaje.horaSalida ? fichaje.horaSalida.substring(0, 16) : '',
      observaciones: fichaje.observaciones ?? '',
    });
  }

  cerrarCorreccion(): void {
    this.fichajeACorregir.set(null);
  }

  guardarCorreccion(): void {
    const fichaje = this.fichajeACorregir();
    if (!fichaje || this.correccionForm.invalid) {
      this.correccionForm.markAllAsTouched();
      return;
    }

    const { horaEntrada, horaSalida, observaciones } = this.correccionForm.getRawValue();
    this.guardandoCorreccion.set(true);
    this.fichajeService.corregir(fichaje.id, { horaEntrada, horaSalida, observaciones: observaciones || null }).subscribe({
      next: () => {
        this.guardandoCorreccion.set(false);
        this.fichajeACorregir.set(null);
        this.notificationService.success(this.translateService.instant('fichajes.admin.correccion.exito'));
        this.consultar();
      },
      // El toast del error ya lo muestra el errorInterceptor global; el modal se mantiene
      // abierto con los datos introducidos para que el Admin no tenga que rehacerlos.
      error: () => this.guardandoCorreccion.set(false),
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
          this.notificationService.success(this.translateService.instant('fichajes.admin.ubicacion.exito'));
        },
        error: () => this.guardandoUbicacion.set(false),
      });
  }

  private cargarDiario(empleadoId: number, page: number): void {
    const { desde, hasta } = this.filtros.getRawValue();
    this.cargando.set(true);
    this.fichajeService.listarComoAdmin({ empleadoId, desde, hasta }, page, PAGE_SIZE).subscribe({
      next: (pagina) => {
        this.pageFichajes.set(pagina);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
    this.fichajeService.obtenerResumenDiario(empleadoId, desde, hasta).subscribe((resumen) => {
      this.diasSinMinimo.set(new Set(resumen.filter((dia) => !dia.cumpleMinimo).map((dia) => dia.fecha)));
    });
  }

  private cargarSemanal(empleadoId: number): void {
    const { desde, hasta } = this.filtros.getRawValue();
    this.cargando.set(true);
    this.fichajeService.obtenerResumenSemanal(empleadoId, desde, hasta).subscribe({
      next: (resumen) => {
        this.resumenSemanal.set(resumen);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  private cargarMensual(empleadoId: number): void {
    const anio = Number(this.filtros.controls.anio.value);
    this.cargando.set(true);
    this.fichajeService.obtenerResumenMensual(empleadoId, anio).subscribe({
      next: (resumen) => {
        this.resumenMensual.set(resumen);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  private cargarUbicacion(): void {
    this.cargandoUbicacion.set(true);
    this.configuracionService.obtenerUbicacion().subscribe({
      next: (ubicacion) => {
        this.ubicacionActual.set(ubicacion);
        this.ubicacionForm.setValue({
          lat: String(ubicacion.lat),
          lng: String(ubicacion.lng),
          radioMetros: String(ubicacion.radioMetros),
        });
        this.cargandoUbicacion.set(false);
      },
      error: () => this.cargandoUbicacion.set(false),
    });
  }
}
