import { Component, DestroyRef, OnInit, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, map } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/services/auth.service';
import { ConfiguracionService } from '../../core/services/configuracion.service';
import { AVAILABLE_LANGUAGES, storeLanguage } from '../../core/i18n/languages';
import { LayoutService } from '../../core/services/layout.service';
import { UiIcon } from '../../shared/ui/ui-icon/ui-icon';
import { MENU_ITEMS, MenuItem, tieneAccesoItem } from '../menu-items';

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive, UiIcon, TranslatePipe],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class Sidebar implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly configuracionService = inject(ConfiguracionService);
  private readonly translateService = inject(TranslateService);
  private readonly layoutService = inject(LayoutService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  // Nombre/logo de la empresa (back/fase5-1-back.md): se piden una sola vez, al montar el
  // sidebar (persistente durante toda la sesion, no se recrea al navegar entre rutas hijas de
  // Shell) - null hasta que responda obtenerEmpresa()/obtenerLogo(), momento en el que la
  // plantilla cae de vuelta al logo/nombre estaticos por defecto.
  readonly nombreEmpresa = signal<string | null>(null);
  readonly logoUrl = signal<string | null>(null);
  private ultimoLogoObjectUrl: string | null = null;

  ngOnInit(): void {
    this.configuracionService.obtenerEmpresa().subscribe((empresa) => {
      this.nombreEmpresa.set(empresa.nombreEmpresa);
      // El logo se pide como blob, nunca como <img src> directo: el endpoint exige un usuario
      // autenticado y el interceptor de JWT solo se aplica a peticiones HttpClient
      // (front/fase5-1-front.md).
      if (empresa.logoUrl !== null) {
        this.configuracionService.obtenerLogo().subscribe((blob) => {
          const objectUrl = URL.createObjectURL(blob);
          this.ultimoLogoObjectUrl = objectUrl;
          this.logoUrl.set(objectUrl);
        });
      }
    });

    this.destroyRef.onDestroy(() => {
      if (this.ultimoLogoObjectUrl) {
        URL.revokeObjectURL(this.ultimoLogoObjectUrl);
      }
    });
  }

  private readonly manualToggle = signal<Record<string, boolean>>({});
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );

  // Un cierre manual solo debe durar mientras el usuario no navegue: si no, un grupo
  // cerrado a mano se queda cerrado para siempre aunque luego se navegue a una de sus
  // rutas hijas, escondiendo el enlace activo sin que quede forma obvia de recuperarlo
  // salvo recordar que existe el toggle.
  private readonly resetToggleManualAlNavegar = effect(() => {
    this.currentUrl();
    this.manualToggle.set({});
  });

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
      const children = item.children.filter((child) => tieneAccesoItem(this.authService, child));
      return children.length > 0 ? { ...item, children } : null;
    }
    return tieneAccesoItem(this.authService, item) ? item : null;
  }
}
