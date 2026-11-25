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
        path: 'mesas',
        title: 'Mesas · SIVANT',
        canActivate: [conRol('MESERO', 'CAJERO')],
        loadComponent: () => import('./features/mesas-cuentas/pages/mesas').then((m) => m.Mesas),
      },
      {
        path: 'cuentas/:id',
        canActivate: [conRol('MESERO', 'CAJERO')],
        loadChildren: () => import('./features/mesas-cuentas/cuentas.routes').then((m) => m.rutasCuentas),
      },
      {
        path: 'cocina',
        title: 'Cocina · SIVANT',
        canActivate: [conRol('COCINERO')],
        loadComponent: () => import('./features/cocina/pages/cocina').then((m) => m.Cocina),
      },
      {
        path: 'notificaciones',
        title: 'Avisos · SIVANT',
        loadComponent: () => import('./features/notificaciones/pages/notificaciones').then((m) => m.Notificaciones),
      },
      {
        path: 'empleados',
        canActivate: [conRol('ADMINISTRADOR')],
        loadChildren: () => import('./features/empleados/empleados.routes').then((m) => m.rutasEmpleados),
      },
      {
        path: 'productos',
        canActivate: [conRol('ADMINISTRADOR', 'MESERO')],
        loadChildren: () => import('./features/productos/productos.routes').then((m) => m.rutasProductos),
      },
      {
        path: 'inventario',
        canActivate: [conRol('ADMINISTRADOR', 'ALMACENISTA')],
        loadChildren: () => import('./features/inventario/inventario.routes').then((m) => m.rutasInventario),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
