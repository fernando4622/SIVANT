import { Routes } from '@angular/router';
import { conRol, conSesion, sinSesion } from './core/auth/guardas';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Iniciar sesión · SIVANT',
    canActivate: [sinSesion],
    loadComponent: () => import('./core/auth/login').then((m) => m.Login),
  },
  {
    path: '',
    canActivate: [conSesion],
    loadComponent: () => import('./core/layout/estructura').then((m) => m.Estructura),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
      {
        path: 'inicio',
        title: 'Inicio · SIVANT',
        loadComponent: () => import('./features/inicio/inicio').then((m) => m.Inicio),
      },
      {
        path: 'empleados',
        canActivate: [conRol('ADMINISTRADOR')],
        loadChildren: () => import('./features/empleados/empleados.routes').then((m) => m.rutasEmpleados),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
