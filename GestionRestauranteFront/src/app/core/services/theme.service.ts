import { Injectable, effect, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'gestion-restaurante.theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.leerTemaGuardado());

  constructor() {
    // Aplica el tema al <html> (y lo persiste) tambien en la primera ejecucion, para que la
    // preferencia guardada se refleje desde el primer render sin esperar a una interaccion.
    effect(() => {
      const tema = this.theme();
      document.documentElement.setAttribute('data-theme', tema);
      localStorage.setItem(STORAGE_KEY, tema);
    });
  }

  toggle(): void {
    this.theme.update((tema) => (tema === 'dark' ? 'light' : 'dark'));
  }

  private leerTemaGuardado(): Theme {
    const guardado = localStorage.getItem(STORAGE_KEY);
    if (guardado === 'light' || guardado === 'dark') {
      return guardado;
    }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
}
