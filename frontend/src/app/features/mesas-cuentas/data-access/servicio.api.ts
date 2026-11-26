import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface Mesa {
  id: string;
  numero: number;
  disponible: boolean;
  /** Presente solo si el usuario puede consultar la cuenta. */
  cuentaId?: string;
  mesero?: string;
}

export type EstadoComanda = 'ENVIADA' | 'EN_PREPARACION' | 'LISTA' | 'SERVIDA' | 'CANCELADA';

export const NOMBRE_COMANDA: Record<EstadoComanda, string> = {
  ENVIADA: 'Enviada',
  EN_PREPARACION: 'En preparación',
  LISTA: 'Lista',
  SERVIDA: 'Servida',
  CANCELADA: 'Cancelada',
};

export const CLASE_COMANDA: Record<EstadoComanda, string> = {
  ENVIADA: 'etiqueta etiqueta--alerta',
  EN_PREPARACION: 'etiqueta etiqueta--proceso',
  LISTA: 'etiqueta etiqueta--bien',
  SERVIDA: 'etiqueta',
  CANCELADA: 'etiqueta etiqueta--mal',
};

export interface Detalle {
  id: string;
  productoId: string;
  producto: string;
  cantidad: number;
  precioUnitario: string;
  subtotal: string;
  observaciones: string;
}

export interface Comanda {
  id: string;
  cuentaId: string;
  estado: EstadoComanda;
  fechaHoraEnvio: string;
  version: number;
  subtotal: string;
  detalles: Detalle[];
  cancelacion?: { cajero: string; fechaHora: string; justificacion: string };
  acciones: ('EDITAR' | 'CANCELAR' | 'SERVIR')[];
}

export interface Cuenta {
  id: string;
  mesaId: string;
  mesa: number;
  mesero: string;
  estado: 'ABIERTA' | 'CERRADA';
  fechaHoraApertura: string;
  fechaHoraCierre?: string;
  version: number;
  total: string;
  comandas: Comanda[];
  /** Presente si la cuenta se cerró con un cobro. */
  cobro?: { fechaHora: string; metodo: string; total: string; referenciaExterna?: string; estado: string };
  /** Fondo de cambio vigente. Mientras está pendiente, la cuenta solo se cobra en efectivo. */
  fondo?: { id: string; monto: string; estado: string; cajero: string; fechaHora: string };
  acciones: ('ENVIAR_COMANDA' | 'COBRAR' | 'CERRAR_SIN_COBRO' | 'ENTREGAR_FONDO' | 'ANULAR_FONDO')[];
}

export interface Servida {
  comanda: Comanda;
  consumo: { articulo: string; unidadMedida: string; cantidad: string; insuficiente: boolean }[];
}

export interface LineaNueva {
  productoId: string;
  cantidad: number;
  observaciones: string;
}

const API = '/api/v1';

@Injectable({ providedIn: 'root' })
export class ServicioApi {
  private readonly http = inject(HttpClient);

  mesas(automatica = false): Observable<Mesa[]> {
    return this.http.get<Mesa[]>(`${API}/mesas`, { headers: pasiva(automatica) });
  }

  abrir(mesaId: string, clave: string): Observable<Cuenta> {
    return this.http.post<Cuenta>(`${API}/cuentas`, { mesaId }, con(clave));
  }

  cuenta(id: string, automatica = false): Observable<Cuenta> {
    return this.http.get<Cuenta>(`${API}/cuentas/${encodeURIComponent(id)}`, { headers: pasiva(automatica) });
  }

  cerrarSinCobro(id: string, version: number, clave: string): Observable<Cuenta> {
    return this.http.post<Cuenta>(`${API}/cuentas/${encodeURIComponent(id)}/cierre`, { version }, con(clave));
  }

  enviar(id: string, detalles: LineaNueva[], clave: string): Observable<Comanda> {
    return this.http.post<Comanda>(`${API}/cuentas/${encodeURIComponent(id)}/comandas`, { detalles }, con(clave));
  }

  editar(comanda: string, version: number, detalles: { detalleId: string; cantidad: number }[]): Observable<Cuenta> {
    return this.http.put<Cuenta>(`${API}/comandas/${encodeURIComponent(comanda)}/detalles`, { version, detalles });
  }

  servir(comanda: string, version: number, clave: string): Observable<Servida> {
    return this.http.post<Servida>(
      `${API}/comandas/${encodeURIComponent(comanda)}/servicio`,
      { version },
      con(clave),
    );
  }

  cancelar(comanda: string, version: number, justificacion: string, clave: string): Observable<Comanda> {
    return this.http.post<Comanda>(
      `${API}/comandas/${encodeURIComponent(comanda)}/cancelacion`,
      { version, justificacion },
      con(clave),
    );
  }
}

function con(clave: string) {
  return { headers: { 'Idempotency-Key': clave } };
}

function pasiva(automatica: boolean): Record<string, string> {
  return automatica ? { 'X-Solicitud-Pasiva': 'true' } : {};
}
