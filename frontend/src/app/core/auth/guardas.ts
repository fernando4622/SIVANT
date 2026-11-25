import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Rol } from './rol';
import { Sesion } from './sesion';

/** Orientan la navegación. El permiso efectivo lo comprueba siempre la API. */
export const conSesion: CanActivateFn = (_ruta, estado) =>
  inject(Sesion).activa() || inject(Router).createUrlTree(['/login'], { queryParams: { volver: estado.url } });

export const sinSesion: CanActivateFn = () => !inject(Sesion).activa() || inject(Router).createUrlTree(['/inicio']);

export function conRol(...roles: Rol[]): CanActivateFn {
  return () => {
    const perfil = inject(Sesion).perfil();
    return (perfil !== null && roles.includes(perfil.rol)) || inject(Router).createUrlTree(['/inicio']);
  };
}
