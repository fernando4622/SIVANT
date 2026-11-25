import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { ClaveIdempotencia } from '../../../core/http/clave-idempotencia';
import { leerProblema } from '../../../core/http/problema';
import { TiempoReal } from '../../../core/realtime/tiempo-real';
import { dinero, fechaHora } from '../../../shared/formato';
import { Confirmacion } from '../../../shared/ui/confirmacion';
import { Icono } from '../../../shared/ui/icono';
import { TarjetaComanda } from '../components/tarjeta-comanda';
import { Cuenta, ServicioApi } from '../data-access/servicio.api';

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
  private readonly confirmacion = viewChild.required(Confirmacion);

  protected readonly cuenta = signal<Cuenta | null>(null);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly cerrando = signal(false);
  protected readonly errorCierre = signal<string | null>(null);
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

  protected pedirCierre(): void {
    this.errorCierre.set(null);
    this.confirmacion().abrir();
  }

  protected cerrar(): void {
    const cuenta = this.cuenta();
    if (!cuenta || this.cerrando()) {
      return;
    }
    this.cerrando.set(true);
    this.api.cerrarSinCobro(cuenta.id, cuenta.version, this.clave.para(cuenta.version)).subscribe({
      next: () => {
        this.confirmacion().cerrar();
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
