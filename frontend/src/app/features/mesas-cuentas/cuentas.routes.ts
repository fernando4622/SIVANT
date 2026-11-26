import { Routes } from '@angular/router';
import { conRol } from '../../core/auth/guardas';

/** Se montan en `cuentas/:id`. */
export const rutasCuentas: Routes = [
  {
    path: '',
    title: 'Cuenta · SIVANT',
    loadComponent: () => import('./pages/detalle-cuenta').then((m) => m.DetalleCuenta),
  },
  {
    path: 'cobro',
    title: 'Cobrar cuenta · SIVANT',
    canActivate: [conRol('MESERO')],
    loadComponent: () => import('../cobro/pages/cobrar-cuenta').then((m) => m.CobrarCuenta),
  },
  {
    path: 'comandas/nueva',
    title: 'Nueva comanda · SIVANT',
    canActivate: [conRol('MESERO')],
    loadComponent: () => import('../comandas/pages/nueva-comanda').then((m) => m.NuevaComanda),
  },
  {
    path: 'comandas/:comandaId/modificar',
    title: 'Modificar comanda · SIVANT',
    canActivate: [conRol('MESERO')],
    loadComponent: () => import('../comandas/pages/modificar-comanda').then((m) => m.ModificarComanda),
  },
  {
    path: 'comandas/:comandaId/cancelar',
    title: 'Cancelar comanda · SIVANT',
    canActivate: [conRol('CAJERO')],
    loadComponent: () => import('../comandas/pages/cancelar-comanda').then((m) => m.CancelarComanda),
  },
];
