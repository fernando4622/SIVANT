import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClaveIdempotencia } from '../../../core/http/clave-idempotencia';
import { leerProblema } from '../../../core/http/problema';
import { dinero, fechaHora } from '../../../shared/formato';
import { Icono } from '../../../shared/ui/icono';
import { NOMBRE_METODO, PATRON_REFERENCIA, Venta, VentasApi, pareceTarjeta } from '../data-access/ventas.api';

/** RF22: el administrador registra la devolución completa ya realizada. Pago, cuenta y consumos se conservan. */
@Component({
  selector: 'app-cancelar-venta',
  imports: [ReactiveFormsModule, RouterLink, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <a class="boton-icono" [routerLink]="['/ventas', id()]" aria-label="Volver a la venta">
        <app-icono nombre="atras" [tamano]="30" />
      </a>
      <h1>Cancelar venta</h1>
      @if (venta(); as v) {
        <span class="mesa">Mesa {{ v.mesa }}</span>
      }
    </div>

    @if (errorCarga(); as mensaje) {
      <div class="aviso aviso--error" role="alert">
        <app-icono nombre="info" [tamano]="32" />
        <p>{{ mensaje }}</p>
      </div>
    } @else if (venta(); as v) {
      <div class="columnas">
        <section class="tarjeta" aria-labelledby="titulo-venta">
          <h2 id="titulo-venta">Venta {{ v.id }}</h2>
          <p class="dato"><app-icono nombre="calendario" />{{ fechaHora(v.fechaHora) }}</p>
          <p class="dato"><app-icono nombre="persona" />Mesero: {{ v.mesero }}</p>
          <ul class="lineas">
            @for (comanda of v.comandas; track comanda.id) {
              @if (comanda.estado !== 'CANCELADA') {
                @for (d of comanda.detalles; track d.id) {
                  <li>
                    <span>{{ d.cantidad }} ×</span>
                    <span class="articulo">{{ d.producto }}</span>
                    <strong>{{ dinero(d.subtotal) }}</strong>
                  </li>
                }
              }
            }
          </ul>
          <dl class="datos-lista">
            <dt>Método:</dt>
            <dd>{{ metodos[v.pago.metodo] }}</dd>
            @if (v.pago.referenciaExterna; as referencia) {
              <dt>Referencia del cobro:</dt>
              <dd>{{ referencia }}</dd>
            }
          </dl>
          <p class="total"><span>Total</span><strong>{{ dinero(v.total) }}</strong></p>
        </section>

        @if (v.acciones.includes('CANCELAR')) {
          <form class="pila" [formGroup]="formulario" (ngSubmit)="cancelar()" novalidate>
            <div class="aviso">
              <app-icono nombre="info" [tamano]="32" />
              <p>
                @if (efectivo()) {
                  Entrega {{ dinero(v.total) }} al cliente desde caja antes de confirmar.
                } @else {
                  Revierte {{ dinero(v.total) }} en {{ v.pago.metodo === 'TARJETA' ? 'la terminal' : 'el banco' }} y
                  captura la referencia que te den.
                }
                El inventario no se repone: lo servido ya se consumió.
              </p>
            </div>

            <div class="campo">
              <label for="justificacion">Motivo de cancelación</label>
              <textarea
                id="justificacion"
                class="control"
                formControlName="justificacion"
                placeholder="Describe el motivo de la cancelación…"
                [attr.aria-invalid]="!!errorJustificacion()"
                aria-describedby="justificacion-error"
              ></textarea>
              <span id="justificacion-error" class="campo__error" aria-live="polite">{{ errorJustificacion() }}</span>
            </div>

            @if (!efectivo()) {
              <div class="campo">
                <label for="referencia">Referencia de la reversión</label>
                <input
                  id="referencia"
                  class="control"
                  formControlName="referencia"
                  autocomplete="off"
                  maxlength="40"
                  [attr.aria-invalid]="!!errorReferencia()"
                  aria-describedby="referencia-error"
                />
                <span id="referencia-error" class="campo__error" aria-live="polite">{{ errorReferencia() }}</span>
              </div>
            }

            <label class="casilla">
              <input type="checkbox" formControlName="confirmado" [attr.aria-invalid]="!!errorConfirmado()" />
              Confirmo que ya se devolvieron {{ dinero(v.total) }} al cliente.
            </label>
            <span class="campo__error" aria-live="polite">{{ errorConfirmado() }}</span>

            <div class="aviso">
              <app-icono nombre="info" [tamano]="32" />
              <p>Esta acción registrará al administrador responsable y no podrá deshacerse.</p>
            </div>

            @if (error(); as mensaje) {
              <p class="campo__error" role="alert">{{ mensaje }}</p>
            }
            <div class="acciones">
              <a class="boton" [routerLink]="['/ventas', id()]">Volver</a>
              <button type="submit" class="boton boton--peligro" [disabled]="guardando()">
                <app-icono nombre="basura" />
                {{ guardando() ? 'Cancelando…' : 'Cancelar venta' }}
              </button>
            </div>
          </form>
        } @else {
          <div class="aviso aviso--error" role="alert">
            <app-icono nombre="info" [tamano]="32" />
            <p>{{ error() ?? 'Esta venta ya no puede cancelarse.' }}</p>
          </div>
        }
      </div>
    } @else {
      <p role="status">Cargando venta…</p>
    }
  `,
  styleUrl: '../../comandas/pages/comandas.css',
  styles: `
    .dato {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin-top: 0.75rem;
    }

    .lineas li {
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
    }

    .articulo {
      overflow-wrap: anywhere;
    }

    .total {
      display: flex;
      justify-content: space-between;
      gap: 1rem;
      margin-top: 1rem;
      font-size: 1.4rem;
      font-weight: 650;
    }

    .casilla {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      font-weight: 600;
    }

    .casilla input {
      flex: none;
      width: 1.4rem;
      height: 1.4rem;
      margin-top: 0.15rem;
      accent-color: var(--azul);
    }
  `,
})
export class CancelarVenta implements OnInit {
  readonly id = input.required<string>();

  private readonly api = inject(VentasApi);
  private readonly router = inject(Router);
  private readonly clave = new ClaveIdempotencia();

  protected readonly venta = signal<Venta | null>(null);
  protected readonly efectivo = computed(() => this.venta()?.pago.metodo === 'EFECTIVO');
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly errores = signal<Record<string, string>>({});
  protected readonly enviado = signal(false);
  protected readonly guardando = signal(false);
  protected readonly formulario = inject(NonNullableFormBuilder).group({
    justificacion: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(500)]],
    referencia: '',
    confirmado: false,
  });
  protected readonly metodos = NOMBRE_METODO;
  protected readonly dinero = dinero;
  protected readonly fechaHora = fechaHora;

  protected errorJustificacion(): string {
    if (this.enviado() && this.formulario.controls.justificacion.invalid) {
      return 'Escribe el motivo, con 500 caracteres como máximo.';
    }
    return this.errores()['justificacion'] ?? '';
  }

  protected errorReferencia(): string {
    const referencia = this.formulario.controls.referencia.value;
    if (this.enviado() && !this.efectivo()) {
      if (!PATRON_REFERENCIA.test(referencia)) {
        return referencia.trim() ? 'Usa hasta 40 letras, números o guiones.' : 'Escribe la referencia de la reversión.';
      }
      if (pareceTarjeta(referencia)) {
        return 'No captures el número de la tarjeta. Usa la referencia.';
      }
    }
    return this.errores()['referenciaReversion'] ?? '';
  }

  protected errorConfirmado(): string {
    return this.enviado() && !this.formulario.controls.confirmado.value ? 'Confirma la devolución.' : '';
  }

  ngOnInit(): void {
    this.formulario.valueChanges.subscribe(() => this.errores.set({}));
    this.cargar();
  }

  protected cancelar(): void {
    const venta = this.venta();
    this.enviado.set(true);
    this.error.set(null);
    this.errores.set({});
    if (!venta || this.errorJustificacion() || this.errorReferencia() || this.errorConfirmado() || this.guardando()) {
      return;
    }
    const justificacion = this.formulario.controls.justificacion.value;
    const referencia = this.efectivo() ? '' : this.formulario.controls.referencia.value;
    this.guardando.set(true);
    this.api.cancelar(venta.id, justificacion, referencia, this.clave.para([justificacion, referencia])).subscribe({
      next: () => void this.router.navigate(['/ventas', venta.id]),
      error: (error: unknown) => {
        const problema = leerProblema(error);
        this.guardando.set(false);
        this.error.set(problema.detalle);
        this.errores.set(problema.erroresCampo);
        if (problema.estado === 409) {
          this.cargar();
        }
      },
    });
  }

  private cargar(): void {
    this.api.venta(this.id()).subscribe({
      next: (venta) => {
        this.venta.set(venta);
        this.errorCarga.set(null);
      },
      error: (error: unknown) => this.errorCarga.set(leerProblema(error).detalle),
    });
  }
}
