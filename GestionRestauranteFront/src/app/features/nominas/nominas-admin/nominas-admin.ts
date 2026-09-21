import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { AbstractControl, FormBuilder, FormControl, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { EmpleadoService } from '../../../core/services/empleado.service';
import { NominaService } from '../../../core/services/nomina.service';
import { NotificationService } from '../../../core/services/notification.service';
import {
  EstadoNomina,
  MotivoPendienteNomina,
  NominaAccionResponse,
  NominaListado,
  NominaPendiente,
} from '../../../core/models/nomina.model';
import { Page } from '../../../core/models/page.model';
import { UiBadge, BadgeVariant } from '../../../shared/ui/ui-badge/ui-badge';
import { UiButton } from '../../../shared/ui/ui-button/ui-button';
import { UiCard } from '../../../shared/ui/ui-card/ui-card';
import { UiFileInput } from '../../../shared/ui/ui-file-input/ui-file-input';
import { UiInput } from '../../../shared/ui/ui-input/ui-input';
import { UiModal } from '../../../shared/ui/ui-modal/ui-modal';
import { UiSelect, UiSelectOption } from '../../../shared/ui/ui-select/ui-select';
import { UiTable } from '../../../shared/ui/ui-table/ui-table';

const PAGE_SIZE = 20;

/** Sentinel para "sin filtro" en los ui-select de empleado/estado: no puede ser '' porque
 * ui-select reserva ese valor para su placeholder deshabilitado (mismo patron que
 * vacaciones-admin.ts, fase1-4-front.md). */
const FILTRO_TODOS = 'TODOS';

interface PendienteFila {
  pendiente: NominaPendiente;
  empleadoIdControl: FormControl<string>;
  periodoControl: FormControl<string>;
}

function periodoActual(): string {
  const ahora = new Date();
  return `${ahora.getFullYear()}-${String(ahora.getMonth() + 1).padStart(2, '0')}`;
}

function parsearPeriodo(valor: string): { mes: number; anio: number } {
  const [anio, mes] = valor.split('-').map(Number);
  return { mes, anio };
}

/** Mismo criterio que valida el backend (NominaService.validarPdf, back/fase3-1-back.md):
 * extension .pdf y content-type application/pdf. Bloquea archivos invalidos antes de enviarlos,
 * en vez de esperar al 400 del servidor. */
function archivoPdfValidator(control: AbstractControl<File | null>): ValidationErrors | null {
  const archivo = control.value;
  if (!archivo) {
    return null;
  }
  const extensionValida = archivo.name.toLowerCase().endsWith('.pdf');
  const tipoValido = archivo.type === 'application/pdf';
  return extensionValida && tipoValido ? null : { formatoInvalido: true };
}

@Component({
  selector: 'app-nominas-admin',
  imports: [ReactiveFormsModule, UiCard, UiInput, UiSelect, UiButton, UiTable, UiModal, UiBadge, UiFileInput, TranslatePipe],
  templateUrl: './nominas-admin.html',
  styleUrl: './nominas-admin.scss',
})
export class NominasAdmin implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly nominaService = inject(NominaService);
  private readonly empleadoService = inject(EmpleadoService);
  private readonly notificationService = inject(NotificationService);
  private readonly translateService = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  readonly empleadosBase = signal<UiSelectOption[]>([]);
  readonly cargando = signal(false);
  readonly pagina = signal<Page<NominaListado> | null>(null);

  readonly filtrosForm = this.formBuilder.nonNullable.group({
    empleadoId: [FILTRO_TODOS],
    periodo: [periodoActual()],
    estado: [FILTRO_TODOS],
  });

  readonly empleadoFiltroOptions = computed<UiSelectOption[]>(() => {
    this.translateService.currentLang();
    return [
      { value: FILTRO_TODOS, label: this.translateService.instant('nominas.admin.filtros.todos') },
      ...this.empleadosBase(),
    ];
  });

  readonly estadoFiltroOptions = computed<UiSelectOption[]>(() => {
    this.translateService.currentLang();
    return [
      { value: FILTRO_TODOS, label: this.translateService.instant('nominas.admin.filtros.todos') },
      { value: 'PENDIENTE', label: this.translateService.instant('nominas.admin.estados.PENDIENTE') },
      { value: 'ADJUNTADA', label: this.translateService.instant('nominas.admin.estados.ADJUNTADA') },
      { value: 'ENVIADA', label: this.translateService.instant('nominas.admin.estados.ENVIADA') },
    ];
  });

  // ----- Subida individual / reemplazo -----
  readonly filaSubida = signal<NominaListado | null>(null);
  readonly archivoSubida = new FormControl<File | null>(null, [Validators.required, archivoPdfValidator]);
  readonly guardandoSubida = signal(false);

  // ----- Subida masiva por ZIP -----
  readonly mostrarZip = signal(false);
  readonly archivoZip = new FormControl<File | null>(null, Validators.required);
  readonly subiendoZip = signal(false);
  readonly resumenZip = signal<number | null>(null);
  readonly pendientesZip = signal<PendienteFila[]>([]);
  readonly asignandoPendiente = signal<string | null>(null);
  readonly mostrarConfirmarCierreZip = signal(false);

  // ----- Envio por email -----
  readonly filaEnvio = signal<NominaListado | null>(null);
  readonly guardandoEnvio = signal(false);

  ngOnInit(): void {
    this.empleadoService.listar(0, 100).subscribe((pagina) => {
      this.empleadosBase.set(pagina.content.map((e) => ({ value: String(e.id), label: `${e.nombre} ${e.apellidos}` })));
    });

    this.cargarPagina(0);

    this.filtrosForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.cargarPagina(0));
  }

  cambiarPagina(page: number): void {
    this.cargarPagina(page);
  }

  badgeVariant(estado: EstadoNomina): BadgeVariant {
    switch (estado) {
      case 'ENVIADA':
        return 'success';
      case 'ADJUNTADA':
        return 'neutral';
      case 'PENDIENTE':
        return 'warning';
    }
  }

  // ----- Subida individual / reemplazo -----

  abrirSubida(fila: NominaListado): void {
    this.filaSubida.set(fila);
    this.archivoSubida.reset(null);
  }

  cerrarSubida(): void {
    this.filaSubida.set(null);
  }

  confirmarSubida(): void {
    const fila = this.filaSubida();
    if (!fila || this.archivoSubida.invalid) {
      this.archivoSubida.markAsTouched();
      return;
    }
    const archivo = this.archivoSubida.value!;
    this.guardandoSubida.set(true);
    this.nominaService.subir(fila.empleadoId, fila.periodoMes, fila.periodoAnio, archivo).subscribe({
      next: (respuesta) => {
        this.guardandoSubida.set(false);
        this.filaSubida.set(null);
        this.notificationService.success(this.translateService.instant('nominas.admin.notificaciones.subidaExito'));
        this.actualizarFilaDesdeRespuesta(fila.empleadoId, respuesta);
      },
      // Error 400 (archivo no valido) ya lo muestra el errorInterceptor global; el modal se
      // mantiene abierto para reintentar con otro archivo (ui-file-input no puede "recordar"
      // el archivo elegido tras un error, hay que volver a seleccionarlo).
      error: () => this.guardandoSubida.set(false),
    });
  }

  // ----- Descarga -----

  descargar(fila: NominaListado): void {
    if (fila.nominaId === null) {
      return;
    }
    this.nominaService.descargar(fila.nominaId).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const enlace = document.createElement('a');
      enlace.href = url;
      enlace.download = fila.nombreArchivoOriginal ?? 'nomina.pdf';
      document.body.appendChild(enlace);
      enlace.click();
      enlace.remove();
      URL.revokeObjectURL(url);
    });
  }

  // ----- Subida masiva por ZIP -----

  abrirZip(): void {
    this.archivoZip.reset(null);
    this.resumenZip.set(null);
    this.pendientesZip.set([]);
    this.mostrarZip.set(true);
  }

  intentarCerrarZip(): void {
    if (this.pendientesZip().length > 0) {
      this.mostrarConfirmarCierreZip.set(true);
      return;
    }
    this.cerrarZipDefinitivo();
  }

  cancelarCierreZip(): void {
    this.mostrarConfirmarCierreZip.set(false);
  }

  confirmarCierreZip(): void {
    this.mostrarConfirmarCierreZip.set(false);
    this.cerrarZipDefinitivo();
  }

  private cerrarZipDefinitivo(): void {
    this.mostrarZip.set(false);
    this.archivoZip.reset(null);
    this.resumenZip.set(null);
    this.pendientesZip.set([]);
  }

  confirmarSubidaZip(): void {
    if (this.archivoZip.invalid) {
      this.archivoZip.markAsTouched();
      return;
    }
    const archivo = this.archivoZip.value!;
    this.subiendoZip.set(true);
    this.nominaService.subirZip(archivo).subscribe({
      next: (resultado) => {
        this.subiendoZip.set(false);
        this.resumenZip.set(resultado.asignadosAutomaticamente);
        this.pendientesZip.set(
          resultado.pendientes.map((pendiente) => ({
            pendiente,
            empleadoIdControl: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
            periodoControl: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
          })),
        );
        if (resultado.pendientes.length === 0) {
          this.cerrarZipDefinitivo();
        }
        this.cargarPagina(this.pagina()?.number ?? 0);
      },
      error: () => this.subiendoZip.set(false),
    });
  }

  motivoPendienteKey(motivo: MotivoPendienteNomina): string {
    return `nominas.admin.zip.pendientes.motivo.${motivo}`;
  }

  asignarPendiente(fila: PendienteFila): void {
    if (fila.empleadoIdControl.invalid || fila.periodoControl.invalid) {
      fila.empleadoIdControl.markAsTouched();
      fila.periodoControl.markAsTouched();
      return;
    }
    const { mes, anio } = parsearPeriodo(fila.periodoControl.value);
    this.asignandoPendiente.set(fila.pendiente.tempId);
    this.nominaService.asignarPendiente(fila.pendiente.tempId, Number(fila.empleadoIdControl.value), mes, anio).subscribe({
      next: () => {
        this.asignandoPendiente.set(null);
        this.pendientesZip.update((lista) => lista.filter((p) => p.pendiente.tempId !== fila.pendiente.tempId));
        // Refleja tambien las asignaciones manuales en el resumen, no solo el matching
        // automatico inicial - si no, el contador se queda congelado y parece que la
        // asignacion manual "no cuenta".
        this.resumenZip.update((valor) => (valor ?? 0) + 1);
        this.cargarPagina(this.pagina()?.number ?? 0);
      },
      error: () => this.asignandoPendiente.set(null),
    });
  }

  // ----- Envio por email -----

  abrirEnvio(fila: NominaListado): void {
    this.filaEnvio.set(fila);
  }

  cerrarEnvio(): void {
    this.filaEnvio.set(null);
  }

  confirmarEnvio(): void {
    const fila = this.filaEnvio();
    if (!fila || fila.nominaId === null) {
      return;
    }
    this.guardandoEnvio.set(true);
    this.nominaService.enviar(fila.nominaId).subscribe({
      next: (respuesta) => {
        this.guardandoEnvio.set(false);
        this.filaEnvio.set(null);
        this.notificationService.success(this.translateService.instant('nominas.admin.notificaciones.envioExito'));
        this.actualizarFilaDesdeRespuesta(fila.empleadoId, respuesta);
      },
      // Error 502 (fallo SMTP) ya lo muestra el errorInterceptor global; el modal se
      // mantiene abierto para reintentar sin perder el contexto.
      error: () => this.guardandoEnvio.set(false),
    });
  }

  private cargarPagina(page: number): void {
    this.cargando.set(true);
    const { empleadoId, periodo, estado } = this.filtrosForm.getRawValue();
    const { mes, anio } = parsearPeriodo(periodo);
    const empleadoIdNumero = empleadoId === FILTRO_TODOS ? null : Number(empleadoId);
    const estadoFiltro = estado === FILTRO_TODOS ? null : (estado as EstadoNomina);
    this.nominaService.listar(empleadoIdNumero, mes, anio, estadoFiltro, page, PAGE_SIZE).subscribe({
      next: (pagina) => {
        this.pagina.set(pagina);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  private actualizarFilaDesdeRespuesta(empleadoId: number, respuesta: NominaAccionResponse): void {
    const pagina = this.pagina();
    if (!pagina) {
      return;
    }
    const content = pagina.content.map((fila) =>
      fila.empleadoId === empleadoId && fila.periodoMes === respuesta.periodoMes && fila.periodoAnio === respuesta.periodoAnio
        ? {
            ...fila,
            estado: respuesta.estado,
            nominaId: respuesta.id,
            nombreArchivoOriginal: respuesta.nombreArchivoOriginal,
            fechaSubida: respuesta.fechaSubida,
            fechaEnvio: respuesta.fechaEnvio,
          }
        : fila,
    );
    this.pagina.set({ ...pagina, content });
  }
}
