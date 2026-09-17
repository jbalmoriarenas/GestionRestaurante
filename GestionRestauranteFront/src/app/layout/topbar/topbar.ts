import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/services/auth.service';
import { LayoutService } from '../../core/services/layout.service';
import { UiIcon } from '../../shared/ui/ui-icon/ui-icon';

@Component({
  selector: 'app-topbar',
  imports: [UiIcon, TranslatePipe],
  templateUrl: './topbar.html',
  styleUrl: './topbar.scss',
})
export class Topbar {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly layoutService = inject(LayoutService);

  readonly currentUser = this.authService.currentUser;

  toggleMenu(): void {
    this.layoutService.toggleMobileMenu();
  }

  logout(): void {
    this.authService.logout();
    this.router.navigateByUrl('/login');
  }
}
