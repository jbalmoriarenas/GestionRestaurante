import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  FormControl,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Observable, of, switchMap } from 'rxjs';
import { ConfiguracionService } from '../../../core/services/configuracion.service';
import { EmpleadoService } from '../../../core/services/empleado.service';
import { NotificationService } from '../../../core/services/notification.service';
import { VacacionesService } from '../../../core/services/vacaciones.service';
import { Empleado } from '../../../core/models/empleado.model';
import { Page } from '../../../core/models/page.model';
import {
  EstadoSolicitud,
  MOTIVOS_RECHAZO_VACACIONES,
  ResumenVacaciones,
  SolicitudVacaciones,
  SolicitudVacacionesAdmin,
} from '../../../core/models/vacaciones.model';
import { CalendarioMarcador, CalendarioVariante, UiCalendar } from '../../../shared/ui/ui-calendar/ui-calendar';
import { UiBadge } from '../../../shared/ui/ui-badge/ui-badge';
import { UiButton } from '../../../shared/ui/ui-button/ui-button';
import { UiCard } from '../../../shared/ui/ui-card/ui-card';
import { UiInput } from '../../../shared/ui/ui-input/ui-input';
import { UiModal } from '../../../shared/ui/ui-modal/ui-modal';
import { UiSelect, UiSelectOption } from '../../../shared/ui/ui-select/ui-select';
import { UiTable } from '../../../shared/ui/ui-table/ui-table';

type Pestana = 'pendientes' | 'porEmpleado' | 'configuracion';

const PAGE_SIZE = 20;

/** Sentinel para "sin filtro" en el ui-select de estado: no puede ser '' porque ui-select
 * reserva ese valor para su placeholder deshabilitado (ver ui-select.html). */
const FILTRO_ESTADO_TODOS = 'TODOS';

/** Solo el valor inicial hasta que responda obtenerVacaciones(); debe coincidir con el
 * seed de configuracion_vacaciones (V20) para que no haya parpadeo visual. */
const DIAS_ANUALES_INICIAL = 23;

/** Acepta un entero de dias (vacio = sin cambios, vuelve al valor global al guardar). El
 * techo (no superar el valor global) es dinamico, por eso se recibe como funcion en vez de
 * ser un Validators.max fijo. */
function crearValidadorDiasVacaciones(obtenerTecho: () => number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const raw = (control.value ?? '').toString().trim();
    if (!raw) {
      return null;
    }
    if (!/^\d+$/.test(raw) || Number(raw) < 1) {
      return { formatoInvalido: true };
    }
    if (Number(raw) > obtenerTecho()) {
      return { fueraDeRango: true };
    }
    return null;
  };
}

