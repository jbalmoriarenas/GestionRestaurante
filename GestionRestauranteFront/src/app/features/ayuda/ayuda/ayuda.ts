import { Component, computed, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthService } from '../../../core/services/auth.service';
import { tieneAccesoItem } from '../../../layout/menu-items';
import { UiCard } from '../../../shared/ui/ui-card/ui-card';
import { AYUDA_ITEMS } from '../ayuda-items';

@Component({
  selector: 'app-ayuda',
  imports: [UiCard, TranslatePipe],
  templateUrl: './ayuda.html',
  styleUrl: './ayuda.scss',
})
export class Ayuda {
  private readonly authService = inject(AuthService);

  // Mismo criterio de permisos que el menu lateral (Sidebar): solo se documentan las
  // vistas a las que el usuario logueado tiene acceso.
  readonly items = computed(() => AYUDA_ITEMS.filter((item) => tieneAccesoItem(this.authService, item)));
}
