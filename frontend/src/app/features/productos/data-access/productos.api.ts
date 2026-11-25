import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { NOMBRE_UNIDAD } from '../../../shared/formato';
import { Pagina, TAMANO_PAGINA } from '../../../shared/listado';

export type TipoProducto = 'PREPARADO' | 'REVENTA';
export type UnidadMedida = keyof typeof NOMBRE_UNIDAD;

export const NOMBRE_TIPO: Record<TipoProducto, string> = { PREPARADO: 'Preparado', REVENTA: 'Reventa' };

export interface Insumo {
  id: string;
  nombre: string;
  unidadMedida: UnidadMedida;
}

export interface Producto {
  id: string;
  nombre: string;
  descripcion: string;
  precioVenta: string;
  tipo: TipoProducto;
  unidadMedida: UnidadMedida;
  activo: boolean;
  receta?: { insumoId: string; nombre: string; unidadMedida: UnidadMedida; cantidadRequerida: string }[];
  existencia?: { id: string; cantidadActual: string; nivelMinimo: string };
}

export interface AltaProducto {
  nombre: string;
  descripcion: string;
  precioVenta: string;
  tipo: TipoProducto;
  unidadMedida: UnidadMedida;
  receta?: { insumoId: string; cantidadRequerida: string }[];
  nivelMinimo?: string;
}

export type CambiosProducto = Partial<Pick<AltaProducto, 'nombre' | 'descripcion' | 'precioVenta'>>;

const API = '/api/v1/productos';

@Injectable({ providedIn: 'root' })
export class ProductosApi {
  private readonly http = inject(HttpClient);

  listar(filtro: { q: string; tipo: string; activo: string; page: number }): Observable<Pagina<Producto>> {
    let params = new HttpParams().set('page', filtro.page).set('size', TAMANO_PAGINA);
    for (const clave of ['q', 'tipo', 'activo'] as const) {
      if (filtro[clave]) {
        params = params.set(clave, filtro[clave]);
      }
    }
    return this.http.get<Pagina<Producto>>(API, { params });
  }

  obtener(id: string): Observable<Producto> {
    return this.http.get<Producto>(`${API}/${encodeURIComponent(id)}`);
  }

  insumos(): Observable<Insumo[]> {
    return this.http.get<Insumo[]>('/api/v1/insumos');
  }

  registrar(alta: AltaProducto): Observable<Producto> {
    return this.http.post<Producto>(API, alta);
  }

  editar(id: string, cambios: CambiosProducto): Observable<Producto> {
    return this.http.patch<Producto>(`${API}/${encodeURIComponent(id)}`, cambios);
  }

  desactivar(id: string): Observable<Producto> {
    return this.http.post<Producto>(`${API}/${encodeURIComponent(id)}/desactivacion`, { confirmado: true });
  }
}