@Component({
  selector: 'app-vacaciones-admin',
  imports: [ReactiveFormsModule, RouterLink, UiCard, UiInput, UiSelect, UiButton, UiTable, UiModal, UiBadge, UiCalendar, TranslatePipe],
  templateUrl: './vacaciones-admin.html',
  styleUrl: './vacaciones-admin.scss',
})
export class VacacionesAdmin implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly vacacionesService = inject(VacacionesService);
  private readonly empleadoService = inject(EmpleadoService);
  private readonly configuracionService = inject(ConfiguracionService);
  private readonly notificationService = inject(NotificationService);
  private readonly translateService = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  readonly pestana = signal<Pestana>('pendientes');
  readonly empleadoOptions = signal<UiSelectOption[]>([]);

  // Pendientes (bandeja global)
  readonly cargandoPendientes = signal(false);
  readonly pagePendientes = signal<Page<SolicitudVacacionesAdmin> | null>(null);

  // Por empleado
  readonly porEmpleadoForm = this.formBuilder.nonNullable.group({ empleadoId: ['', Validators.required] });
  readonly mesMostrado = signal(new Date().getMonth() + 1);
  readonly anioMostrado = signal(new Date().getFullYear());
  readonly cargandoResumenEmpleado = signal(false);
  readonly cargandoCalendarioEmpleado = signal(false);
  readonly cargandoSolicitudesEmpleado = signal(false);
  readonly resumenEmpleado = signal<ResumenVacaciones | null>(null);
  readonly marcadoresCalendarioEmpleado = signal<CalendarioMarcador[]>([]);
  readonly pageSolicitudesEmpleado = signal<Page<SolicitudVacaciones> | null>(null);
  readonly filtroEstado = signal<EstadoSolicitud | ''>('');
  readonly filtroEstadoControl = new FormControl(FILTRO_ESTADO_TODOS, { nonNullable: true });

  readonly filtroEstadoOptions = computed<UiSelectOption[]>(() => {
    this.translateService.currentLang();
    return [
      { value: FILTRO_ESTADO_TODOS, label: this.translateService.instant('vacaciones.admin.porEmpleado.solicitudes.todos') },
      { value: 'PENDIENTE', label: this.translateService.instant('vacaciones.misVacaciones.solicitudes.estados.PENDIENTE') },
      { value: 'APROBADA', label: this.translateService.instant('vacaciones.misVacaciones.solicitudes.estados.APROBADA') },
      { value: 'RECHAZADA', label: this.translateService.instant('vacaciones.misVacaciones.solicitudes.estados.RECHAZADA') },
    ];
  });

  readonly tituloMesEmpleado = computed(() => {
    const fecha = new Date(this.anioMostrado(), this.mesMostrado() - 1, 1);
    const locale = this.translateService.currentLang() === 'en' ? 'en-GB' : 'es-ES';
    const mes = new Intl.DateTimeFormat(locale, { month: 'long' }).format(fecha);
    return `${mes} ${fecha.getFullYear()}`;
  });

  // Rechazar: modal compartido entre "Pendientes" y "Por empleado"
  readonly solicitudARechazar = signal<number | null>(null);
  readonly rechazoForm = this.formBuilder.nonNullable.group({ motivo: ['', Validators.required] });
  readonly guardandoRechazo = signal(false);

  // Se recalcula al cambiar de idioma (currentLang() como dependencia reactiva del computed),
  // igual que tituloMesEmpleado. Los value deben coincidir con el @Pattern del backend.
  readonly motivoRechazoOptions = computed<UiSelectOption[]>(() => {
    this.translateService.currentLang();
    return MOTIVOS_RECHAZO_VACACIONES.map((codigo) => ({
      value: codigo,
      label: this.translateService.instant(`vacaciones.motivosRechazo.${codigo}`),
    }));
  });

  // Asignacion de oficio
  readonly mostrarAsignarOficio = signal(false);
  readonly asignarOficioForm = this.formBuilder.nonNullable.group({
    fechaInicio: ['', Validators.required],
    fechaFin: ['', Validators.required],
  });
  readonly guardandoAsignacion = signal(false);

  // Valor global de dias anuales por defecto: solo lectura aqui (se edita desde /configuracion,
  // front/fase5-1-front.md), se necesita como techo dinamico para validar el override de abajo.
  readonly diasAnualesGlobal = signal(DIAS_ANUALES_INICIAL);

  // Configuracion: sobrescritura por empleado
  readonly overrideForm = this.formBuilder.nonNullable.group({
    empleadoId: ['', Validators.required],
    dias: ['', crearValidadorDiasVacaciones(() => this.diasAnualesGlobal())],
  });
  readonly cargandoOverride = signal(false);
  readonly guardandoOverride = signal(false);
  readonly empleadoOverride = signal<Empleado | null>(null);
  readonly resumenOverride = signal<ResumenVacaciones | null>(null);

  ngOnInit(): void {
    this.empleadoService.listar(0, 100).subscribe((pagina) => {
      this.empleadoOptions.set(pagina.content.map((e) => ({ value: String(e.id), label: `${e.nombre} ${e.apellidos}` })));
    });
    this.cargarPendientes(0);
    this.cargarConfiguracionGlobal();

    this.porEmpleadoForm.controls.empleadoId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((empleadoId) => {
        if (empleadoId) {
          this.cargarDetalleEmpleado(Number(empleadoId), this.anioMostrado());
        } else {
          this.limpiarDetalleEmpleado();
        }
      });

    this.filtroEstadoControl.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((valor) => {
      this.cambiarFiltroEstado(valor === FILTRO_ESTADO_TODOS ? '' : valor);
    });

    // switchMap descarta la respuesta de un empleado anterior si el Admin ya selecciono
    // otro antes de que llegara (mismo fix aplicado en fichajes-admin.ts, fase1-3-front.md).
    this.overrideForm.controls.empleadoId.valueChanges
      .pipe(
        switchMap((empleadoId): Observable<Empleado | null> => {
          if (!empleadoId) {
            return of(null);
          }
          this.cargandoOverride.set(true);
          return this.empleadoService.obtener(Number(empleadoId));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (empleado) => {
          this.empleadoOverride.set(empleado);
          if (empleado) {
            this.overrideForm.controls.dias.setValue(
              empleado.diasVacacionesAnuales !== null ? String(empleado.diasVacacionesAnuales) : '',
            );
            this.cargarResumenOverride(empleado.id);
          } else {
            this.resumenOverride.set(null);
          }
          this.cargandoOverride.set(false);
        },
        error: () => this.cargandoOverride.set(false),
      });
  }

  cambiarPestana(pestana: Pestana): void {
    this.pestana.set(pestana);
  }

  // ----- Pendientes -----

  cambiarPaginaPendientes(page: number): void {
    this.cargarPendientes(page);
  }

  aprobarDesdePendientes(id: number): void {
    this.vacacionesService.aprobar(id).subscribe({
      next: () => {
        this.notificationService.success(this.translateService.instant('vacaciones.admin.notificaciones.aprobada'));
        this.cargarPendientes(this.pagePendientes()?.number ?? 0);
        this.refrescarEmpleadoSeleccionadoSiHayUno();
      },
    });
  }

  // ----- Rechazar (compartido) -----

  abrirRechazo(id: number): void {
    this.solicitudARechazar.set(id);
    this.rechazoForm.reset();
  }

  cerrarRechazo(): void {
    this.solicitudARechazar.set(null);
  }

  guardarRechazo(): void {
    const id = this.solicitudARechazar();
    if (id === null || this.rechazoForm.invalid) {
      this.rechazoForm.markAllAsTouched();
      return;
    }
    this.guardandoRechazo.set(true);
    this.vacacionesService.rechazar(id, this.rechazoForm.getRawValue().motivo).subscribe({
      next: () => {
        this.guardandoRechazo.set(false);
        this.solicitudARechazar.set(null);
        this.notificationService.success(this.translateService.instant('vacaciones.admin.notificaciones.rechazada'));
        this.cargarPendientes(this.pagePendientes()?.number ?? 0);
        this.refrescarEmpleadoSeleccionadoSiHayUno();
      },
      error: () => this.guardandoRechazo.set(false),
    });
  }

  // ----- Por empleado -----

  irAMesAnteriorEmpleado(): void {
    this.navegarMesEmpleado(-1);
  }

  irAMesSiguienteEmpleado(): void {
    this.navegarMesEmpleado(1);
  }

  cambiarFiltroEstado(estado: string): void {
    this.filtroEstado.set(estado as EstadoSolicitud | '');
    const empleadoId = this.empleadoSeleccionadoId();
    if (empleadoId) {
      this.cargarSolicitudesEmpleado(empleadoId, 0);
    }
  }

  cambiarPaginaSolicitudesEmpleado(page: number): void {
    const empleadoId = this.empleadoSeleccionadoId();
    if (empleadoId) {
      this.cargarSolicitudesEmpleado(empleadoId, page);
    }
  }

  aprobarSolicitudEmpleado(id: number): void {
    this.vacacionesService.aprobar(id).subscribe({
      next: () => {
        this.notificationService.success(this.translateService.instant('vacaciones.admin.notificaciones.aprobada'));
        this.refrescarEmpleadoSeleccionadoSiHayUno();
        this.cargarPendientes(this.pagePendientes()?.number ?? 0);
      },
    });
  }

  abrirAsignarOficio(): void {
    this.asignarOficioForm.reset();
    this.mostrarAsignarOficio.set(true);
  }

  cerrarAsignarOficio(): void {
    this.mostrarAsignarOficio.set(false);
  }

  guardarAsignacionOficio(): void {
    const empleadoId = this.empleadoSeleccionadoId();
    if (!empleadoId || this.asignarOficioForm.invalid) {
      this.asignarOficioForm.markAllAsTouched();
      return;
    }
    const { fechaInicio, fechaFin } = this.asignarOficioForm.getRawValue();
    this.guardandoAsignacion.set(true);
    this.vacacionesService.asignarDeOficio({ empleadoId, fechaInicio, fechaFin }).subscribe({
      next: () => {
        this.guardandoAsignacion.set(false);
        this.mostrarAsignarOficio.set(false);
        this.notificationService.success(this.translateService.instant('vacaciones.admin.notificaciones.asignada'));
        this.cargarDetalleEmpleado(empleadoId, this.anioMostrado());
      },
      // Si el backend responde 409 (cupo insuficiente), el toast del error ya lo muestra el
      // errorInterceptor global; el modal se mantiene abierto para poder ajustar el rango.
      error: () => this.guardandoAsignacion.set(false),
    });
  }

  // ----- Configuracion: sobrescritura por empleado -----

  guardarOverride(): void {
    if (this.overrideForm.invalid) {
      this.overrideForm.markAllAsTouched();
      return;
    }
    const { empleadoId, dias } = this.overrideForm.getRawValue();
    const diasNumero = dias.trim() === '' ? null : Number(dias);
    this.guardandoOverride.set(true);
    this.empleadoService.actualizarDiasVacacionesAnuales(Number(empleadoId), diasNumero).subscribe({
      next: (empleado) => {
        this.guardandoOverride.set(false);
        this.empleadoOverride.set(empleado);
        this.notificationService.success(this.translateService.instant('vacaciones.admin.notificaciones.overrideGuardado'));
        this.cargarResumenOverride(empleado.id);
      },
      error: () => this.guardandoOverride.set(false),
    });
  }

  overrideDiasErrorMessage(): string | null {
    const control = this.overrideForm.controls.dias;
    if (!control.touched || control.valid) {
      return null;
    }
    const key = control.hasError('fueraDeRango')
      ? 'vacaciones.admin.configuracion.override.fueraDeRango'
      : 'vacaciones.admin.configuracion.override.formatoInvalido';
    return this.translateService.instant(key, { defecto: this.diasAnualesGlobal() });
  }

  private cargarPendientes(page: number): void {
    this.cargandoPendientes.set(true);
    this.vacacionesService.listarTodasPendientes(page, PAGE_SIZE).subscribe({
      next: (pagina) => {
        this.pagePendientes.set(pagina);
        this.cargandoPendientes.set(false);
      },
      error: () => this.cargandoPendientes.set(false),
    });
  }

  private navegarMesEmpleado(delta: number): void {
    let mes = this.mesMostrado() + delta;
    let anio = this.anioMostrado();
    if (mes > 12) {
      mes = 1;
      anio += 1;
    } else if (mes < 1) {
      mes = 12;
      anio -= 1;
    }
    this.mesMostrado.set(mes);
    if (anio !== this.anioMostrado()) {
      this.anioMostrado.set(anio);
      const empleadoId = this.empleadoSeleccionadoId();
      if (empleadoId) {
        this.cargarDetalleEmpleado(empleadoId, anio);
      }
    }
  }

  private cargarDetalleEmpleado(empleadoId: number, anio: number): void {
    this.cargandoResumenEmpleado.set(true);
    this.vacacionesService.obtenerResumenComoAdmin(empleadoId, anio).subscribe({
      next: (resumen) => {
        // Descarta la respuesta si el Admin ya cambio de empleado antes de que llegara.
        if (this.empleadoSeleccionadoId() === empleadoId) {
          this.resumenEmpleado.set(resumen);
        }
        this.cargandoResumenEmpleado.set(false);
      },
      error: () => this.cargandoResumenEmpleado.set(false),
    });

    this.cargandoCalendarioEmpleado.set(true);
    this.vacacionesService.obtenerCalendarioComoAdmin(empleadoId, anio).subscribe({
      next: (dias) => {
        if (this.empleadoSeleccionadoId() === empleadoId) {
          this.marcadoresCalendarioEmpleado.set(dias.map((dia) => ({ fecha: dia.fecha, variante: this.aVariante(dia.tipo) })));
        }
        this.cargandoCalendarioEmpleado.set(false);
      },
      error: () => this.cargandoCalendarioEmpleado.set(false),
    });

    this.cargarSolicitudesEmpleado(empleadoId, 0);
  }

  private cargarSolicitudesEmpleado(empleadoId: number, page: number): void {
    this.cargandoSolicitudesEmpleado.set(true);
    const estado = this.filtroEstado() || null;
    this.vacacionesService.listarSolicitudesComoAdmin(empleadoId, estado, page, PAGE_SIZE).subscribe({
      next: (pagina) => {
        if (this.empleadoSeleccionadoId() === empleadoId) {
          this.pageSolicitudesEmpleado.set(pagina);
        }
        this.cargandoSolicitudesEmpleado.set(false);
      },
      error: () => this.cargandoSolicitudesEmpleado.set(false),
    });
  }

  private limpiarDetalleEmpleado(): void {
    this.resumenEmpleado.set(null);
    this.marcadoresCalendarioEmpleado.set([]);
    this.pageSolicitudesEmpleado.set(null);
  }

  private empleadoSeleccionadoId(): number | null {
    const value = this.porEmpleadoForm.controls.empleadoId.value;
    return value ? Number(value) : null;
  }

  private refrescarEmpleadoSeleccionadoSiHayUno(): void {
    const empleadoId = this.empleadoSeleccionadoId();
    if (empleadoId) {
      this.cargarDetalleEmpleado(empleadoId, this.anioMostrado());
    }
  }

  private cargarConfiguracionGlobal(): void {
    this.configuracionService.obtenerVacaciones().subscribe((configuracion) => {
      this.diasAnualesGlobal.set(configuracion.diasAnualesDefecto);
    });
  }

  private cargarResumenOverride(empleadoId: number): void {
    this.vacacionesService.obtenerResumenComoAdmin(empleadoId, new Date().getFullYear()).subscribe((resumen) => {
      if (this.empleadoOverride()?.id === empleadoId) {
        this.resumenOverride.set(resumen);
      }
    });
  }

  private aVariante(tipo: 'FESTIVO' | 'APROBADO' | 'PENDIENTE'): CalendarioVariante {
    switch (tipo) {
      case 'FESTIVO':
        return 'festivo';
      case 'APROBADO':
        return 'aprobado';
      case 'PENDIENTE':
        return 'pendiente';
    }
  }
}
