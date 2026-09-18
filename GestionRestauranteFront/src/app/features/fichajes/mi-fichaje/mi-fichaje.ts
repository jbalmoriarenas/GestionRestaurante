import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { FichajeService } from '../../../core/services/fichaje.service';
import { GeolocationError, GeolocationService } from '../../../core/services/geolocation.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Fichaje } from '../../../core/models/fichaje.model';
import { UiButton } from '../../../shared/ui/ui-button/ui-button';
import { UiCard } from '../../../shared/ui/ui-card/ui-card';
import { UiTable } from '../../../shared/ui/ui-table/ui-table';

@Component({
  selector: 'app-mi-fichaje',
  imports: [UiButton, UiCard, UiTable, TranslatePipe, DatePipe],
  templateUrl: './mi-fichaje.html',
  styleUrl: './mi-fichaje.scss',
})
export class MiFichaje implements OnInit {
  private readonly fichajeService = inject(FichajeService);
  private readonly geolocationService = inject(GeolocationService);
  private readonly notificationService = inject(NotificationService);
  private readonly translateService = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  private intervalId: ReturnType<typeof setInterval> | null = null;

  readonly enCurso = signal(false);
  readonly segundosTranscurridos = signal(0);
  readonly obteniendoUbicacion = signal(false);
  readonly tiempoFormateado = computed(() => this.formatearTiempo(this.segundosTranscurridos()));
  readonly fechaHoy = computed(() => this.formatearFecha(this.translateService.currentLang()));

  readonly fichajesHoy = signal<Fichaje[]>([]);
  readonly horasImputadas = signal(0);
  readonly horasTeoricas = signal(0);
  readonly cargandoHoy = signal(true);

  ngOnInit(): void {
    this.fichajeService.obtenerActual().subscribe((actual) => {
      if (actual.enCurso && actual.horaEntrada) {
        this.enCurso.set(true);
        this.segundosTranscurridos.set(this.segundosDesde(actual.horaEntrada));
        this.iniciarIntervalo();
      }
    });

    this.cargarResumenHoy();
    this.destroyRef.onDestroy(() => this.detenerIntervalo());
  }

  iniciar(): void {
    this.obteniendoUbicacion.set(true);
    this.geolocationService
      .obtenerPosicionActual()
      .then(({ lat, lng }) => {
        this.fichajeService.iniciar(lat, lng).subscribe({
          next: () => {
            this.obteniendoUbicacion.set(false);
            this.enCurso.set(true);
            this.segundosTranscurridos.set(0);
            this.iniciarIntervalo();
            this.cargarResumenHoy();
          },
          error: (error: HttpErrorResponse) => {
            this.obteniendoUbicacion.set(false);
            // El toast del error (403 fuera de rango, etc.) ya lo muestra el
            // errorInterceptor global; aqui solo hay que resincronizar en el caso de
            // doble fichaje (409, p.ej. dos pestañas), que sigue siendo un estado valido.
            if (error.status === 409) {
              this.resincronizar();
            }
          },
        });
      })
      .catch((error: GeolocationError) => {
        this.obteniendoUbicacion.set(false);
        this.notificationService.error(
          this.translateService.instant(`fichajes.miFichaje.errores.${error.tipo}`),
        );
      });
  }

  finalizar(): void {
    this.fichajeService.finalizar().subscribe((fichaje) => {
      this.detenerIntervalo();
      this.enCurso.set(false);
      this.segundosTranscurridos.set(0);
      this.notificationService.success(
        this.translateService.instant('fichajes.miFichaje.turnoFinalizado', {
          horas: fichaje.horasCalculadas,
        }),
      );
      this.cargarResumenHoy();
    });
  }

  private resincronizar(): void {
    this.fichajeService.obtenerActual().subscribe((actual) => {
      this.enCurso.set(actual.enCurso);
      if (actual.enCurso && actual.horaEntrada) {
        this.segundosTranscurridos.set(this.segundosDesde(actual.horaEntrada));
        this.iniciarIntervalo();
      }
    });
  }

  private cargarResumenHoy(): void {
    this.cargandoHoy.set(true);
    this.fichajeService.obtenerResumenHoy().subscribe({
      next: (resumen) => {
        this.fichajesHoy.set(resumen.fichajes);
        this.horasImputadas.set(resumen.horasImputadas);
        this.horasTeoricas.set(resumen.horasTeoricas);
        this.cargandoHoy.set(false);
      },
      error: () => this.cargandoHoy.set(false),
    });
  }

  private iniciarIntervalo(): void {
    this.detenerIntervalo();
    this.intervalId = setInterval(() => this.segundosTranscurridos.update((s) => s + 1), 1000);
  }

  private detenerIntervalo(): void {
    if (this.intervalId !== null) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }

  private segundosDesde(horaEntradaIso: string): number {
    return Math.max(Math.floor((Date.now() - new Date(horaEntradaIso).getTime()) / 1000), 0);
  }

  private formatearFecha(idioma: string | null): string {
    const locale = idioma === 'en' ? 'en-GB' : 'es-ES';
    return new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(
      new Date(),
    );
  }

  private formatearTiempo(totalSegundos: number): string {
    const horas = Math.floor(totalSegundos / 3600);
    const minutos = Math.floor((totalSegundos % 3600) / 60);
    const segundos = totalSegundos % 60;
    const pad = (n: number) => n.toString().padStart(2, '0');
    return `${pad(horas)}:${pad(minutos)}:${pad(segundos)}`;
  }
}
