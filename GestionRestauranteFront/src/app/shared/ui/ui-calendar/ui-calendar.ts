import { Component, computed, input, output } from '@angular/core';

export type CalendarioVariante = 'festivo' | 'aprobado' | 'pendiente';

export interface CalendarioMarcador {
  fecha: string; // yyyy-MM-dd
  variante: CalendarioVariante;
}

export interface CalendarioDia {
  fecha: Date;
  numero: number;
  fueraDeMes: boolean;
  hoy: boolean;
  variante: CalendarioVariante | null;
}

function aClaveFecha(fecha: Date): string {
  const anio = fecha.getFullYear();
  const mes = (fecha.getMonth() + 1).toString().padStart(2, '0');
  const dia = fecha.getDate().toString().padStart(2, '0');
  return `${anio}-${mes}-${dia}`;
}

@Component({
  selector: 'ui-calendar',
  templateUrl: './ui-calendar.html',
  styleUrl: './ui-calendar.scss',
})
export class UiCalendar {
  readonly anio = input.required<number>();
  readonly mes = input.required<number>(); // 1-12
  readonly marcadores = input<CalendarioMarcador[]>([]);
  readonly titulo = input('');
  readonly weekdayLabels = input<string[]>(['L', 'M', 'X', 'J', 'V', 'S', 'D']);
  readonly previousLabel = input('← Previous');
  readonly nextLabel = input('Next →');

  readonly mesAnterior = output<void>();
  readonly mesSiguiente = output<void>();

  private readonly marcadoresPorFecha = computed(() => {
    const mapa = new Map<string, CalendarioVariante>();
    for (const marcador of this.marcadores()) {
      mapa.set(marcador.fecha, marcador.variante);
    }
    return mapa;
  });

  readonly dias = computed<CalendarioDia[]>(() => {
    const anio = this.anio();
    const mes = this.mes();
    const marcadores = this.marcadoresPorFecha();
    const hoy = aClaveFecha(new Date());

    const primerDiaMes = new Date(anio, mes - 1, 1);
    // getDay(): 0=domingo..6=sabado -> se traduce a 0=lunes..6=domingo para la cuadricula L-D.
    const offsetInicio = (primerDiaMes.getDay() + 6) % 7;

    const ultimoDiaMes = new Date(anio, mes, 0).getDate();
    const totalCeldas = Math.ceil((offsetInicio + ultimoDiaMes) / 7) * 7;

    const dias: CalendarioDia[] = [];
    for (let i = 0; i < totalCeldas; i++) {
      const fecha = new Date(anio, mes - 1, 1 - offsetInicio + i);
      const clave = aClaveFecha(fecha);
      dias.push({
        fecha,
        numero: fecha.getDate(),
        fueraDeMes: fecha.getMonth() !== mes - 1,
        hoy: clave === hoy,
        variante: marcadores.get(clave) ?? null,
      });
    }
    return dias;
  });

  irAMesAnterior(): void {
    this.mesAnterior.emit();
  }

  irAMesSiguiente(): void {
    this.mesSiguiente.emit();
  }
}
