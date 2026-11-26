import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Pagina } from '../../shared/listado';

export type TipoNotificacion =
  'MODIFICACION_COMANDA' | 'CANCELACION_COMANDA' | 'COMANDA_LISTA' | 'ALERTA_INVENTARIO';

export interface Notificacion {
  id: string;
  tipo: TipoNotificacion;
  mensaje: string;
  fechaHora: string;
  leida: boolean;
  comandaId?: string;
  contexto: {
    mesa?: number;
    cuentaId?: string;
    comandaId?: string;
    existenciaId?: string;
    faltante?: string;
    unidadMedida?: string;
  };
}

const API = '/api/v1/notificaciones';

@Injectable({ providedIn: 'root' })
export class NotificacionesApi {
  private readonly http = inject(HttpClient);

  /** `automatica` indica una consulta que el usuario no pidió: no cuenta como actividad. */
  listar(leida: string, page: number, size: number, automatica = false): Observable<Pagina<Notificacion>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (leida) {
      params = params.set('leida', leida);
    }
    return this.http.get<Pagina<Notificacion>>(API, {
      params,
      headers: automatica ? { 'X-Solicitud-Pasiva': 'true' } : {},
    });
  }

  leer(id: string): Observable<void> {
    return this.http.put<void>(`${API}/${encodeURIComponent(id)}/lectura`, null);
  }

  descartar(id: string): Observable<void> {
    return this.http.put<void>(`${API}/${encodeURIComponent(id)}/descarte`, null);
  }
}
