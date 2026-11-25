import { Routes } from '@angular/router';
import { conRol } from '../../core/auth/guardas';

export const rutasVentas: Routes = [
  {
    path: '',
    title: 'Ventas · SIVANT',
    loadComponent: () => import('./pages/ventas').then((m) => m.Ventas),
  },
  {
    path: ':id',
    title: 'Venta · SIVANT',
    loadComponent: () => import('./pages/detalle-venta').then((m) => m.DetalleVenta),
  },
  {
    path: ':id/cancelar',
    title: 'Cancelar venta · SIVANT',
    canActivate: [conRol('ADMINISTRADOR')],
    loadComponent: () => import('./pages/cancelar-venta').then((m) => m.CancelarVenta),
  },
];
