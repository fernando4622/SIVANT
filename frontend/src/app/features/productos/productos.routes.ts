import { Routes } from '@angular/router';
import { conRol } from '../../core/auth/guardas';

export const rutasProductos: Routes = [
  {
    path: '',
    title: 'Productos · SIVANT',
    loadComponent: () => import('./pages/listado-productos').then((m) => m.ListadoProductos),
  },
  {
    path: 'nuevo',
    title: 'Nuevo producto · SIVANT',
    canActivate: [conRol('ADMINISTRADOR')],
    loadComponent: () => import('./pages/formulario-producto').then((m) => m.FormularioProducto),
  },
  {
    path: ':id',
    title: 'Editar producto · SIVANT',
    canActivate: [conRol('ADMINISTRADOR')],
    loadComponent: () => import('./pages/formulario-producto').then((m) => m.FormularioProducto),
  },
];
