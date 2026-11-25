import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { leerProblema } from '../../../core/http/problema';
import { dinero, fechaHora, sumaDinero } from '../../../shared/formato';
import { Icono } from '../../../shared/ui/icono';
import { CLASE_COMANDA, Comanda, Cuenta, NOMBRE_COMANDA, ServicioApi } from '../../mesas-cuentas/data-access/servicio.api';
import { PasoCantidad } from '../components/paso-cantidad';

/** RF02: cambiar cantidades o eliminar productos. Los consumos adicionales van en una comanda nueva. */
@Component({
  selector: 'app-modificar-comanda',
  imports: [RouterLink, Icono, PasoCantidad],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './modificar-comanda.html',
  styleUrl: './comandas.css',
})
export class ModificarComanda implements OnInit {
  readonly id = input.required<string>();
  readonly comandaId = input.required<string>();

  private readonly api = inject(ServicioApi);
  private readonly router = inject(Router);

  protected readonly cuenta = signal<Cuenta | null>(null);
  protected readonly comanda = signal<Comanda | null>(null);
  /** Cantidad elegida por detalle. Un detalle ausente se elimina al guardar. */
  protected readonly cantidades = signal<Record<string, number>>({});
  protected readonly lineas = computed(() =>
    (this.comanda()?.detalles ?? []).filter((d) => this.cantidades()[d.id] !== undefined),
  );
  protected readonly total = computed(() =>
    sumaDinero(this.lineas().flatMap((d) => Array<string>(this.cantidades()[d.id]).fill(d.precioUnitario))),
  );
  protected readonly editable = computed(() => this.comanda()?.acciones.includes('EDITAR') ?? false);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly guardando = signal(false);

  protected readonly nombre = NOMBRE_COMANDA;
  protected readonly clase = CLASE_COMANDA;
  protected readonly dinero = dinero;
  protected readonly fechaHora = fechaHora;

  ngOnInit(): void {
    this.cargar(false);
  }

  protected cambiar(detalle: string, cantidad: number): void {
    this.cantidades.update((c) => ({ ...c, [detalle]: cantidad }));
  }

  protected quitar(detalle: string): void {
    this.cantidades.update((actuales) => {
      const { [detalle]: _quitada, ...resto } = actuales;
      return resto;
    });
  }

  protected guardar(): void {
    const comanda = this.comanda();
    if (!comanda || this.guardando()) {
      return;
    }
    this.error.set(null);
    this.guardando.set(true);
    const detalles = this.lineas().map((d) => ({ detalleId: d.id, cantidad: this.cantidades()[d.id] }));
    this.api.editar(comanda.id, comanda.version, detalles).subscribe({
      next: () => void this.router.navigate(['/cuentas', this.id()]),
      error: (error: unknown) => {
        const problema = leerProblema(error);
        this.guardando.set(false);
        this.error.set(problema.detalle);
        if (problema.estado === 409 && problema.codigo !== 'ULTIMO_DETALLE') {
          // Muestra el estado vigente y conserva lo editado para revisarlo.
          this.cargar(true);
        }
      },
    });
  }

  private cargar(conservar: boolean): void {
    this.api.cuenta(this.id()).subscribe({
      next: (cuenta) => {
        const comanda = cuenta.comandas.find((c) => c.id === this.comandaId());
        if (!comanda) {
          this.errorCarga.set('La comanda no existe.');
          return;
        }
        const previas = this.cantidades();
        this.cuenta.set(cuenta);
        this.comanda.set(comanda);
        this.cantidades.set(
          Object.fromEntries(
            comanda.detalles
              .filter((d) => !conservar || previas[d.id] !== undefined)
              .map((d) => [d.id, conservar ? previas[d.id] : d.cantidad]),
          ),
        );
      },
      error: (error: unknown) => this.errorCarga.set(leerProblema(error).detalle),
    });
  }
}
