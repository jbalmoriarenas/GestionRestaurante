import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { filter, map } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { LayoutService } from '../../core/services/layout.service';
import { ThemeService } from '../../core/services/theme.service';
import { UiIcon } from '../../shared/ui/ui-icon/ui-icon';

@Component({
  selector: 'app-topbar',
  imports: [UiIcon, TranslatePipe, RouterLink],
  templateUrl: './topbar.html',
  styleUrl: './topbar.scss',
})
export class Topbar {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly layoutService = inject(LayoutService);
  private readonly themeService = inject(ThemeService);

  readonly currentUser = this.authService.currentUser;
  readonly theme = this.themeService.theme;

  // Clave i18n del titulo de la vista activa (Topbar es persistente, no se recrea al navegar
  // entre rutas hijas de Shell, de ahi el mismo patron ya usado en Sidebar.currentUrl para
  // reaccionar a NavigationEnd en vez de depender del ciclo de vida del componente).
  readonly pageTitle = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(() => this.tituloDeRutaActual()),
    ),
    { initialValue: this.tituloDeRutaActual() },
  );

  toggleMenu(): void {
    this.layoutService.toggleMobileMenu();
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }

  logout(): void {
    this.authService.logout();
    this.router.navigateByUrl('/login');
  }

  private tituloDeRutaActual(): string | null {
    let ruta = this.activatedRoute.root;
    while (ruta.firstChild) {
      ruta = ruta.firstChild;
    }
    // En la primera construccion (justo al entrar en el Shell, con la ruta hija aun
    // resolviendose via loadComponent) el snapshot mas profundo puede no estar listo todavia;
    // el propio NavigationEnd que sigue ya trae el arbol completo y recalcula el valor real.
    return (ruta.snapshot?.data?.['title'] as string | undefined) ?? null;
  }
}
