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
    path: 'insumos/nuevo',
    title: 'Nuevo insumo · SIVANT',
    canActivate: [conRol('ADMINISTRADOR')],
    loadComponent: () => import('./pages/formulario-insumo').then((m) => m.FormularioInsumo),
  },
  {
    path: ':id',
    title: 'Editar producto · SIVANT',
    canActivate: [conRol('ADMINISTRADOR')],
    loadComponent: () => import('./pages/formulario-producto').then((m) => m.FormularioProducto),
  },
];
