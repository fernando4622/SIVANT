import { Routes } from '@angular/router';

export const rutasEmpleados: Routes = [
  {
    path: '',
    title: 'Empleados · SIVANT',
    loadComponent: () => import('./pages/listado-empleados').then((m) => m.ListadoEmpleados),
  },
  {
    path: 'nuevo',
    title: 'Nuevo empleado · SIVANT',
    loadComponent: () => import('./pages/formulario-empleado').then((m) => m.FormularioEmpleado),
  },
  {
    path: ':id',
    title: 'Editar empleado · SIVANT',
    loadComponent: () => import('./pages/formulario-empleado').then((m) => m.FormularioEmpleado),
  },
];
