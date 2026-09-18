import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { VacacionesService } from '../../../core/services/vacaciones.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Page } from '../../../core/models/page.model';
import { ResumenVacaciones, SolicitudVacaciones } from '../../../core/models/vacaciones.model';
import { CalendarioMarcador, UiCalendar } from '../../../shared/ui/ui-calendar/ui-calendar';
import { UiBadge } from '../../../shared/ui/ui-badge/ui-badge';
import { UiButton } from '../../../shared/ui/ui-button/ui-button';
import { UiCard } from '../../../shared/ui/ui-card/ui-card';
import { UiInput } from '../../../shared/ui/ui-input/ui-input';
import { UiModal } from '../../../shared/ui/ui-modal/ui-modal';
import { UiTable } from '../../../shared/ui/ui-table/ui-table';

const PAGE_SIZE = 10;
const RESUMEN_VACIO: ResumenVacaciones = { total: 0, aprobados: 0, pendientes: 0, disponibles: 0 };

function fechaFinNoAnteriorAInicio(group: AbstractControl): ValidationErrors | null {
  const inicio: string = group.get('fechaInicio')?.value;
  const fin: string = group.get('fechaFin')?.value;
  if (!inicio || !fin) return null;
  if (fin < inicio) return { rangoInvalido: true };
  // Comparacion por prefijo de año (yyyy-MM-dd): mismo criterio que back/fase1-2-back.md,
  // que rechaza un rango que cruce el año natural para no tener que prorratear el cupo
  // entre dos años distintos.
  if (inicio.slice(0, 4) !== fin.slice(0, 4)) return { distintoAnio: true };
  return null;
}

