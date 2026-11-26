import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Pagina, TAMANO_PAGINA } from '../../../shared/listado';
import { EstadoVenta } from '../../ventas/data-access/ventas.api';

export type EstadoFondo = 'PENDIENTE' | 'APLICADO' | 'LIQUIDADO' | 'ANULADO';

export const NOMBRE_FONDO: Record<EstadoFondo, string> = {
  PENDIENTE: 'Pendiente',
  APLICADO: 'Aplicado al cobro',
  LIQUIDADO: 'Regresado a caja',
  ANULADO: 'Anulado',
};

export const CLASE_FONDO: Record<EstadoFondo, string> = {
  PENDIENTE: 'etiqueta etiqueta--alerta',
  APLICADO: 'etiqueta etiqueta--proceso',
  LIQUIDADO: 'etiqueta etiqueta--bien',
  ANULADO: 'etiqueta etiqueta--mal',
};

export interface Fondo {
  id: string;
  cuentaId: string;
  mesa: number;
  mesero: string;
  cajero: string;
  monto: string;
  fechaHora: string;
  estado: EstadoFondo;
  ventaId?: string;
  entregaId?: string;
  anulacion?: { cajero: string; fechaHora: string; justificacion: string };
}

export interface PagoEfectivo {
  id: string;
  ventaId: string;
  mesa: number;
  meseroId: string;
  mesero: string;
  fechaHora: string;
  monto: string;
  estadoVenta: EstadoVenta;
  /** Fondo de cambio aplicado a este pago, que el mesero regresa con el efectivo. */
  fondo?: string;
}

export interface Entrega {
  id: string;
  fechaHora: string;
  mesero: string;
  cajero: string;
  monto: string;
  montoFondo: string;
  pagos: PagoEfectivo[];
}

export interface Recepcion {
  meseroId: string;
  pagoIds: string[];
  montoRecibido: string;
  fondoRecibido: string;
}

const API = '/api/v1';

@Injectable({ providedIn: 'root' })
export class CajaApi {
  private readonly http = inject(HttpClient);

  fondosDe(cuenta: string): Observable<Fondo[]> {
    return this.http.get<Fondo[]>(`${API}/cuentas/${encodeURIComponent(cuenta)}/fondo-cambio`);
  }

  entregarFondo(cuenta: string, monto: string, clave: string): Observable<Fondo> {
    return this.http.post<Fondo>(`${API}/cuentas/${encodeURIComponent(cuenta)}/fondo-cambio`, { monto }, con(clave));
  }

  anularFondo(cuenta: string, justificacion: string, clave: string): Observable<Fondo> {
    return this.http.post<Fondo>(
      `${API}/cuentas/${encodeURIComponent(cuenta)}/fondo-cambio/anulacion`,
      { justificacion },
      con(clave),
    );
  }

  fondos(estado: string, page: number): Observable<Pagina<Fondo>> {
    let params = new HttpParams().set('size', TAMANO_PAGINA).set('page', page);
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<Pagina<Fondo>>(`${API}/caja/fondos`, { params });
  }

  pendientes(): Observable<PagoEfectivo[]> {
    return this.http.get<PagoEfectivo[]>(`${API}/caja/pagos-efectivo-pendientes`);
  }

  recibir(recepcion: Recepcion, clave: string): Observable<Entrega> {
    return this.http.post<Entrega>(`${API}/caja/entregas-efectivo`, recepcion, con(clave));
  }
}

function con(clave: string) {
  return { headers: { 'Idempotency-Key': clave } };
}
