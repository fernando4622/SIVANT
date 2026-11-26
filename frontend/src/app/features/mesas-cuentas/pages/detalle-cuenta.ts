import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { ClaveIdempotencia } from '../../../core/http/clave-idempotencia';
import { leerProblema } from '../../../core/http/problema';
import { TiempoReal } from '../../../core/realtime/tiempo-real';
import { dinero, fechaHora } from '../../../shared/formato';
import { Confirmacion } from '../../../shared/ui/confirmacion';
import { Icono } from '../../../shared/ui/icono';
import { NOMBRE_METODO } from '../../ventas/data-access/ventas.api';
import { TarjetaComanda } from '../components/tarjeta-comanda';
import { Comanda, Cuenta, Servida, ServicioApi } from '../data-access/servicio.api';

@Component({
  selector: 'app-detalle-cuenta',
  imports: [RouterLink, Icono, Confirmacion, TarjetaComanda],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './detalle-cuenta.html',
  styleUrl: './detalle-cuenta.css',
})
export class DetalleCuenta implements OnInit {
  readonly id = input.required<string>();

  private readonly api = inject(ServicioApi);
  private readonly router = inject(Router);
  private readonly clave = new ClaveIdempotencia();
  private readonly claveServicio = new ClaveIdempotencia();
  private readonly confirmacionCierre = viewChild.required<Confirmacion>('cierre');
  private readonly confirmacionServicio = viewChild.required<Confirmacion>('servicio');

  protected readonly cuenta = signal<Cuenta | null>(null);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly cerrando = signal(false);
  protected readonly errorCierre = signal<string | null>(null);
  protected readonly porServir = signal<Comanda | null>(null);
  protected readonly sirviendo = signal(false);
  protected readonly errorServicio = signal<string | null>(null);
  protected readonly servida = signal<Servida | null>(null);
  protected readonly faltantes = computed(() =>
    (this.servida()?.consumo ?? [])
      .filter((c) => c.insuficiente)
      .map((c) => c.articulo)
      .join(', '),
  );
  /** La cuenta tiene consumo pendiente de servir: todavía no puede cobrarse. */
  protected readonly porServirAun = computed(() => {
    const cuenta = this.cuenta();
    return (
      !!cuenta &&
      cuenta.estado === 'ABIERTA' &&
      cuenta.comandas.some((c) => c.estado !== 'CANCELADA' && c.estado !== 'SERVIDA')
    );
  });
  protected readonly metodos = NOMBRE_METODO as Partial<Record<string, string>>;
  protected readonly dinero = dinero;
  protected readonly fechaHora = fechaHora;

  constructor() {
    inject(TiempoReal)
      .cambios()
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.cargar(true));
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(automatica = false): void {
    this.api.cuenta(this.id(), automatica).subscribe({
      next: (cuenta) => {
        this.cuenta.set(cuenta);
        this.errorCarga.set(null);
      },
      error: (error: unknown) => this.errorCarga.set(leerProblema(error).detalle),
    });
  }

  protected pedirServicio(comanda: Comanda): void {
    this.errorServicio.set(null);
    this.servida.set(null);
    this.porServir.set(comanda);
    this.confirmacionServicio().abrir();
  }

  /** RF23. El descuento de inventario ocurre en el servidor (RF24). */
  protected servir(): void {
    const comanda = this.porServir();
    if (!comanda || this.sirviendo()) {
      return;
    }
    this.sirviendo.set(true);
    const clave = this.claveServicio.para([comanda.id, comanda.version]);
    this.api.servir(comanda.id, comanda.version, clave).subscribe({
      next: (servida) => {
        this.claveServicio.completar();
        this.sirviendo.set(false);
        this.confirmacionServicio().cerrar();
        this.servida.set(servida);
        this.cargar();
      },
      error: (error: unknown) => {
        const problema = leerProblema(error);
        this.sirviendo.set(false);
        this.errorServicio.set(problema.detalle);
        if (problema.estado === 409) {
          this.cargar();
        }
      },
    });
  }

  protected pedirCierre(): void {
    this.errorCierre.set(null);
    this.confirmacionCierre().abrir();
  }

  protected cerrar(): void {
    const cuenta = this.cuenta();
    if (!cuenta || this.cerrando()) {
      return;
    }
    this.cerrando.set(true);
    this.api.cerrarSinCobro(cuenta.id, cuenta.version, this.clave.para(cuenta.version)).subscribe({
      next: () => {
        this.confirmacionCierre().cerrar();
        void this.router.navigateByUrl('/mesas');
      },
      error: (error: unknown) => {
        this.cerrando.set(false);
        this.errorCierre.set(leerProblema(error).detalle);
        this.cargar();
      },
    });
  }
}
