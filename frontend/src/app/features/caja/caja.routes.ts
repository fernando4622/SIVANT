import { Routes } from '@angular/router';

export const rutasCaja: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'entregas' },
  {
    path: 'entregas',
    title: 'Recibir efectivo · SIVANT',
    loadComponent: () => import('./pages/entregas').then((m) => m.Entregas),
  },
  {
    path: 'fondos',
    title: 'Fondos de cambio · SIVANT',
    loadComponent: () => import('./pages/fondos').then((m) => m.Fondos),
  },
];
