import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { EstadoVenta } from '../../ventas/data-access/ventas.api';

/** Pago en efectivo que caja todavía no recibe. */
export interface PagoEfectivo {
  id: string;
  ventaId: string;
  mesa: number;
  meseroId: string;
  mesero: string;
  fechaHora: string;
  monto: string;
  estadoVenta: EstadoVenta;
}

export interface PagoRecibido {
  id: string;
  ventaId: string;
  mesa: number;
  monto: string;
  montoRecibido: string;
  cambio: string;
}

/** `monto` suma los pagos; `cambio`, lo que caja devolvió. */
export interface Entrega {
  id: string;
  fechaHora: string;
  mesero: string;
  cajero: string;
  monto: string;
  cambio: string;
  pagos: PagoRecibido[];
}

export interface Recepcion {
  meseroId: string;
  pagos: { pagoId: string; montoRecibido: string }[];
}

const API = '/api/v1';

@Injectable({ providedIn: 'root' })
export class CajaApi {
  private readonly http = inject(HttpClient);

  pendientes(): Observable<PagoEfectivo[]> {
    return this.http.get<PagoEfectivo[]>(`${API}/caja/pagos-efectivo-pendientes`);
  }

  recibir(recepcion: Recepcion, clave: string): Observable<Entrega> {
    return this.http.post<Entrega>(`${API}/caja/entregas-efectivo`, recepcion, {
      headers: { 'Idempotency-Key': clave },
    });
  }
}
