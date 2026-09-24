import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, WritableSignal, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { AbstractControl, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { hoyLocalISO } from '../../../core/date-utils';
import { FacturaService } from '../../../core/services/factura.service';
import { ProveedorService } from '../../../core/services/proveedor.service';
import { NotificationService } from '../../../core/services/notification.service';
import { EstadoPago, FacturaDetalle, FacturaListado } from '../../../core/models/factura.model';
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

const PAGE_SIZE = 10;

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

/** Periodo (mes actual) en formato yyyy-MM, el mismo que produce/consume un <input type="month">
 * - valor por defecto del filtro al abrir la vista (confirmado con el usuario). Se deriva de
 * hoyLocalISO() (yyyy-MM-dd) en vez de recalcular año/mes locales aqui otra vez. */
function periodoActual(): string {
  return hoyLocalISO().slice(0, 7);
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
  readonly exportandoZip = signal(false);

  // Un formulario de filtros por pestana (fase2-1-front.md): cambiar de pestana no debe
  // perder los filtros que el Admin ya habia puesto en la otra.
  private readonly filtrosPorTab: Record<TipoProveedor, FiltrosFormGroup> = {
    PROVEEDOR: this.formBuilder.nonNullable.group({ proveedorId: FILTRO_TODOS, estadoPago: FILTRO_TODOS, periodo: periodoActual() }),
    SUMINISTRO: this.formBuilder.nonNullable.group({ proveedorId: FILTRO_TODOS, estadoPago: FILTRO_TODOS, periodo: periodoActual() }),
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

  readonly archivoAlta = new FormControl<File | null>(null, [Validators.required, archivoFacturaValidator]);

  readonly altaForm = this.formBuilder.nonNullable.group({
    proveedorId: ['', Validators.required],
    fechaImpresion: ['', Validators.required],
    fechaEntrega: ['', Validators.required],
    importeBase: ['', Validators.required],
    iva: ['', Validators.required],
    importeTotal: ['', [Validators.required, Validators.min(0)]],
    // Sin Validators.required: el <select> siempre arranca en 'PENDIENTE' y su placeholder esta
    // disabled (ui-select.html), asi que nunca puede quedar vacio desde la UI - el validador
    // seria decorativo, nunca lo dejaria invalido.
    estadoPago: ['PENDIENTE'],
  });

  // ----- Alta rapida de proveedor (modal anidado, ver fase2-1-front.md) -----
  readonly mostrarNuevoProveedor = signal(false);
  readonly guardandoProveedor = signal(false);
  readonly nuevoProveedorForm = this.formBuilder.nonNullable.group({
    nombre: ['', Validators.required],
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
      fechaImpresion: '',
      fechaEntrega: '',
      importeBase: '',
      iva: '',
      importeTotal: '',
      estadoPago: 'PENDIENTE',
    });
    this.archivoAlta.reset(null);
    this.origenOcr.set(false);
    this.mostrarAlta.set(true);
  }

  cerrarAlta(): void {
    this.mostrarAlta.set(false);
  }

  // Paso opcional dentro del modal de alta (fase2-3-front.md): nunca guarda nada por si sola.
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
        // El proveedor siempre se selecciona (o se da de alta) a mano: la extraccion OCR no
        // propone ni autocompleta proveedor, solo los campos de fecha/importe.
        this.altaForm.patchValue({
          fechaImpresion: preview.fechaImpresionSugerida ?? '',
          fechaEntrega: preview.fechaEntregaSugerida ?? '',
          importeBase: preview.importeBaseSugerido !== null ? String(preview.importeBaseSugerido) : '',
          iva: preview.ivaSugerido !== null ? String(preview.ivaSugerido) : '',
          importeTotal: preview.importeTotalSugerido !== null ? String(preview.importeTotalSugerido) : '',
        });
      },
      error: () => this.extrayendo.set(false),
    });
  }

  confirmarAlta(): void {
    if (this.altaForm.invalid || this.archivoAlta.invalid || !this.archivoAlta.value) {
      this.altaForm.markAllAsTouched();
      this.archivoAlta.markAsTouched();
      return;
    }
    const raw = this.altaForm.getRawValue();

    this.guardandoAlta.set(true);
    this.facturaService
      .crear({
        proveedorId: Number(raw.proveedorId),
        numeroFactura: null,
        fechaImpresion: raw.fechaImpresion,
        fechaEntrega: raw.fechaEntrega || null,
        importeBase: raw.importeBase ? Number(raw.importeBase) : null,
        iva: raw.iva ? Number(raw.iva) : null,
        importeTotal: Number(raw.importeTotal),
        estadoPago: raw.estadoPago as EstadoPago,
        archivo: this.archivoAlta.value,
        origenOcr: this.origenOcr(),
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
    this.nuevoProveedorForm.reset({ nombre: '', telefono: '' });
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
        cif: null,
        emailContacto: null,
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

  // ----- Exportacion (fase2-2-front.md): siempre el conjunto completo filtrado por el periodo
  // seleccionado (nunca "todos los periodos" a la vez, ni solo la pagina visible) -----

  exportar(): void {
    const periodo = this.resolverPeriodoParaExportar();
    if (!periodo) {
      return;
    }
    const { mes, anio, proveedorIdNumero, estadoPagoFiltro } = periodo;
    const titulo = this.tituloExportar(mes, anio);
    const cabeceras = this.cabecerasExportar();

    this.exportando.set(true);
    this.facturaService.exportar(this.activeTab(), proveedorIdNumero, estadoPagoFiltro, mes, anio, titulo, cabeceras).subscribe({
      next: (blob) => {
        this.exportando.set(false);
        this.descargarBlob(blob, `facturas_${this.activeTab().toLowerCase()}_${hoyLocalISO()}.xlsx`);
      },
      error: () => this.exportando.set(false),
    });
  }

  // Descarga el archivo original (PDF/imagen) de cada factura del periodo filtrado, todas
  // dentro de un unico .zip - mismo requisito de periodo obligatorio que exportar().
  exportarPdfs(): void {
    const periodo = this.resolverPeriodoParaExportar();
    if (!periodo) {
      return;
    }
    const { mes, anio, proveedorIdNumero, estadoPagoFiltro } = periodo;
    const nombreCarpeta = this.nombreCarpetaExportar(mes, anio);

    this.exportandoZip.set(true);
    this.facturaService.exportarZip(this.activeTab(), proveedorIdNumero, estadoPagoFiltro, mes, anio, nombreCarpeta).subscribe({
      next: (blob) => {
        this.exportandoZip.set(false);
        this.descargarBlob(blob, `${nombreCarpeta}.zip`);
      },
      error: () => this.exportandoZip.set(false),
    });
  }

  // ----- Privado -----

  private descargarBlob(blob: Blob, nombreArchivo: string): void {
    const url = URL.createObjectURL(blob);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombreArchivo;
    document.body.appendChild(enlace);
    enlace.click();
    enlace.remove();
    URL.revokeObjectURL(url);
  }

  // Valida el filtro de periodo (obligatorio para ambas exportaciones, Excel y ZIP: nunca
  // "todos los periodos" a la vez) y resuelve los filtros comunes a partir de el.
  private resolverPeriodoParaExportar(): {
    mes: number;
    anio: number;
    proveedorIdNumero: number | null;
    estadoPagoFiltro: EstadoPago | null;
  } | null {
    const { proveedorId, estadoPago, periodo } = this.filtrosForm.getRawValue();
    const { mes, anio } = parsearPeriodo(periodo);
    if (mes === null || anio === null) {
      this.notificationService.error(this.translateService.instant('facturas.admin.filtros.periodoRequeridoExportar'));
      return null;
    }
    return {
      mes,
      anio,
      proveedorIdNumero: proveedorId === FILTRO_TODOS ? null : Number(proveedorId),
      estadoPagoFiltro: estadoPago === FILTRO_TODOS ? null : (estadoPago as EstadoPago),
    };
  }

  // Titulo de la fila superior del Excel exportado (mismo criterio de formato de mes que
  // mis-vacaciones.ts#tituloMes). exportar() siempre llama a esto con un periodo ya elegido:
  // la exportacion nunca cubre "todos los periodos" a la vez.
  private tituloExportar(mes: number, anio: number): string {
    const locale = this.translateService.currentLang() === 'en' ? 'en-GB' : 'es-ES';
    const nombreMes = new Intl.DateTimeFormat(locale, { month: 'long' }).format(new Date(anio, mes - 1, 1));
    return this.translateService.instant('facturas.admin.exportar.tituloConPeriodo', { mes: nombreMes, anio });
  }

  // Nombre de archivo/carpeta para el ZIP de PDFs (p.ej. "facturas_julio_2026"): en minusculas
  // y sin espacios porque, a diferencia del titulo del Excel, esto se usa como nombre de
  // archivo real, no solo como texto visible.
  private nombreCarpetaExportar(mes: number, anio: number): string {
    const locale = this.translateService.currentLang() === 'en' ? 'en-GB' : 'es-ES';
    const nombreMes = new Intl.DateTimeFormat(locale, { month: 'long' }).format(new Date(anio, mes - 1, 1));
    return `facturas_${nombreMes.toLowerCase()}_${anio}`;
  }

  // Cabeceras de columna del Excel exportado, en el idioma activo de la app - el back no tiene
  // ningun mecanismo de i18n para el resto de la app (ver factura.service.ts#exportar), asi que
  // se generan aqui y se mandan ya traducidas, igual que el titulo.
  //
  // IMPORTANTE: el orden de "claves" de abajo tiene que coincidir exactamente, columna a
  // columna, con el orden en que FacturaExportService#escribirFilaFactura (back) escribe los
  // valores y con CABECERA_DEFECTO (mismo archivo) - el back no valida esto, solo comprueba que
  // el numero de cabeceras coincida. Si reordenas una lista, reordena la otra.
  private cabecerasExportar(): string[] {
    const claves = [
      'empresa',
      'telefono',
      'tipo',
      'fechaImpresion',
      'fechaEntrega',
      'importeBase',
      'iva',
      'importeTotal',
      'mes',
      'anio',
      'estadoPago',
      'nombreArchivoOriginal',
      'fechaSubida',
    ];
    return claves.map((clave) => this.translateService.instant(`facturas.admin.exportar.cabeceras.${clave}`));
  }

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
