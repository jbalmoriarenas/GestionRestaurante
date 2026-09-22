import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, WritableSignal, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { AbstractControl, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { FacturaService } from '../../../core/services/factura.service';
import { ProveedorService } from '../../../core/services/proveedor.service';
import { NotificationService } from '../../../core/services/notification.service';
import {
  CampoDinamico,
  EstadoPago,
  FacturaDetalle,
  FacturaExtraccionPreview,
  FacturaListado,
} from '../../../core/models/factura.model';
import { Proveedor, TipoProveedor } from '../../../core/models/proveedor.model';
import { Page } from '../../../core/models/page.model';

import { UiBadge, BadgeVariant } from '../../../shared/ui/ui-badge/ui-badge';
import { UiButton } from '../../../shared/ui/ui-button/ui-button';
import { UiCard } from '../../../shared/ui/ui-card/ui-card';
import { UiFileInput } from '../../../shared/ui/ui-file-input/ui-file-input';
import { UiInput } from '../../../shared/ui/ui-input/ui-input';
import { UiModal } from '../../../shared/ui/ui-modal/ui-modal';
import { UiSelect, UiSelectOption } from '../../../shared/ui/ui-select/ui-select';
import { UiSpinner } from '../../../shared/ui/ui-spinner/ui-spinner';
import { UiTable } from '../../../shared/ui/ui-table/ui-table';
import { UiTabItem, UiTabs } from '../../../shared/ui/ui-tabs/ui-tabs';

const PAGE_SIZE = 25;

/** Sentinel para "sin filtro" en los ui-select de proveedor/estado, mismo patron que
 * nominas-admin.ts/vacaciones-admin.ts (fase3-1-front.md/fase1-4-front.md). */
const FILTRO_TODOS = 'TODOS';

const TIPOS: TipoProveedor[] = ['PROVEEDOR', 'SUMINISTRO'];

type FiltrosFormGroup = FormGroup<{
  proveedorId: FormControl<string>;
  estadoPago: FormControl<string>;
  periodo: FormControl<string>;
}>;

function parsearPeriodo(valor: string): { mes: number | null; anio: number | null } {
  if (!valor) {
    return { mes: null, anio: null };
  }
  const [anio, mes] = valor.split('-').map(Number);
  return { mes, anio };
}

/** Mismo criterio que valida el backend (FacturaService.crear, back/fase2-1-back.md):
 * content-type dentro de la lista soportada (jpeg/png/webp/pdf). Bloquea archivos invalidos
 * antes de enviarlos, en vez de esperar al 400 del servidor. */
function archivoFacturaValidator(control: AbstractControl<File | null>): ValidationErrors | null {
  const archivo = control.value;
  if (!archivo) {
    return null;
  }
  const tiposValidos = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'];
  return tiposValidos.includes(archivo.type) ? null : { formatoInvalido: true };
}

@Component({
  selector: 'app-facturas-admin',
  imports: [
    ReactiveFormsModule,
    DecimalPipe,
    UiCard,
    UiInput,
    UiSelect,
    UiButton,
    UiTable,
    UiModal,
    UiBadge,
    UiFileInput,
    UiTabs,
    UiSpinner,
    TranslatePipe,
  ],
  templateUrl: './facturas-admin.html',
  styleUrl: './facturas-admin.scss',
})
export class FacturasAdmin implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly facturaService = inject(FacturaService);
  private readonly proveedorService = inject(ProveedorService);
  private readonly notificationService = inject(NotificationService);
  private readonly translateService = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  readonly tabs = computed<UiTabItem[]>(() => {
    this.translateService.currentLang();
    return [
      { id: 'PROVEEDOR', label: this.translateService.instant('facturas.admin.tabs.proveedores') },
      { id: 'SUMINISTRO', label: this.translateService.instant('facturas.admin.tabs.suministros') },
    ];
  });

  readonly activeTab = signal<TipoProveedor>('PROVEEDOR');
  readonly cargando = signal(false);
  readonly pagina = signal<Page<FacturaListado> | null>(null);
  readonly exportando = signal(false);

  // Un formulario de filtros por pestana (fase2-1-front.md): cambiar de pestana no debe
  // perder los filtros que el Admin ya habia puesto en la otra.
  private readonly filtrosPorTab: Record<TipoProveedor, FiltrosFormGroup> = {
    PROVEEDOR: this.formBuilder.nonNullable.group({ proveedorId: FILTRO_TODOS, estadoPago: FILTRO_TODOS, periodo: '' }),
    SUMINISTRO: this.formBuilder.nonNullable.group({ proveedorId: FILTRO_TODOS, estadoPago: FILTRO_TODOS, periodo: '' }),
  };

  private readonly proveedoresPorTab: Record<TipoProveedor, WritableSignal<UiSelectOption[]>> = {
    PROVEEDOR: signal<UiSelectOption[]>([]),
    SUMINISTRO: signal<UiSelectOption[]>([]),
  };

  private readonly proveedoresCrudosPorTab: Record<TipoProveedor, WritableSignal<Proveedor[]>> = {
    PROVEEDOR: signal<Proveedor[]>([]),
    SUMINISTRO: signal<Proveedor[]>([]),
  };

  get filtrosForm(): FiltrosFormGroup {
    return this.filtrosPorTab[this.activeTab()];
  }

  readonly proveedorFiltroOptions = computed<UiSelectOption[]>(() => {
    this.translateService.currentLang();
    const activo = this.activeTab();
    return [
      { value: FILTRO_TODOS, label: this.translateService.instant('facturas.admin.filtros.todos') },
      ...this.proveedoresPorTab[activo](),
    ];
  });

  readonly estadoPagoFiltroOptions = computed<UiSelectOption[]>(() => {
    this.translateService.currentLang();
    return [
      { value: FILTRO_TODOS, label: this.translateService.instant('facturas.admin.filtros.todos') },
      { value: 'PENDIENTE', label: this.translateService.instant('facturas.admin.estados.PENDIENTE') },
      { value: 'PAGADA', label: this.translateService.instant('facturas.admin.estados.PAGADA') },
    ];
  });

  readonly proveedorAltaOptions = computed<UiSelectOption[]>(() => this.proveedoresPorTab[this.activeTab()]());

  // ----- Alta de factura -----
  readonly mostrarAlta = signal(false);
  readonly guardandoAlta = signal(false);
  readonly extrayendo = signal(false);
  readonly origenOcr = signal(false);
  readonly camposAdicionales = signal<CampoDinamico[]>([]);
  // Previsualizacion completa de /extraer, guardada para reenviarla como datosExtraidosRawJson
  // al confirmar el alta (auditoria de facturas.datos_extraidos_raw, back/fase2-3-back.md).
  readonly previewOcr = signal<FacturaExtraccionPreview | null>(null);

  readonly archivoAlta = new FormControl<File | null>(null, [Validators.required, archivoFacturaValidator]);

  readonly altaForm = this.formBuilder.nonNullable.group({
    proveedorId: ['', Validators.required],
    numeroFactura: [''],
    producto: ['', Validators.required],
    fechaFactura: ['', Validators.required],
    fechaVencimiento: [''],
    importeBase: [''],
    iva: [''],
    importeTotal: ['', [Validators.required, Validators.min(0)]],
    estadoPago: ['PENDIENTE'],
  });

  // ----- Alta rapida de proveedor (modal anidado, ver fase2-1-front.md) -----
  readonly mostrarNuevoProveedor = signal(false);
  readonly guardandoProveedor = signal(false);
  readonly nuevoProveedorForm = this.formBuilder.nonNullable.group({
    nombre: ['', Validators.required],
    cif: [''],
    emailContacto: [''],
    telefono: [''],
  });

  // ----- Detalle -----
  readonly filaDetalle = signal<FacturaDetalle | null>(null);
  readonly cargandoDetalle = signal(false);

  ngOnInit(): void {
    for (const tipo of TIPOS) {
      this.cargarProveedores(tipo);
    }
    this.cargarPagina(0);

    for (const tipo of TIPOS) {
      this.filtrosPorTab[tipo].valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
        if (this.activeTab() === tipo) {
          this.cargarPagina(0);
        }
      });
    }
  }

  cambiarTab(tabId: string): void {
    this.activeTab.set(tabId as TipoProveedor);
    this.cargarPagina(0);
  }

  cambiarPagina(page: number): void {
    this.cargarPagina(page);
  }

  badgeVariantPago(estado: EstadoPago): BadgeVariant {
    return estado === 'PAGADA' ? 'success' : 'warning';
  }

  badgeVariantExtraccion(estado: FacturaDetalle['estadoExtraccion']): BadgeVariant {
    switch (estado) {
      case 'REVISADA':
        return 'success';
      case 'ERROR':
        return 'danger';
      default:
        return 'neutral';
    }
  }

  // ----- Alta de factura -----

  abrirAlta(): void {
    this.altaForm.reset({
      proveedorId: '',
      numeroFactura: '',
      producto: '',
      fechaFactura: '',
      fechaVencimiento: '',
      importeBase: '',
      iva: '',
      importeTotal: '',
      estadoPago: 'PENDIENTE',
    });
    this.archivoAlta.reset(null);
    this.camposAdicionales.set([]);
    this.origenOcr.set(false);
    this.previewOcr.set(null);
    this.mostrarAlta.set(true);
  }

  cerrarAlta(): void {
    this.mostrarAlta.set(false);
  }

  // Paso opcional dentro del modal de alta (fase2-3-front.md): nunca autocompleta "producto"
  // (sin heuristica fiable posible, ver back/fase2-3-back.md) y nunca guarda nada por si sola.
  extraerDatos(): void {
    if (this.archivoAlta.invalid || !this.archivoAlta.value) {
      this.archivoAlta.markAsTouched();
      return;
    }
    this.extrayendo.set(true);
    this.facturaService.extraer(this.archivoAlta.value, this.activeTab()).subscribe({
      next: (preview) => {
        this.extrayendo.set(false);
        this.origenOcr.set(true);
        this.previewOcr.set(preview);
        this.altaForm.patchValue({
          proveedorId: preview.proveedorSugeridoId !== null ? String(preview.proveedorSugeridoId) : '',
          numeroFactura: preview.numeroFacturaSugerido ?? '',
          fechaFactura: preview.fechaFacturaSugerida ?? '',
          importeBase: preview.importeBaseSugerido !== null ? String(preview.importeBaseSugerido) : '',
          iva: preview.ivaSugerido !== null ? String(preview.ivaSugerido) : '',
          importeTotal: preview.importeTotalSugerido !== null ? String(preview.importeTotalSugerido) : '',
        });
        this.camposAdicionales.set(Object.entries(preview.camposAdicionales).map(([clave, valor]) => ({ clave, valor })));
        if (preview.proveedorSugeridoId === null && preview.nombreProveedorSugerido) {
          this.nuevoProveedorForm.patchValue({ nombre: preview.nombreProveedorSugerido });
        }
      },
      error: () => this.extrayendo.set(false),
    });
  }

  anadirCampoAdicional(): void {
    this.camposAdicionales.update((lista) => [...lista, { clave: '', valor: '' }]);
  }

  eliminarCampoAdicional(index: number): void {
    this.camposAdicionales.update((lista) => lista.filter((_, i) => i !== index));
  }

  actualizarClaveCampo(index: number, clave: string): void {
    this.camposAdicionales.update((lista) => lista.map((campo, i) => (i === index ? { ...campo, clave } : campo)));
  }

  actualizarValorCampo(index: number, valor: string): void {
    this.camposAdicionales.update((lista) => lista.map((campo, i) => (i === index ? { ...campo, valor } : campo)));
  }

  confirmarAlta(): void {
    if (this.altaForm.invalid || this.archivoAlta.invalid || !this.archivoAlta.value) {
      this.altaForm.markAllAsTouched();
      this.archivoAlta.markAsTouched();
      return;
    }
    const raw = this.altaForm.getRawValue();
    const camposAdicionalesMap: Record<string, string> = {};
    for (const campo of this.camposAdicionales()) {
      if (campo.clave.trim()) {
        camposAdicionalesMap[campo.clave.trim()] = campo.valor;
      }
    }

    this.guardandoAlta.set(true);
    this.facturaService
      .crear({
        proveedorId: Number(raw.proveedorId),
        numeroFactura: raw.numeroFactura || null,
        producto: raw.producto,
        fechaFactura: raw.fechaFactura,
        fechaVencimiento: raw.fechaVencimiento || null,
        importeBase: raw.importeBase ? Number(raw.importeBase) : null,
        iva: raw.iva ? Number(raw.iva) : null,
        importeTotal: Number(raw.importeTotal),
        estadoPago: raw.estadoPago as EstadoPago,
        archivo: this.archivoAlta.value,
        camposAdicionales: Object.keys(camposAdicionalesMap).length > 0 ? camposAdicionalesMap : undefined,
        origenOcr: this.origenOcr(),
        datosExtraidosRawJson: this.origenOcr() && this.previewOcr() ? JSON.stringify(this.previewOcr()) : undefined,
      })
      .subscribe({
        next: () => {
          this.guardandoAlta.set(false);
          this.mostrarAlta.set(false);
          this.notificationService.success(this.translateService.instant('facturas.admin.notificaciones.altaExito'));
          this.cargarPagina(this.pagina()?.number ?? 0);
        },
        // Error 400 (archivo no valido / datos invalidos) ya lo muestra el errorInterceptor
        // global; el modal se mantiene abierto para reintentar sin perder lo ya rellenado.
        error: () => this.guardandoAlta.set(false),
      });
  }

  // ----- Alta rapida de proveedor -----

  abrirNuevoProveedor(): void {
    this.nuevoProveedorForm.reset({ nombre: '', cif: '', emailContacto: '', telefono: '' });
    this.mostrarNuevoProveedor.set(true);
  }

  cerrarNuevoProveedor(): void {
    this.mostrarNuevoProveedor.set(false);
  }

  confirmarNuevoProveedor(): void {
    if (this.nuevoProveedorForm.invalid) {
      this.nuevoProveedorForm.markAllAsTouched();
      return;
    }
    const raw = this.nuevoProveedorForm.getRawValue();
    const tipoActivo = this.activeTab();
    this.guardandoProveedor.set(true);
    this.proveedorService
      .crear({
        nombre: raw.nombre,
        cif: raw.cif || null,
        emailContacto: raw.emailContacto || null,
        telefono: raw.telefono || null,
        tipo: tipoActivo,
      })
      .subscribe({
        next: (proveedor) => {
          this.guardandoProveedor.set(false);
          this.mostrarNuevoProveedor.set(false);
          this.proveedoresCrudosPorTab[tipoActivo].update((lista) => [...lista, proveedor]);
          this.proveedoresPorTab[tipoActivo].update((lista) => [...lista, { value: String(proveedor.id), label: proveedor.nombre }]);
          this.altaForm.patchValue({ proveedorId: String(proveedor.id) });
        },
        error: () => this.guardandoProveedor.set(false),
      });
  }

  // ----- Marcar pagada/pendiente -----

  alternarEstadoPago(fila: FacturaListado): void {
    const nuevoEstado: EstadoPago = fila.estadoPago === 'PAGADA' ? 'PENDIENTE' : 'PAGADA';
    this.facturaService.cambiarEstadoPago(fila.id, nuevoEstado).subscribe((respuesta) => {
      this.pagina.update((pagina) => {
        if (!pagina) {
          return pagina;
        }
        return {
          ...pagina,
          content: pagina.content.map((f) => (f.id === fila.id ? { ...f, estadoPago: respuesta.estadoPago } : f)),
        };
      });
    });
  }

  // ----- Detalle -----

  abrirDetalle(fila: FacturaListado): void {
    this.cargandoDetalle.set(true);
    this.filaDetalle.set(null);
    this.facturaService.obtenerDetalle(fila.id).subscribe({
      next: (detalle) => {
        this.cargandoDetalle.set(false);
        this.filaDetalle.set(detalle);
      },
      error: () => this.cargandoDetalle.set(false),
    });
  }

  cerrarDetalle(): void {
    this.filaDetalle.set(null);
  }

  descargarArchivo(id: number, nombreArchivoOriginal: string): void {
    this.facturaService.descargarArchivo(id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const enlace = document.createElement('a');
      enlace.href = url;
      enlace.download = nombreArchivoOriginal;
      document.body.appendChild(enlace);
      enlace.click();
      enlace.remove();
      URL.revokeObjectURL(url);
    });
  }

  // ----- Exportacion (fase2-2-front.md): siempre el conjunto completo filtrado, nunca solo
  // la pagina visible -----

  exportar(): void {
    const { proveedorId, estadoPago, periodo } = this.filtrosForm.getRawValue();
    const { mes, anio } = parsearPeriodo(periodo);
    const proveedorIdNumero = proveedorId === FILTRO_TODOS ? null : Number(proveedorId);
    const estadoPagoFiltro = estadoPago === FILTRO_TODOS ? null : (estadoPago as EstadoPago);

    this.exportando.set(true);
    this.facturaService.exportar(this.activeTab(), proveedorIdNumero, estadoPagoFiltro, mes, anio).subscribe({
      next: (blob) => {
        this.exportando.set(false);
        const url = URL.createObjectURL(blob);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = `facturas_${this.activeTab().toLowerCase()}_${new Date().toISOString().slice(0, 10)}.xlsx`;
        document.body.appendChild(enlace);
        enlace.click();
        enlace.remove();
        URL.revokeObjectURL(url);
      },
      error: () => this.exportando.set(false),
    });
  }

  // ----- Privado -----

  private cargarProveedores(tipo: TipoProveedor): void {
    this.proveedorService.listar(tipo).subscribe((proveedores) => {
      this.proveedoresCrudosPorTab[tipo].set(proveedores);
      this.proveedoresPorTab[tipo].set(proveedores.map((p) => ({ value: String(p.id), label: p.nombre })));
    });
  }

  private cargarPagina(page: number): void {
    this.cargando.set(true);
    const tipo = this.activeTab();
    const { proveedorId, estadoPago, periodo } = this.filtrosPorTab[tipo].getRawValue();
    const { mes, anio } = parsearPeriodo(periodo);
    const proveedorIdNumero = proveedorId === FILTRO_TODOS ? null : Number(proveedorId);
    const estadoPagoFiltro = estadoPago === FILTRO_TODOS ? null : (estadoPago as EstadoPago);

    this.facturaService.listar(tipo, proveedorIdNumero, estadoPagoFiltro, mes, anio, page, PAGE_SIZE).subscribe({
      next: (pagina) => {
        this.pagina.set(pagina);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }
}
