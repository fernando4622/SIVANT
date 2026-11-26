import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { leerProblema } from '../../../core/http/problema';
import { dinero, fechaHora } from '../../../shared/formato';
import { Icono } from '../../../shared/ui/icono';
import { TarjetaComanda } from '../../mesas-cuentas/components/tarjeta-comanda';
import { CLASE_VENTA, NOMBRE_METODO, NOMBRE_VENTA, Venta, VentasApi } from '../data-access/ventas.api';

/** RF06. La cancelación (RF22) solo la ofrece la API al administrador. */
@Component({
  selector: 'app-detalle-venta',
  imports: [RouterLink, Icono, TarjetaComanda],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <a class="boton-icono" routerLink="/ventas" aria-label="Volver a ventas">
        <app-icono nombre="atras" [tamano]="30" />
      </a>
      <h1>Venta {{ id() }}</h1>
      @if (venta(); as v) {
        <span [class]="clase[v.estado]">{{ nombreEstado[v.estado] }}</span>
      }
    </div>

    @if (errorCarga(); as mensaje) {
      <div class="aviso aviso--error" role="alert">
        <app-icono nombre="info" [tamano]="32" />
        <div>
          <p>{{ mensaje }}</p>
          <button type="button" class="boton" (click)="cargar()">Reintentar</button>
        </div>
      </div>
    } @else if (venta(); as v) {
      <div class="columnas">
        <div class="pila">
          @for (comanda of v.comandas; track comanda.id) {
            <app-tarjeta-comanda [comanda]="comanda" />
          }
        </div>

        <div class="pila">
          <section class="tarjeta" aria-labelledby="titulo-pago">
            <h2 id="titulo-pago">Pago</h2>
            <dl class="datos-lista">
              <dt>Fecha:</dt>
              <dd>{{ fechaHora(v.fechaHora) }}</dd>
              <dt>Mesa:</dt>
              <dd>{{ v.mesa }}</dd>
              <dt>Mesero:</dt>
              <dd>{{ v.mesero }}</dd>
              <dt>Método:</dt>
              <dd>{{ metodos[v.pago.metodo] }}</dd>
              @if (v.pago.referenciaExterna; as referencia) {
                <dt>Referencia:</dt>
                <dd>{{ referencia }}</dd>
              }
            </dl>
            <p class="total"><span>Total</span><strong>{{ dinero(v.total) }}</strong></p>
          </section>

          @if (v.cancelacion; as c) {
            <section class="tarjeta" aria-labelledby="titulo-cancelacion">
              <h2 id="titulo-cancelacion">Cancelación</h2>
              <dl class="datos-lista">
                <dt>Administrador:</dt>
                <dd>{{ c.administrador }}</dd>
                <dt>Fecha:</dt>
                <dd>{{ fechaHora(c.fechaHora) }}</dd>
                <dt>Devuelto:</dt>
                <dd>{{ dinero(c.montoDevuelto) }}</dd>
                @if (c.referenciaReversion; as referencia) {
                  <dt>Referencia de reversión:</dt>
                  <dd>{{ referencia }}</dd>
                }
                <dt>Justificación:</dt>
                <dd>{{ c.justificacion }}</dd>
              </dl>
              <p class="campo__ayuda">El pago original y los consumos de inventario se conservan.</p>
            </section>
          }

          @if (v.acciones.includes('CANCELAR')) {
            <a class="boton boton--peligro" [routerLink]="['/ventas', v.id, 'cancelar']">
              <app-icono nombre="basura" />
              Cancelar venta
            </a>
          }
        </div>
      </div>
    } @else {
      <p role="status">Cargando venta…</p>
    }
  `,
  styles: `
    .total {
      display: flex;
      justify-content: space-between;
      gap: 1rem;
      margin-top: 1.25rem;
      padding-top: 1rem;
      border-top: 2px solid var(--borde);
      font-size: 1.4rem;
      font-weight: 650;
    }

    .campo__ayuda {
      margin-top: 1rem;
    }

    @media (max-width: 60rem) {
      .columnas > :last-child {
        order: -1;
      }
    }
  `,
})
export class DetalleVenta implements OnInit {
  readonly id = input.required<string>();

  private readonly api = inject(VentasApi);

  protected readonly venta = signal<Venta | null>(null);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly nombreEstado = NOMBRE_VENTA;
  protected readonly clase = CLASE_VENTA;
  protected readonly metodos = NOMBRE_METODO;
  protected readonly dinero = dinero;
  protected readonly fechaHora = fechaHora;

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.api.venta(this.id()).subscribe({
      next: (venta) => {
        this.venta.set(venta);
        this.errorCarga.set(null);
      },
      error: (error: unknown) => this.errorCarga.set(leerProblema(error).detalle),
    });
  }
}
