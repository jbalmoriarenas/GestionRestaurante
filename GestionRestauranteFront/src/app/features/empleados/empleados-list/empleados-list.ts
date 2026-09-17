import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../../core/services/auth.service';
import { EmpleadoService } from '../../../core/services/empleado.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Empleado } from '../../../core/models/empleado.model';
import { Page } from '../../../core/models/page.model';
import { UiBadge } from '../../../shared/ui/ui-badge/ui-badge';
import { UiButton } from '../../../shared/ui/ui-button/ui-button';
import { UiModal } from '../../../shared/ui/ui-modal/ui-modal';
import { UiTable } from '../../../shared/ui/ui-table/ui-table';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-empleados-list',
  imports: [RouterLink, UiTable, UiBadge, UiButton, UiModal, TranslatePipe],
  templateUrl: './empleados-list.html',
  styleUrl: './empleados-list.scss',
})
export class EmpleadosList implements OnInit {
  private readonly empleadoService = inject(EmpleadoService);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly translateService = inject(TranslateService);

  readonly puedeEditar = computed(() => this.authService.puedeEditar('EMPLEADOS'));

  readonly loading = signal(true);
  readonly pageData = signal<Page<Empleado> | null>(null);
  readonly empleadoABajar = signal<Empleado | null>(null);

  ngOnInit(): void {
    this.load(0);
  }

  load(page: number): void {
    this.loading.set(true);
    this.empleadoService.listar(page, PAGE_SIZE).subscribe({
      next: (data) => {
        this.pageData.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  confirmarBaja(empleado: Empleado): void {
    this.empleadoABajar.set(empleado);
  }

  cerrarModalBaja(): void {
    this.empleadoABajar.set(null);
  }

  darDeBaja(): void {
    const empleado = this.empleadoABajar();
    if (!empleado) {
      return;
    }
    this.empleadoService.darDeBaja(empleado.id).subscribe({
      next: () => {
        const nombre = `${empleado.nombre} ${empleado.apellidos}`;
        this.notificationService.success(this.translateService.instant('empleados.notifications.baja', { nombre }));
        this.empleadoABajar.set(null);
        this.load(this.pageData()?.number ?? 0);
      },
    });
  }
}
