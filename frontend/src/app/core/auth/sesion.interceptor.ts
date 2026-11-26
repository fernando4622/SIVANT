import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { leerProblema } from '../http/problema';
import { Sesion } from './sesion';

/** Un 401 de la API con sesión abierta lleva al inicio de sesión. */
export const sesionInterceptor: HttpInterceptorFn = (solicitud, siguiente) => {
  const sesion = inject(Sesion);
  return siguiente(solicitud).pipe(
    tap({
      error: (error: unknown) => {
        const problema = leerProblema(error);
        if (problema.estado === 401 && problema.codigo.startsWith('SESION_')) {
          sesion.terminada(problema.codigo);
        }
      },
    }),
  );
};
