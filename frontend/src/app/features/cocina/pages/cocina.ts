import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, of, switchMap } from 'rxjs';
import { leerProblema } from '../../../core/http/problema';
import { Bandeja } from '../../../core/notificaciones/bandeja';
import { TiempoReal } from '../../../core/realtime/tiempo-real';
import { fechaHora } from '../../../shared/formato';
import { Icono } from '../../../shared/ui/icono';
import { CLASE_COMANDA, EstadoComanda, NOMBRE_COMANDA } from '../../mesas-cuentas/data-access/servicio.api';
import { CocinaApi, ComandaCocina } from '../data-access/cocina.api';

/** RF19 a RF21. La cola conserva el orden de envío. */
@Component({
  selector: 'app-cocina',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cocina.html',
  styleUrl: './cocina.css',
})
export class Cocina {
  private readonly api = inject(CocinaApi);
  private readonly consulta = new Subject<boolean>();

  protected readonly bandeja = inject(Bandeja);
  protected readonly tiempoReal = inject(TiempoReal);
  protected readonly cola = signal<ComandaCocina[] | null>(null);
  protected readonly filtro = signal<EstadoComanda | ''>('');
  protected readonly visibles = computed(() => {
    const filtro = this.filtro();
    return (this.cola() ?? []).filter((c) => !filtro || c.estado === filtro);
  });
  protected readonly error = signal<string | null>(null);
  protected readonly ocupada = signal<string | null>(null);

  protected readonly nombre = NOMBRE_COMANDA;
  protected readonly clase = CLASE_COMANDA;
  protected readonly fechaHora = fechaHora;
  protected readonly filtros: { valor: EstadoComanda | ''; texto: string }[] = [
    { valor: '', texto: 'Todas' },
    { valor: 'ENVIADA', texto: 'Enviadas' },
    { valor: 'EN_PREPARACION', texto: 'En preparación' },
    { valor: 'LISTA', texto: 'Listas' },
  ];

  constructor() {
    this.consulta
      .pipe(
        switchMap((automatica) =>
          this.api.cola(automatica).pipe(
            catchError((error: unknown) => {
              this.error.set(leerProblema(error).detalle);
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((cola) => {
        if (cola) {
          this.cola.set(cola);
        }
      });
    this.tiempoReal
      .cambios()
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.consulta.next(true));
    this.actualizar();
  }

  protected actualizar(): void {
    this.error.set(null);
    this.consulta.next(false);
  }

  protected contar(estado: EstadoComanda | ''): number {
    return (this.cola() ?? []).filter((c) => !estado || c.estado === estado).length;
  }

  protected avanzar(comanda: ComandaCocina): void {
    if (this.ocupada()) {
      return;
    }
    this.error.set(null);
    this.ocupada.set(comanda.id);
    const operacion =
      comanda.estado === 'ENVIADA'
        ? this.api.iniciar(comanda.id, comanda.version)
        : this.api.marcarLista(comanda.id, comanda.version);
    operacion.subscribe({
      next: (actualizada) => {
        this.ocupada.set(null);
        this.cola.update((cola) => (cola ?? []).map((c) => (c.id === actualizada.id ? actualizada : c)));
      },
      error: (error: unknown) => {
        this.ocupada.set(null);
        this.error.set(leerProblema(error).detalle);
        this.consulta.next(false);
      },
    });
  }
}
