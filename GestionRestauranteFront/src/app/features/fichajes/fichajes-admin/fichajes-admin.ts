import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Observable, of, switchMap } from 'rxjs';
import { EmpleadoService } from '../../../core/services/empleado.service';
import { FichajeService } from '../../../core/services/fichaje.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Empleado } from '../../../core/models/empleado.model';
import { Fichaje, FichajeResumenDia, FichajeResumenMes, FichajeResumenSemana } from '../../../core/models/fichaje.model';
import { UiButton } from '../../../shared/ui/ui-button/ui-button';
import { UiCard } from '../../../shared/ui/ui-card/ui-card';
import { UiInput } from '../../../shared/ui/ui-input/ui-input';
import { UiModal } from '../../../shared/ui/ui-modal/ui-modal';
import { UiSelect, UiSelectOption } from '../../../shared/ui/ui-select/ui-select';
import { UiTable } from '../../../shared/ui/ui-table/ui-table';
import { horasATiempo, parsearHorasFlexible } from '../../../shared/utils/tiempo.util';

type Pestana = 'diario' | 'semanal' | 'mensual' | 'horarios';

const PAGE_SIZE_DETALLE_DIA = 100;
const HORAS_JORNADA_MINIMA = 1;
const HORAS_JORNADA_MAXIMA = 12;

/** Debe coincidir con app.fichajes.horas-jornada-default en el backend. */
const HORAS_JORNADA_DEFAULT = 8;

