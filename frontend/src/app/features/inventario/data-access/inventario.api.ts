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

export type TipoHistorial = 'ENTRADA_MANUAL' | 'SALIDA_MANUAL' | 'CONSUMO_VENTA' | 'AJUSTE' | 'AJUSTE_RECHAZADO';
export type TipoMovimiento = 'ENTRADA_MANUAL' | 'SALIDA_MANUAL';
export type CausaSalida = 'MERMA' | 'CONSUMO_PERSONAL' | 'DEVOLUCION_PROVEEDOR' | 'OTRO';

/** Por qué sale inventario fuera de una venta. Un faltante sin causa conocida se corrige con un ajuste. */
export const NOMBRE_CAUSA: Record<CausaSalida, string> = {
  MERMA: 'Merma (caducado o dañado)',
  CONSUMO_PERSONAL: 'Consumo del personal',
  DEVOLUCION_PROVEEDOR: 'Devolución a proveedor',
  OTRO: 'Otro',
};

export interface DatosMovimiento {
  tipo: TipoMovimiento;
  cantidad: string;
  causa?: CausaSalida;
  nota?: string;
}

export const NOMBRE_HISTORIAL: Record<TipoHistorial, string> = {
  ENTRADA_MANUAL: 'Entrada',
  SALIDA_MANUAL: 'Salida',
  CONSUMO_VENTA: 'Consumo',
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
  comandaId?: string;
  motivo?: string;
  causa?: CausaSalida;
}

export type EstadoSolicitud = 'PENDIENTE' | 'AUTORIZADA' | 'RECHAZADA';

export const NOMBRE_ESTADO: Record<EstadoSolicitud, string> = {
  PENDIENTE: 'Pendiente',
  AUTORIZADA: 'Autorizada',
  RECHAZADA: 'Rechazada',
};

export const CLASE_ESTADO: Record<EstadoSolicitud, string> = {
  PENDIENTE: 'etiqueta etiqueta--alerta',
  AUTORIZADA: 'estado--autorizada',
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

function pasiva(automatica: boolean): Record<string, string> {
  return automatica ? { 'X-Solicitud-Pasiva': 'true' } : {};
}

@Injectable({ providedIn: 'root' })
export class InventarioApi {
  private readonly http = inject(HttpClient);

  existencias(filtro: Filtro, automatica = false): Observable<Pagina<Existencia>> {
    return this.http.get<Pagina<Existencia>>(`${API}/existencias`, {
      params: parametros(filtro),
      headers: pasiva(automatica),
    });
  }

  existencia(id: string, automatica = false): Observable<Existencia> {
    return this.http.get<Existencia>(`${API}/existencias/${encodeURIComponent(id)}`, {
      headers: pasiva(automatica),
    });
  }

  movimientos(filtro: Filtro, automatica = false): Observable<Pagina<Historial>> {
    return this.http.get<Pagina<Historial>>(`${API}/movimientos`, {
      params: parametros(filtro),
      headers: pasiva(automatica),
    });
  }

  movimientosDe(id: string, filtro: Filtro, automatica = false): Observable<Pagina<Historial>> {
    return this.http.get<Pagina<Historial>>(`${API}/existencias/${encodeURIComponent(id)}/movimientos`, {
      params: parametros(filtro),
      headers: pasiva(automatica),
    });
  }

  registrarMovimiento(
    id: string,
    datos: DatosMovimiento,
    clave: string,
  ): Observable<{ movimiento: Historial; existencia: Existencia }> {
    return this.http.post<{ movimiento: Historial; existencia: Existencia }>(
      `${API}/existencias/${encodeURIComponent(id)}/movimientos`,
      datos,
      { headers: { 'Idempotency-Key': clave } },
    );
  }

  /** RF16: cambia el nivel que marca la existencia como baja. No cambia el saldo. */
  cambiarNivelMinimo(id: string, nivelMinimo: string): Observable<Existencia> {
    return this.http.patch<Existencia>(`${API}/existencias/${encodeURIComponent(id)}`, { nivelMinimo });
  }

  solicitarAjuste(
    datos: { existenciaId: string; cantidadSolicitada: string; justificacion: string },
    clave: string,
  ): Observable<Solicitud> {
    return this.http.post<Solicitud>(`${API}/ajustes`, datos, { headers: { 'Idempotency-Key': clave } });
  }

  ajustes(filtro: Filtro, automatica = false): Observable<Pagina<Solicitud>> {
    return this.http.get<Pagina<Solicitud>>(`${API}/ajustes`, {
      params: parametros(filtro),
      headers: pasiva(automatica),
    });
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
