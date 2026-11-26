import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { Subject, catchError, of, switchMap } from 'rxjs';
import { Sesion } from '../auth/sesion';
import { TiempoReal } from '../realtime/tiempo-real';
import { Notificacion, NotificacionesApi } from './notificaciones.api';

const TIPOS = ['COMANDA_LISTA', 'MODIFICACION_COMANDA', 'CANCELACION_COMANDA', 'ALERTA_INVENTARIO'];
const EMERGENTES = ['COMANDA_LISTA', 'ALERTA_INVENTARIO'];
const VISIBLES = 20;

/** Avisos sin leer, disponibles desde cualquier pantalla. Persisten en el servidor. */
@Injectable({ providedIn: 'root' })
export class Bandeja {
  private readonly api = inject(NotificacionesApi);
  private readonly sesion = inject(Sesion);
  private readonly consulta = new Subject<void>();

  readonly pendientes = signal<Notificacion[]>([]);
  readonly noLeidas = signal(0);
  readonly error = signal(false);
  /** Avisos que deben verse en cualquier pantalla: pedidos listos (mesero) y faltantes (almacenista). */
  readonly reciente = computed(() => this.pendientes().find((n) => EMERGENTES.includes(n.tipo)) ?? null);

  constructor() {
    this.consulta
      .pipe(
        switchMap(() => this.api.listar('false', 0, VISIBLES, true).pipe(catchError(() => of(null)))),
      )
      .subscribe((pagina) => {
        if (pagina && this.sesion.activa()) {
          this.pendientes.set(pagina.contenido);
          this.noLeidas.set(pagina.totalElementos);
        }
      });
    inject(TiempoReal)
      .cambios(...TIPOS)
      .subscribe(() => this.actualizar());
    const actor = computed(() => this.sesion.perfil()?.id ?? null);
    effect(() => {
      const actual = actor();
      untracked(() => {
        this.pendientes.set([]);
        this.noLeidas.set(0);
        if (actual) {
          this.actualizar();
        }
      });
    });
  }

  actualizar(): void {
    this.consulta.next();
  }

  leer(id: string): void {
    this.error.set(false);
    this.api.leer(id).subscribe({
      next: () => this.actualizar(),
      error: () => {
        this.error.set(true);
        this.actualizar();
      },
    });
  }

  descartar(id: string): void {
    this.error.set(false);
    this.api.descartar(id).subscribe({
      next: () => this.actualizar(),
      error: () => {
        this.error.set(true);
        this.actualizar();
      },
    });
  }
}
