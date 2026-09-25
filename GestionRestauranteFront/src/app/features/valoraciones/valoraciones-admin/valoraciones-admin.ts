import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { NotificationService } from '../../../core/services/notification.service';
import { ValoracionService } from '../../../core/services/valoracion.service';
import { ConexionEstado, ResumenValoraciones, Valoracion } from '../../../core/models/valoracion.model';
import { Page } from '../../../core/models/page.model';

import { UiBadge } from '../../../shared/ui/ui-badge/ui-badge';
import { UiButton } from '../../../shared/ui/ui-button/ui-button';
import { UiCard } from '../../../shared/ui/ui-card/ui-card';
import { UiModal } from '../../../shared/ui/ui-modal/ui-modal';
import { UiSelect, UiSelectOption } from '../../../shared/ui/ui-select/ui-select';
import { UiSpinner } from '../../../shared/ui/ui-spinner/ui-spinner';
import { UiTable } from '../../../shared/ui/ui-table/ui-table';

const PAGE_SIZE = 10;

/** Sentinel para "todas las puntuaciones", mismo patron que FILTRO_TODOS en
 * facturas-admin.ts/nominas-admin.ts (fase2-1-front.md/fase3-1-front.md). */
const FILTRO_TODAS = 'TODAS';

@Component({
  selector: 'app-valoraciones-admin',
  imports: [ReactiveFormsModule, DecimalPipe, UiCard, UiButton, UiBadge, UiModal, UiSelect, UiSpinner, UiTable, TranslatePipe],
  templateUrl: './valoraciones-admin.html',
  styleUrl: './valoraciones-admin.scss',
})
export class ValoracionesAdmin implements OnInit {
  private readonly valoracionService = inject(ValoracionService);
  private readonly notificationService = inject(NotificationService);
  private readonly translateService = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly puntuacionControl = new FormControl(FILTRO_TODAS, { nonNullable: true });

  readonly opcionesPuntuacion = computed<UiSelectOption[]>(() => {
    this.translateService.currentLang();
    return [
      { value: FILTRO_TODAS, label: this.translateService.instant('valoraciones.admin.filtros.todas') },
      { value: '5', label: '5' },
      { value: '4', label: '4' },
      { value: '3', label: '3' },
      { value: '2', label: '2' },
      { value: '1', label: '1' },
    ];
  });

  readonly loadingEstado = signal(true);
  readonly conexionEstado = signal<ConexionEstado | null>(null);
  readonly conectando = signal(false);
  readonly sincronizando = signal(false);
  readonly desconectando = signal(false);
  readonly mostrarModalDesconectar = signal(false);

  readonly resumen = signal<ResumenValoraciones | null>(null);
  readonly loadingTabla = signal(true);
  readonly pageData = signal<Page<Valoracion> | null>(null);

  readonly conectado = computed(() => this.conexionEstado()?.conectado ?? false);

  // Distribucion 5..1 (de mas a menos estrellas) para pintar las barras en ese orden.
  readonly distribucionOrdenada = computed(() => {
    const distribucion = this.resumen()?.distribucion;
    if (!distribucion) {
      return [];
    }
    return [5, 4, 3, 2, 1].map((puntuacion) => ({ puntuacion, cantidad: distribucion[puntuacion] ?? 0 }));
  });

  ngOnInit(): void {
    this.leerQueryParamsDeCallback();
    this.cargarEstadoConexion();

    this.puntuacionControl.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.cargarTabla(0));
  }

  private leerQueryParamsDeCallback(): void {
    const params = this.route.snapshot.queryParamMap;
    const conectadoParam = params.get('conectado');
    const errorParam = params.get('error');
    if (conectadoParam === 'true') {
      this.notificationService.success(this.translateService.instant('valoraciones.admin.notificaciones.conectadoExito'));
    } else if (errorParam) {
      this.notificationService.error(this.translateService.instant('valoraciones.admin.notificaciones.conexionError'));
    }
    if (conectadoParam !== null || errorParam !== null) {
      // Limpia los query params para que un refresco de pagina no repita la notificacion
      // (front/fase4-1-front.md). replaceUrl: true para no dejar una entrada de historial con
      // los query params ya limpiados (si no, el boton "atras" los mostraria brevemente).
      this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
    }
  }

  private cargarEstadoConexion(): void {
    this.loadingEstado.set(true);
    this.valoracionService.obtenerEstadoConexion().subscribe({
      next: (estado) => {
        this.conexionEstado.set(estado);
        this.loadingEstado.set(false);
        if (estado.conectado) {
          this.cargarResumen();
          this.cargarTabla(0);
        }
      },
      error: () => this.loadingEstado.set(false),
    });
  }

  private cargarResumen(): void {
    this.valoracionService.obtenerResumen().subscribe((resumen) => this.resumen.set(resumen));
  }

  cargarTabla(page: number): void {
    this.loadingTabla.set(true);
    const puntuacion = this.puntuacionControl.value === FILTRO_TODAS ? null : Number(this.puntuacionControl.value);
    this.valoracionService.listar(puntuacion, page, PAGE_SIZE).subscribe({
      next: (data) => {
        this.pageData.set(data);
        this.loadingTabla.set(false);
      },
      error: () => this.loadingTabla.set(false),
    });
  }

  conectar(): void {
    this.conectando.set(true);
    this.valoracionService.obtenerUrlConexion().subscribe({
      next: (respuesta) => {
        // Navegacion de pagina completa a Google, no una llamada AJAX seguida de renderizado
        // SPA: el consentimiento OAuth tiene que ocurrir en una navegacion real del navegador
        // (front/fase4-1-front.md, "Decisiones tecnicas").
        window.location.href = respuesta.urlConsentimiento;
      },
      error: () => this.conectando.set(false),
    });
  }

  abrirModalDesconectar(): void {
    this.mostrarModalDesconectar.set(true);
  }

  cerrarModalDesconectar(): void {
    this.mostrarModalDesconectar.set(false);
  }

  confirmarDesconectar(): void {
    this.desconectando.set(true);
    this.valoracionService.desconectar().subscribe({
      next: () => {
        this.desconectando.set(false);
        this.mostrarModalDesconectar.set(false);
        this.notificationService.success(this.translateService.instant('valoraciones.admin.notificaciones.desconectadoExito'));
        this.resumen.set(null);
        this.pageData.set(null);
        this.cargarEstadoConexion();
      },
      error: () => this.desconectando.set(false),
    });
  }

  sincronizarAhora(): void {
    this.sincronizando.set(true);
    this.valoracionService.sincronizar().subscribe({
      next: (resultado) => {
        this.sincronizando.set(false);
        // resultado.error != null: la sincronizacion ha fallado del lado de Google (token
        // revocado, servicio caido, etc.) - el endpoint responde 200 igualmente (back/fase4-1-back.md),
        // asi que hay que mirar este campo en vez de asumir exito solo porque no hubo error HTTP.
        if (resultado.error) {
          this.notificationService.error(
            this.translateService.instant('valoraciones.admin.notificaciones.sincronizacionError', {
              error: resultado.error,
            }),
          );
        } else {
          this.notificationService.success(
            this.translateService.instant('valoraciones.admin.notificaciones.sincronizacionExito', {
              nuevas: resultado.nuevas,
              actualizadas: resultado.actualizadas,
            }),
          );
        }
        this.cargarEstadoConexion();
      },
      error: () => this.sincronizando.set(false),
    });
  }
}
