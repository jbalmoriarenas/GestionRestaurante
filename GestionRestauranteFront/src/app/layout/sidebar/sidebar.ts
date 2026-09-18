import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, map } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/services/auth.service';
import { CodigoVista, NivelAcceso } from '../../core/models/permiso.model';
import { AVAILABLE_LANGUAGES, storeLanguage } from '../../core/i18n/languages';
import { LayoutService } from '../../core/services/layout.service';
import { UiIcon } from '../../shared/ui/ui-icon/ui-icon';
import { MENU_ITEMS, MenuItem } from '../menu-items';

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive, UiIcon, TranslatePipe],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class Sidebar {
  private readonly authService = inject(AuthService);
  private readonly translateService = inject(TranslateService);
  private readonly layoutService = inject(LayoutService);
  private readonly router = inject(Router);

  private readonly manualToggle = signal<Record<string, boolean>>({});
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );

  readonly items = computed(() =>
    MENU_ITEMS.map((item) => this.resolverItem(item)).filter((item): item is MenuItem => item !== null),
  );

  readonly openGroups = computed(() => {
    const url = this.currentUrl();
    const overrides = this.manualToggle();
    const result: Record<string, boolean> = {};
    for (const item of this.items()) {
      if (!item.children) continue;
      const activoPorRuta = item.children.some((child) => url.startsWith(child.ruta));
      result[item.labelKey] = overrides[item.labelKey] ?? activoPorRuta;
    }
    return result;
  });

  readonly languages = AVAILABLE_LANGUAGES;
  readonly currentLang = this.translateService.currentLang;
  readonly mobileMenuOpen = this.layoutService.mobileMenuOpen;

  toggleGroup(labelKey: string): void {
    const abiertoActual = this.openGroups()[labelKey] ?? false;
    this.manualToggle.update((estado) => ({ ...estado, [labelKey]: !abiertoActual }));
  }

  closeMobileMenu(): void {
    this.layoutService.closeMobileMenu();
  }

  changeLanguage(event: Event): void {
    const code = (event.target as HTMLSelectElement).value;
    this.translateService.use(code);
    storeLanguage(code);
  }

  private resolverItem(item: MenuItem): MenuItem | null {
    if (item.children) {
      const children = item.children.filter((child) => this.tieneAccesoA(child));
      return children.length > 0 ? { ...item, children } : null;
    }
    return this.tieneAccesoA(item) ? item : null;
  }

  private tieneAccesoA(entry: { vista: CodigoVista; nivelMinimo?: NivelAcceso }): boolean {
    return entry.nivelMinimo === 'EDICION'
      ? this.authService.puedeEditar(entry.vista)
      : this.authService.tieneAcceso(entry.vista);
  }
}
