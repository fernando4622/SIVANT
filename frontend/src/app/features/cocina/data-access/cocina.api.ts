import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { EstadoComanda } from '../../mesas-cuentas/data-access/servicio.api';

export interface ComandaCocina {
  id: string;
  cuentaId: string;
  mesa: number;
  mesero: string;
  estado: EstadoComanda;
  fechaHoraEnvio: string;
  version: number;
  detalles: { id: string; producto: string; cantidad: number; observaciones: string }[];
}

@Injectable({ providedIn: 'root' })
export class CocinaApi {
  private readonly http = inject(HttpClient);

  cola(automatica = false): Observable<ComandaCocina[]> {
    const headers: Record<string, string> = automatica ? { 'X-Solicitud-Pasiva': 'true' } : {};
    return this.http.get<ComandaCocina[]>('/api/v1/cocina/cola', { headers });
  }

  iniciar(id: string, version: number): Observable<ComandaCocina> {
    return this.http.post<ComandaCocina>(`/api/v1/comandas/${encodeURIComponent(id)}/preparacion`, { version });
  }

  marcarLista(id: string, version: number): Observable<ComandaCocina> {
    return this.http.post<ComandaCocina>(`/api/v1/comandas/${encodeURIComponent(id)}/lista`, { version });
  }
}
