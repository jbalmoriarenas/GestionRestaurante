import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/services/auth.service';
import { AVAILABLE_LANGUAGES, storeLanguage } from '../../core/i18n/languages';
import { UiIcon } from '../../shared/ui/ui-icon/ui-icon';
import { MENU_ITEMS } from '../menu-items';

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive, UiIcon, TranslatePipe],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class Sidebar {
  private readonly authService = inject(AuthService);
  private readonly translateService = inject(TranslateService);

  readonly items = computed(() => MENU_ITEMS.filter((item) => this.authService.tieneAcceso(item.vista)));
  readonly languages = AVAILABLE_LANGUAGES;
  readonly currentLang = this.translateService.currentLang;

  changeLanguage(event: Event): void {
    const code = (event.target as HTMLSelectElement).value;
    this.translateService.use(code);
    storeLanguage(code);
  }
}
