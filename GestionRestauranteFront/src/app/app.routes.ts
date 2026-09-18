import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { permissionGuard } from './core/guards/permission.guard';
import { Shell } from './layout/shell/shell';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'fichajes', pathMatch: 'full' },
      {
        path: 'fichajes',
        canActivate: [permissionGuard('FICHAJES', 'EDICION')],
        loadComponent: () =>
          import('./features/fichajes/mi-fichaje/mi-fichaje').then((m) => m.MiFichaje),
      },
      {
        path: 'vacaciones',
        canActivate: [permissionGuard('VACACIONES', 'EDICION')],
        loadComponent: () =>
          import('./features/vacaciones/mis-vacaciones/mis-vacaciones').then((m) => m.MisVacaciones),
      },
      {
        path: 'empleados',
        canActivate: [permissionGuard('EMPLEADOS')],
        loadComponent: () =>
          import('./features/empleados/empleados-list/empleados-list').then((m) => m.EmpleadosList),
      },
      {
        path: 'empleados/nuevo',
        canActivate: [permissionGuard('EMPLEADOS', 'EDICION')],
        loadComponent: () =>
          import('./features/empleados/empleado-form/empleado-form').then((m) => m.EmpleadoForm),
      },
      {
        path: 'empleados/:id',
        canActivate: [permissionGuard('EMPLEADOS', 'EDICION')],
        loadComponent: () =>
          import('./features/empleados/empleado-form/empleado-form').then((m) => m.EmpleadoForm),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
