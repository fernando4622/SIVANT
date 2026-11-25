import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Pagina, TAMANO_PAGINA } from '../../../shared/listado';
import { Comanda, Cuenta } from '../../mesas-cuentas/data-access/servicio.api';

export type MetodoPago = 'EFECTIVO' | 'TARJETA' | 'TRANSFERENCIA';
export type EstadoVenta = 'COMPLETADA' | 'CANCELADA';

export const NOMBRE_METODO: Record<MetodoPago, string> = {
  EFECTIVO: 'Efectivo',
  TARJETA: 'Tarjeta',
  TRANSFERENCIA: 'Transferencia',
};

export const NOMBRE_VENTA: Record<EstadoVenta, string> = {
  COMPLETADA: 'Completada',
  CANCELADA: 'Cancelada',
};

export const CLASE_VENTA: Record<EstadoVenta, string> = {
  COMPLETADA: 'etiqueta etiqueta--bien',
  CANCELADA: 'etiqueta etiqueta--mal',
};

export interface Pago {
  id: string;
  metodo: MetodoPago;
  monto: string;
  referenciaExterna?: string;
  fechaHora: string;
  mesero: string;
}

export interface Cobrada {
  venta: { id: string; fechaHora: string; total: string; estado: EstadoVenta };
  pago: Pago;
  cuenta: Cuenta;
}

export interface DatosCobro {
  versionCuenta: number;
  metodo: MetodoPago;
  referenciaExterna: string;
  totalEsperado: string;
}

export interface ResumenVenta {
  id: string;
  fechaHora: string;
  mesa: number;
  mesero: string;
  metodo: MetodoPago;
  total: string;
  estado: EstadoVenta;
}

export interface Venta {
  id: string;
  cuentaId: string;
  mesa: number;
  mesero: string;
  fechaHora: string;
  estado: EstadoVenta;
  total: string;
  pago: Pago;
  cancelacion?: {
    administrador: string;
    fechaHora: string;
    justificacion: string;
    montoDevuelto: string;
    referenciaReversion?: string;
  };
  comandas: Comanda[];
  acciones: 'CANCELAR'[];
}

export interface Opciones {
  meseros: { id: string; nombre: string }[];
  mesas: { id: string; nombre: string }[];
}

export type FiltroVentas = Record<'desde' | 'hasta' | 'estado' | 'metodo' | 'meseroId' | 'mesaId', string> & {
  page: number;
};

const API = '/api/v1';

/** Referencia que da la terminal o el banco: letras, números y guiones. */
export const PATRON_REFERENCIA = /^\s*[A-Za-z0-9-]{1,40}\s*$/;

/** De 13 a 19 dígitos que cumplen Luhn: probablemente es el número de una tarjeta, que nunca se captura. */
export function pareceTarjeta(referencia: string): boolean {
  const digitos = referencia.trim().replaceAll('-', '');
  if (!/^\d{13,19}$/.test(digitos)) {
    return false;
  }
  let suma = 0;
  [...digitos].reverse().forEach((c, i) => {
    let digito = Number(c);
    if (i % 2 === 1) {
      digito = digito > 4 ? digito * 2 - 9 : digito * 2;
    }
    suma += digito;
  });
  return suma % 10 === 0;
}

@Injectable({ providedIn: 'root' })
export class VentasApi {
  private readonly http = inject(HttpClient);

  cobrar(cuenta: string, datos: DatosCobro, clave: string): Observable<Cobrada> {
    const cuerpo = { ...datos, referenciaExterna: datos.referenciaExterna.trim() || null };
    return this.http.post<Cobrada>(`${API}/cuentas/${encodeURIComponent(cuenta)}/cobro`, cuerpo, {
      headers: { 'Idempotency-Key': clave },
    });
  }

  ventas(filtro: FiltroVentas): Observable<Pagina<ResumenVenta>> {
    let params = new HttpParams().set('size', TAMANO_PAGINA);
    for (const [nombre, valor] of Object.entries(filtro)) {
      if (valor !== '') {
        params = params.set(nombre, valor);
      }
    }
    return this.http.get<Pagina<ResumenVenta>>(`${API}/ventas`, { params });
  }

  opciones(): Observable<Opciones> {
    return this.http.get<Opciones>(`${API}/ventas/opciones`);
  }

  venta(id: string): Observable<Venta> {
    return this.http.get<Venta>(`${API}/ventas/${encodeURIComponent(id)}`);
  }

  cancelar(id: string, justificacion: string, referenciaReversion: string, clave: string): Observable<Venta> {
    return this.http.post<Venta>(
      `${API}/ventas/${encodeURIComponent(id)}/cancelacion`,
      { confirmado: true, justificacion, referenciaReversion: referenciaReversion.trim() || null },
      { headers: { 'Idempotency-Key': clave } },
    );
  }
}
