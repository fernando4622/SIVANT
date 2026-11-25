import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Pagina, TAMANO_PAGINA } from '../../../shared/listado';

export interface Existencia {
  id: string;
  articuloId: string;
  nombre: string;
  tipo: 'INSUMO' | 'PRODUCTO';
  unidadMedida: string;
  activo: boolean;
  cantidadActual: string;
  nivelMinimo: string;
  nivelBajo: boolean;
  fechaActualizacion: string;
  version: number;
}

export type TipoHistorial = 'ENTRADA_MANUAL' | 'SALIDA_MANUAL' | 'AJUSTE' | 'AJUSTE_RECHAZADO';
export type TipoMovimiento = 'ENTRADA_MANUAL' | 'SALIDA_MANUAL';

export const NOMBRE_HISTORIAL: Record<TipoHistorial, string> = {
  ENTRADA_MANUAL: 'Entrada',
  SALIDA_MANUAL: 'Salida',
  AJUSTE: 'Ajuste',
  AJUSTE_RECHAZADO: 'Ajuste rechazado',
};

export interface Historial {
  id: string;
  tipo: TipoHistorial;
  existenciaId: string;
  articulo: string;
  unidadMedida: string;
  cambio: string;
  saldoAnterior: string;
  saldoPosterior?: string;
  fechaHora: string;
  empleado: string;
  solicitudId?: string;
  motivo?: string;
}

export type EstadoSolicitud = 'PENDIENTE' | 'AUTORIZADA' | 'RECHAZADA';

export const NOMBRE_ESTADO: Record<EstadoSolicitud, string> = {
  PENDIENTE: 'Pendiente',
  AUTORIZADA: 'Autorizada',
  RECHAZADA: 'Rechazada',
};

export const CLASE_ESTADO: Record<EstadoSolicitud, string> = {
  PENDIENTE: 'etiqueta etiqueta--alerta',
  AUTORIZADA: 'etiqueta etiqueta--bien',
  RECHAZADA: 'etiqueta etiqueta--mal',
};

export interface Solicitud {
  id: string;
  existenciaId: string;
  articulo: string;
  unidadMedida: string;
  cantidadAnterior: string;
  cantidadSolicitada: string;
  cantidadActual: string;
  justificacion: string;
  estado: EstadoSolicitud;
  almacenista: string;
  fechaHoraSolicitud: string;
  administrador?: string;
  fechaHoraResolucion?: string;
  existenciaCambiada: boolean;
}

type Filtro = Record<string, string | number>;

const API = '/api/v1/inventario';

function parametros(filtro: Filtro): HttpParams {
  let params = new HttpParams().set('size', TAMANO_PAGINA);
  for (const [clave, valor] of Object.entries(filtro)) {
    if (valor !== '') {
      params = params.set(clave, valor);
    }
  }
  return params;
}

@Injectable({ providedIn: 'root' })
export class InventarioApi {
  private readonly http = inject(HttpClient);

  existencias(filtro: Filtro): Observable<Pagina<Existencia>> {
    return this.http.get<Pagina<Existencia>>(`${API}/existencias`, { params: parametros(filtro) });
  }

  existencia(id: string): Observable<Existencia> {
    return this.http.get<Existencia>(`${API}/existencias/${encodeURIComponent(id)}`);
  }

  movimientos(filtro: Filtro): Observable<Pagina<Historial>> {
    return this.http.get<Pagina<Historial>>(`${API}/movimientos`, { params: parametros(filtro) });
  }

  movimientosDe(id: string, filtro: Filtro): Observable<Pagina<Historial>> {
    return this.http.get<Pagina<Historial>>(`${API}/existencias/${encodeURIComponent(id)}/movimientos`, {
      params: parametros(filtro),
    });
  }

  registrarMovimiento(
    id: string,
    datos: { tipo: TipoMovimiento; cantidad: string },
    clave: string,
  ): Observable<{ movimiento: Historial; existencia: Existencia }> {
    return this.http.post<{ movimiento: Historial; existencia: Existencia }>(
      `${API}/existencias/${encodeURIComponent(id)}/movimientos`,
      datos,
      { headers: { 'Idempotency-Key': clave } },
    );
  }

  solicitarAjuste(
    datos: { existenciaId: string; cantidadSolicitada: string; justificacion: string },
    clave: string,
  ): Observable<Solicitud> {
    return this.http.post<Solicitud>(`${API}/ajustes`, datos, { headers: { 'Idempotency-Key': clave } });
  }

  ajustes(filtro: Filtro): Observable<Pagina<Solicitud>> {
    return this.http.get<Pagina<Solicitud>>(`${API}/ajustes`, { params: parametros(filtro) });
  }

  ajuste(id: string): Observable<Solicitud> {
    return this.http.get<Solicitud>(`${API}/ajustes/${encodeURIComponent(id)}`);
  }

  resolver(id: string, decision: 'AUTORIZADA' | 'RECHAZADA', clave: string): Observable<Solicitud> {
    return this.http.post<Solicitud>(
      `${API}/ajustes/${encodeURIComponent(id)}/resolucion`,
      { decision },
      { headers: { 'Idempotency-Key': clave } },
    );
  }
}
