import { Routes } from '@angular/router';

export const rutasInventario: Routes = [
  {
    path: '',
    title: 'Inventario · SIVANT',
    loadComponent: () => import('./pages/existencias').then((m) => m.Existencias),
  },
  {
    path: 'movimientos',
    title: 'Movimientos de inventario · SIVANT',
    loadComponent: () => import('./pages/movimientos').then((m) => m.Movimientos),
  },
  {
    path: 'existencias/:id',
    title: 'Existencia · SIVANT',
    loadComponent: () => import('./pages/detalle-existencia').then((m) => m.DetalleExistencia),
  },
  {
    path: 'ajustes',
    title: 'Ajustes de inventario · SIVANT',
    loadComponent: () => import('./pages/ajustes').then((m) => m.Ajustes),
  },
  {
    path: 'ajustes/:id',
    title: 'Ajuste de inventario · SIVANT',
    loadComponent: () => import('./pages/detalle-ajuste').then((m) => m.DetalleAjuste),
  },
];