@Component({
  selector: 'app-mis-vacaciones',
  imports: [ReactiveFormsModule, UiCard, UiCalendar, UiInput, UiButton, UiTable, UiBadge, UiModal, TranslatePipe],
  templateUrl: './mis-vacaciones.html',
  styleUrl: './mis-vacaciones.scss',
})
export class MisVacaciones implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly vacacionesService = inject(VacacionesService);
  private readonly notificationService = inject(NotificationService);
  private readonly translateService = inject(TranslateService);

  readonly mesMostrado = signal(new Date().getMonth() + 1);
  readonly anioMostrado = signal(new Date().getFullYear());

  readonly resumen = signal<ResumenVacaciones>(RESUMEN_VACIO);
  readonly cargandoResumen = signal(true);

  readonly marcadoresCalendario = signal<CalendarioMarcador[]>([]);
  readonly cargandoCalendario = signal(true);

  readonly pageSolicitudes = signal<Page<SolicitudVacaciones> | null>(null);
  readonly cargandoSolicitudes = signal(true);

  readonly enviandoSolicitud = signal(false);
  readonly solicitudACancelar = signal<SolicitudVacaciones | null>(null);

  readonly form = this.formBuilder.nonNullable.group(
    {
      fechaInicio: ['', Validators.required],
      fechaFin: ['', Validators.required],
    },
    { validators: fechaFinNoAnteriorAInicio },
  );

  ngOnInit(): void {
    this.cargarResumenYCalendario(this.anioMostrado());
    this.cargarSolicitudes(0);
  }

  readonly tituloMes = computed(() => {
    const fecha = new Date(this.anioMostrado(), this.mesMostrado() - 1, 1);
    const locale = this.translateService.currentLang() === 'en' ? 'en-GB' : 'es-ES';
    // Intl con { month: 'long', year: 'numeric' } añade un conector ("septiembre de 2026")
    // que aqui no se quiere; formateando mes y año por separado se evita sin tener que
    // parchear el string resultante.
    const mes = new Intl.DateTimeFormat(locale, { month: 'long' }).format(fecha);
    return `${mes} ${fecha.getFullYear()}`;
  });

  irAMesAnterior(): void {
    this.navegarMes(-1);
  }

  irAMesSiguiente(): void {
    this.navegarMes(1);
  }

  rangoErrorMessage(): string | null {
    const fin = this.form.controls.fechaFin;
    if (!fin.touched) return null;
    if (this.form.hasError('rangoInvalido')) {
      return this.translateService.instant('vacaciones.misVacaciones.form.errores.rangoInvalido');
    }
    if (this.form.hasError('distintoAnio')) {
      return this.translateService.instant('vacaciones.misVacaciones.form.errores.distintoAnio');
    }
    return null;
  }

  // Restringe el propio selector de fecha del navegador al año natural de fechaInicio,
  // para que no se puedan ni seleccionar fechas fuera de ese año (ver back/fase1-2-back.md).
  fechaFinMin(): string {
    return this.form.controls.fechaInicio.value;
  }

  fechaFinMax(): string {
    const inicio = this.form.controls.fechaInicio.value;
    return inicio ? `${inicio.slice(0, 4)}-12-31` : '';
  }

  solicitar(): void {
    if (this.form.invalid || this.enviandoSolicitud()) {
      this.form.markAllAsTouched();
      return;
    }

    this.enviandoSolicitud.set(true);
    this.vacacionesService.solicitar(this.form.getRawValue()).subscribe({
      next: () => {
        this.enviandoSolicitud.set(false);
        this.form.reset();
        this.notificationService.success(this.translateService.instant('vacaciones.misVacaciones.solicitudCreada'));
        this.refrescarTodo();
      },
      // El toast del error (409 por dias insuficientes, etc.) ya lo muestra el
      // errorInterceptor global; el formulario mantiene las fechas introducidas para que
      // el empleado no tenga que volver a escribirlas.
      error: () => this.enviandoSolicitud.set(false),
    });
  }

  confirmarCancelacion(solicitud: SolicitudVacaciones): void {
    this.solicitudACancelar.set(solicitud);
  }

  cerrarModalCancelacion(): void {
    this.solicitudACancelar.set(null);
  }

  cancelar(): void {
    const solicitud = this.solicitudACancelar();
    if (!solicitud) {
      return;
    }
    this.vacacionesService.cancelar(solicitud.id).subscribe({
      next: () => {
        this.solicitudACancelar.set(null);
        this.notificationService.success(this.translateService.instant('vacaciones.misVacaciones.solicitudCancelada'));
        this.refrescarTodo();
      },
    });
  }

  private navegarMes(delta: number): void {
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
      this.cargarResumenYCalendario(anio);
    }
  }

  private cargarResumenYCalendario(anio: number): void {
    this.cargandoResumen.set(true);
    this.vacacionesService.obtenerResumen(anio).subscribe({
      next: (resumen) => {
        this.resumen.set(resumen);
        this.cargandoResumen.set(false);
      },
      error: () => this.cargandoResumen.set(false),
    });

    this.cargandoCalendario.set(true);
    this.vacacionesService.obtenerCalendario(anio).subscribe({
      next: (dias) => {
        this.marcadoresCalendario.set(
          dias.map((dia) => ({ fecha: dia.fecha, variante: this.aVariante(dia.tipo) })),
        );
        this.cargandoCalendario.set(false);
      },
      error: () => this.cargandoCalendario.set(false),
    });
  }

  private cargarSolicitudes(page: number): void {
    this.cargandoSolicitudes.set(true);
    this.vacacionesService.listarMisSolicitudes(page, PAGE_SIZE).subscribe({
      next: (data) => {
        this.pageSolicitudes.set(data);
        this.cargandoSolicitudes.set(false);
      },
      error: () => this.cargandoSolicitudes.set(false),
    });
  }

  private refrescarTodo(): void {
    this.cargarResumenYCalendario(this.anioMostrado());
    this.cargarSolicitudes(this.pageSolicitudes()?.number ?? 0);
  }

  private aVariante(tipo: 'FESTIVO' | 'APROBADO' | 'PENDIENTE'): 'festivo' | 'aprobado' | 'pendiente' {
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
