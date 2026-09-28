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
    path: 'restablecer-contrasena',
    loadComponent: () =>
      import('./features/auth/restablecer-password/restablecer-password').then((m) => m.RestablecerPassword),
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
        data: { title: 'menu.fichajesMio' },
        loadComponent: () =>
          import('./features/fichajes/mi-fichaje/mi-fichaje').then((m) => m.MiFichaje),
      },
      {
        path: 'fichajes/admin',
        canActivate: [permissionGuard('EMPLEADOS', 'EDICION')],
        data: { title: 'fichajes.admin.titulo' },
        loadComponent: () =>
          import('./features/fichajes/fichajes-admin/fichajes-admin').then((m) => m.FichajesAdmin),
      },
      {
        path: 'vacaciones',
        canActivate: [permissionGuard('VACACIONES', 'EDICION')],
        data: { title: 'menu.vacacionesMias' },
        loadComponent: () =>
          import('./features/vacaciones/mis-vacaciones/mis-vacaciones').then((m) => m.MisVacaciones),
      },
      {
        path: 'vacaciones/admin',
        canActivate: [permissionGuard('EMPLEADOS', 'EDICION')],
        data: { title: 'vacaciones.admin.titulo' },
        loadComponent: () =>
          import('./features/vacaciones/vacaciones-admin/vacaciones-admin').then((m) => m.VacacionesAdmin),
      },
      {
        path: 'nominas',
        canActivate: [permissionGuard('NOMINAS', 'EDICION')],
        data: { title: 'nominas.admin.titulo' },
        loadComponent: () =>
          import('./features/nominas/nominas-admin/nominas-admin').then((m) => m.NominasAdmin),
      },
      {
        path: 'facturas',
        canActivate: [permissionGuard('FACTURAS', 'EDICION')],
        data: { title: 'facturas.admin.titulo' },
        loadComponent: () =>
          import('./features/facturas/facturas-admin/facturas-admin').then((m) => m.FacturasAdmin),
      },
      {
        path: 'valoraciones',
        canActivate: [permissionGuard('VALORACIONES', 'EDICION')],
        data: { title: 'valoraciones.admin.titulo' },
        loadComponent: () =>
          import('./features/valoraciones/valoraciones-admin/valoraciones-admin').then((m) => m.ValoracionesAdmin),
      },
      {
        path: 'configuracion',
        canActivate: [permissionGuard('EMPLEADOS', 'EDICION')],
        data: { title: 'configuracion.admin.titulo' },
        loadComponent: () =>
          import('./features/configuracion/configuracion-admin/configuracion-admin').then((m) => m.ConfiguracionAdmin),
      },
      {
        path: 'empleados',
        canActivate: [permissionGuard('EMPLEADOS')],
        data: { title: 'empleados.title' },
        loadComponent: () =>
          import('./features/empleados/empleados-list/empleados-list').then((m) => m.EmpleadosList),
      },
      {
        path: 'empleados/nuevo',
        canActivate: [permissionGuard('EMPLEADOS', 'EDICION')],
        data: { title: 'empleados.title' },
        loadComponent: () =>
          import('./features/empleados/empleado-form/empleado-form').then((m) => m.EmpleadoForm),
      },
      {
        path: 'empleados/:id',
        canActivate: [permissionGuard('EMPLEADOS', 'EDICION')],
        data: { title: 'empleados.title' },
        loadComponent: () =>
          import('./features/empleados/empleado-form/empleado-form').then((m) => m.EmpleadoForm),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
