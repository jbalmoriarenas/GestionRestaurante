import { Component, effect, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterOutlet } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { ThemeService } from './core/services/theme.service';
import { NotificationHost } from './layout/notification-host/notification-host';

@Component({
  imports: [RouterOutlet, NotificationHost],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly titleService = inject(Title);
  private readonly translateService = inject(TranslateService);
  // Solo se inyecta para que su efecto (aplicar el tema al <html>) arranque desde el primer
  // render, incluso en /login (fuera del Shell) - back/front sin ticket, a peticion del usuario.
  private readonly themeService = inject(ThemeService);

  constructor() {
    // El <title> del index.html es estatico y vive fuera del bootstrap de Angular,
    // por lo que no puede pasar por el pipe de traduccion. Se fija aqui (y se
    // actualiza automaticamente al cambiar de idioma) via el servicio Title.
    effect(() => {
      this.translateService.currentLang();
      this.titleService.setTitle(this.translateService.instant('app.brand'));
    });
  }
}
