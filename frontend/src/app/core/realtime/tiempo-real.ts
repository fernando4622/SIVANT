import { Injectable, InjectionToken, computed, effect, inject, signal, untracked } from '@angular/core';
import { Client, IMessage, StompConfig } from '@stomp/stompjs';
import { Observable, Subject, filter, map, merge } from 'rxjs';
import { Rol } from '../auth/rol';
import { Sesion } from '../auth/sesion';

export interface Evento {
  eventoId: string;
  tipo: string;
  recursoId: string;
  version: number;
  fechaHora: string;
}

export const CREAR_CLIENTE = new InjectionToken<(config: StompConfig) => Client>('CREAR_CLIENTE', {
  providedIn: 'root',
  factory: () => (config: StompConfig) => new Client(config),
});

const TEMA_POR_ROL: Partial<Record<Rol, string>> = {
  COCINERO: '/topic/cocina',
  ALMACENISTA: '/topic/inventario',
};
const EVENTOS_RECORDADOS = 200;

/**
 * Canal de eventos confirmados. Un evento solo avisa que algo cambió: cada pantalla consulta el estado por REST.
 * Tras reconectar se avisa también, para recuperar lo que no se recibió.
 */
@Injectable({ providedIn: 'root' })
export class TiempoReal {
  private readonly sesion = inject(Sesion);
  private readonly crearCliente = inject(CREAR_CLIENTE);
  private readonly eventos = new Subject<Evento>();
  private readonly reconexiones = new Subject<void>();
  private readonly recibidos = new Set<string>();
  private cliente: Client | undefined;

  readonly conectado = signal(false);

  constructor() {
    const actor = computed(() => {
      const perfil = this.sesion.perfil();
      return perfil ? `${perfil.id}|${perfil.rol}` : null;
    });
    effect(() => {
      const actual = actor();
      untracked(() => (actual ? this.abrir(actual.split('|')[1] as Rol) : this.cerrar()));
    });
  }

  /** Emite el evento o `null` al reconectar. */
  cambios(...tipos: string[]): Observable<Evento | null> {
    return merge(
      this.eventos.pipe(filter((e) => tipos.length === 0 || tipos.includes(e.tipo))),
      this.reconexiones.pipe(map(() => null)),
    );
  }

  private abrir(rol: Rol): void {
    this.cerrar();
    let primera = true;
    const destinos = ['/user/queue/notificaciones', TEMA_POR_ROL[rol]].filter((d) => d !== undefined);
    const cliente = this.crearCliente({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
      reconnectDelay: 5_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => {
        destinos.forEach((destino) => cliente.subscribe(destino, (mensaje) => this.recibir(mensaje)));
        this.conectado.set(true);
        if (!primera) {
          this.reconexiones.next();
        }
        primera = false;
      },
      onWebSocketClose: () => {
        if (this.cliente === cliente) {
          this.conectado.set(false);
          // Puede haberse cerrado porque la sesión terminó.
          this.sesion.comprobar();
        }
      },
    });
    this.cliente = cliente;
    cliente.activate();
  }

  private cerrar(): void {
    const anterior = this.cliente;
    this.cliente = undefined;
    this.conectado.set(false);
    this.recibidos.clear();
    void anterior?.deactivate();
  }

  private recibir(mensaje: IMessage): void {
    const evento = JSON.parse(mensaje.body) as Evento;
    if (this.recibidos.has(evento.eventoId)) {
      return;
    }
    if (this.recibidos.size >= EVENTOS_RECORDADOS) {
      this.recibidos.clear();
    }
    this.recibidos.add(evento.eventoId);
    this.eventos.next(evento);
  }
}