/** Acepta "8,5" (decimal) o "8:30" (horas:minutos); cualquier otro texto es invalido. */
function horasJornadaValidator(control: AbstractControl): ValidationErrors | null {
  const raw = (control.value ?? '').toString().trim();
  if (!raw) {
    return null;
  }
  const horas = parsearHorasFlexible(raw);
  if (horas === null) {
    return { formatoInvalido: true };
  }
  if (horas < HORAS_JORNADA_MINIMA || horas > HORAS_JORNADA_MAXIMA) {
    return { fueraDeRango: true };
  }
  return null;
}

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
  private readonly notificationService = inject(NotificationService);
  private readonly translateService = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  readonly horasJornadaDefault = HORAS_JORNADA_DEFAULT;
  readonly formatearHorasComoTiempo = horasATiempo;

  readonly pestana = signal<Pestana>('diario');
  readonly empleadoOptions = signal<UiSelectOption[]>([]);

  readonly filtros = this.formBuilder.nonNullable.group({
    empleadoId: ['', Validators.required],
    desde: [primerDiaDelMesActualISO(), Validators.required],
    hasta: [hoyISO(), Validators.required],
    anio: [String(new Date().getFullYear()), Validators.required],
  });

  readonly cargando = signal(false);
  readonly resumenDiario = signal<FichajeResumenDia[]>([]);
  readonly resumenSemanal = signal<FichajeResumenSemana[]>([]);
  readonly resumenMensual = signal<FichajeResumenMes[]>([]);
  readonly consultado = signal(false);

  readonly detalleDia = signal<{ fecha: string; fichajes: Fichaje[] } | null>(null);
  readonly cargandoDetalle = signal(false);

  readonly fichajeACorregir = signal<Fichaje | null>(null);
  readonly guardandoCorreccion = signal(false);
  readonly correccionForm = this.formBuilder.nonNullable.group({
    horaEntrada: ['', Validators.required],
    horaSalida: ['', Validators.required],
    observaciones: [''],
  });

  readonly horarioTeoricoForm = this.formBuilder.nonNullable.group({
    empleadoId: ['', Validators.required],
    horas: [String(HORAS_JORNADA_DEFAULT), [Validators.required, horasJornadaValidator]],
  });
  readonly cargandoHorarioTeorico = signal(false);
  readonly guardandoHorarioTeorico = signal(false);
  readonly horarioTeoricoEmpleado = signal<Empleado | null>(null);

  ngOnInit(): void {
    this.empleadoService.listar(0, 100).subscribe((pagina) => {
      this.empleadoOptions.set(pagina.content.map((e) => ({ value: String(e.id), label: `${e.nombre} ${e.apellidos}` })));
    });

    // switchMap descarta la respuesta de un empleado anterior si el Admin ya selecciono
    // otro antes de que llegara: sin esto, una respuesta desordenada (red lenta) podia dejar
    // el formulario mostrando el horario de un empleado distinto al seleccionado.
    this.horarioTeoricoForm.controls.empleadoId.valueChanges
      .pipe(
        switchMap((empleadoId): Observable<Empleado | null> => {
          if (!empleadoId) {
            return of(null);
          }
          this.cargandoHorarioTeorico.set(true);
          return this.empleadoService.obtener(Number(empleadoId));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (empleado) => {
          this.horarioTeoricoEmpleado.set(empleado);
          if (empleado) {
            this.horarioTeoricoForm.controls.horas.setValue(String(empleado.horasJornadaDiaria ?? HORAS_JORNADA_DEFAULT));
          }
          this.cargandoHorarioTeorico.set(false);
        },
        error: () => this.cargandoHorarioTeorico.set(false),
      });
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
        this.cargarDiario(empleadoId);
        break;
      case 'semanal':
        this.cargarSemanal(empleadoId);
        break;
      case 'mensual':
        this.cargarMensual(empleadoId);
        break;
    }
  }

  abrirDetalleDia(fecha: string): void {
    const empleadoId = Number(this.filtros.controls.empleadoId.value);
    this.cargandoDetalle.set(true);
    this.detalleDia.set({ fecha, fichajes: [] });
    this.fichajeService.listarComoAdmin({ empleadoId, desde: fecha, hasta: fecha }, 0, PAGE_SIZE_DETALLE_DIA).subscribe({
      next: (pagina) => {
        this.detalleDia.set({ fecha, fichajes: pagina.content });
        this.cargandoDetalle.set(false);
      },
      error: () => this.cargandoDetalle.set(false),
    });
  }

  cerrarDetalleDia(): void {
    this.detalleDia.set(null);
  }

  abrirCorreccion(fichaje: Fichaje): void {
    // Se cierra el detalle del día para no apilar dos modales a la vez.
    this.detalleDia.set(null);
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

  guardarHorarioTeorico(): void {
    if (this.horarioTeoricoForm.invalid) {
      this.horarioTeoricoForm.markAllAsTouched();
      return;
    }

    const { empleadoId, horas } = this.horarioTeoricoForm.getRawValue();
    const horasDecimal = parsearHorasFlexible(horas);
    if (horasDecimal === null) {
      return;
    }

    this.guardandoHorarioTeorico.set(true);
    this.empleadoService.actualizarHorarioTeorico(Number(empleadoId), horasDecimal).subscribe({
      next: (empleado) => {
        this.guardandoHorarioTeorico.set(false);
        this.horarioTeoricoEmpleado.set(empleado);
        this.notificationService.success(this.translateService.instant('fichajes.admin.horarios.exito'));
      },
      error: () => this.guardandoHorarioTeorico.set(false),
    });
  }

  horarioTeoricoHorasErrorMessage(): string | null {
    const control = this.horarioTeoricoForm.controls.horas;
    if (!control.touched || control.valid) {
      return null;
    }
    const key = control.hasError('required')
      ? 'fichajes.admin.horarios.horasRequerida'
      : control.hasError('fueraDeRango')
        ? 'fichajes.admin.horarios.horasFueraDeRango'
        : 'fichajes.admin.horarios.horasFormatoInvalido';
    return this.translateService.instant(key);
  }

  private cargarDiario(empleadoId: number): void {
    const { desde, hasta } = this.filtros.getRawValue();
    this.cargando.set(true);
    this.fichajeService.obtenerResumenDiario(empleadoId, desde, hasta).subscribe({
      next: (resumen) => {
        this.resumenDiario.set(resumen);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
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

}
