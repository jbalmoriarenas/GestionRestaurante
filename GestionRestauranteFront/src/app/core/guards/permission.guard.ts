import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { CodigoVista, NivelAcceso } from '../models/permiso.model';

export function permissionGuard(vista: CodigoVista, nivelMinimo: NivelAcceso = 'VISUALIZACION'): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isAuthenticated()) {
      return router.parseUrl('/login');
    }

    const tieneAcceso = nivelMinimo === 'EDICION' ? authService.puedeEditar(vista) : authService.tieneAcceso(vista);

    if (tieneAcceso) {
      return true;
    }

    // Redirige a /login (en vez de '/') para no entrar en bucle con la ruta por defecto
    // si en el futuro un tipo de usuario se queda sin ningún permiso asignado.
    authService.logout();
    return router.parseUrl('/login');
  };
}
