import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Rol } from '../../../core/auth/rol';

export type EstadoEmpleado = 'ACTIVO' | 'INACTIVO';

export interface Empleado {
  id: string;
  nombreCompleto: string;
  correo: string;
  rol: Rol;
  estado: EstadoEmpleado;
}

export interface PaginaEmpleados {
  contenido: Empleado[];
  page: number;
  size: number;
  totalElementos: number;
  totalPaginas: number;
}

export interface DatosEmpleado {
  nombreCompleto: string;
  correo: string;
  contrasena: string;
  rol: Rol;
}

export interface FiltroEmpleados {
  q: string;
  rol: string;
  estado: string;
  page: number;
}

export const TAMANO_PAGINA = 10;
const API = '/api/v1/empleados';

@Injectable({ providedIn: 'root' })
export class EmpleadosApi {
  private readonly http = inject(HttpClient);

  listar(filtro: FiltroEmpleados): Observable<PaginaEmpleados> {
    let params = new HttpParams().set('page', filtro.page).set('size', TAMANO_PAGINA);
    for (const clave of ['q', 'rol', 'estado'] as const) {
      if (filtro[clave]) {
        params = params.set(clave, filtro[clave]);
      }
    }
    return this.http.get<PaginaEmpleados>(API, { params });
  }

  obtener(id: string): Observable<Empleado> {
    return this.http.get<Empleado>(`${API}/${encodeURIComponent(id)}`);
  }

  registrar(datos: DatosEmpleado): Observable<Empleado> {
    return this.http.post<Empleado>(API, datos);
  }

  editar(id: string, cambios: Partial<DatosEmpleado>): Observable<Empleado> {
    return this.http.patch<Empleado>(`${API}/${encodeURIComponent(id)}`, cambios);
  }

  desactivar(id: string): Observable<Empleado> {
    return this.http.post<Empleado>(`${API}/${encodeURIComponent(id)}/desactivacion`, { confirmado: true });
  }
}
