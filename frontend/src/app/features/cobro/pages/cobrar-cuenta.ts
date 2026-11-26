import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { startWith } from 'rxjs';
import { ClaveIdempotencia } from '../../../core/http/clave-idempotencia';
import { leerProblema } from '../../../core/http/problema';
import { dinero, fechaHora } from '../../../shared/formato';
import { Icono } from '../../../shared/ui/icono';
import { Cuenta, ServicioApi } from '../../mesas-cuentas/data-access/servicio.api';
import {
  Cobrada,
  DatosCobro,
  MetodoPago,
  NOMBRE_METODO,
  PATRON_REFERENCIA,
  VentasApi,
  pareceTarjeta,
} from '../../ventas/data-access/ventas.api';

/** Intento sin respuesta, o cargo que debe revertirse. Sobrevive a recargar la pestaña. */
interface Pendiente {
  clave?: string;
  datos: DatosCobro;
  revertir?: boolean;
}

const CAMBIOS = ['VERSION_OBSOLETA', 'TOTAL_CAMBIADO'];

/** RF04 y RF05. El cargo con tarjeta se hace en la terminal; aquí se registra su referencia. */
@Component({
  selector: 'app-cobrar-cuenta',
  imports: [ReactiveFormsModule, RouterLink, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <a class="boton-icono" [routerLink]="['/cuentas', id()]" aria-label="Volver a la cuenta">
        <app-icono nombre="atras" [tamano]="30" />
      </a>
      <h1>Cobrar cuenta</h1>
      @if (cuenta(); as c) {
        <span class="mesa">Mesa {{ c.mesa }}</span>
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
    } @else if (cobrada(); as r) {
      <section class="tarjeta recibo" aria-labelledby="titulo-recibo" role="status">
        <h2 id="titulo-recibo"><app-icono nombre="palomita" [tamano]="30" />Cobro registrado</h2>
        <dl class="datos-lista">
          <dt>Venta:</dt>
          <dd>{{ r.venta.id }}</dd>
          <dt>Fecha:</dt>
          <dd>{{ fechaHora(r.venta.fechaHora) }}</dd>
          <dt>Método:</dt>
          <dd>{{ metodos[r.pago.metodo] }}</dd>
          @if (r.pago.referenciaExterna; as referencia) {
            <dt>Referencia:</dt>
            <dd>{{ referencia }}</dd>
          }
        </dl>
        <p class="total"><span>Total cobrado</span><strong>{{ dinero(r.venta.total) }}</strong></p>
        <p>La cuenta quedó cerrada y la mesa {{ r.cuenta.mesa }} está libre.</p>
        <div class="acciones">
          <a class="boton" [routerLink]="['/cuentas', id()]">Ver cuenta</a>
          <a class="boton boton--principal" routerLink="/mesas">Volver a mesas</a>
        </div>
      </section>
    } @else if (cuenta(); as c) {
      <div class="columnas">
        <section class="tarjeta" aria-labelledby="titulo-resumen">
          <h2 id="titulo-resumen">Cuenta {{ c.id }}</h2>
          <p class="dato"><app-icono nombre="calendario" />{{ fechaHora(c.fechaHoraApertura) }}</p>
          <p class="dato"><app-icono nombre="persona" />Mesero: {{ c.mesero }}</p>
          <ul class="lineas">
            @for (comanda of c.comandas; track comanda.id) {
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
          @if (canceladas()) {
            <p class="campo__ayuda">No incluye {{ canceladas() }} comanda(s) cancelada(s).</p>
          }
          <p class="total"><span>Total</span><strong>{{ dinero(c.total) }}</strong></p>
        </section>

        @if (revertir(); as r) {
          <div class="pila">
            <div class="aviso aviso--error" role="alert">
              <app-icono nombre="info" [tamano]="32" />
              <div>
                <p>
                  <strong>La cuenta cambió después del cargo y el cobro no se registró.</strong>
                  Revierte en la terminal el cargo con referencia {{ r.referenciaExterna }} por
                  {{ dinero(r.totalEsperado) }} y vuelve a cobrar el total nuevo de {{ dinero(c.total) }}.
                </p>
                <button type="button" class="boton" (click)="terminarReversion()">Ya lo revertí</button>
              </div>
            </div>
          </div>
        } @else if (c.acciones.includes('COBRAR')) {
          <form class="pila" [formGroup]="formulario" (ngSubmit)="cobrar()" novalidate>
            <fieldset class="metodos">
              <legend>Método de pago</legend>
              @for (m of opciones; track m) {
                <label
                  class="metodo"
                  [class.metodo--activo]="metodo() === m"
                  [class.metodo--bloqueado]="soloEfectivo() && m !== 'EFECTIVO'"
                >
                  <input
                    type="radio"
                    formControlName="metodo"
                    [value]="m"
                    [attr.value]="m"
                    [attr.disabled]="soloEfectivo() && m !== 'EFECTIVO' ? '' : null"
                  />
                  <app-icono [nombre]="m === 'EFECTIVO' ? 'cobro' : m === 'TARJETA' ? 'tarjeta' : 'enviar'" />
                  {{ metodos[m] }}
                </label>
              }
            </fieldset>

            @if (soloEfectivo()) {
              <div class="aviso">
                <app-icono nombre="info" [tamano]="32" />
                <p>
                  La cuenta tiene un fondo de cambio de {{ dinero(c.fondo!.monto) }}: cóbrala en efectivo. El fondo
                  se regresa a caja junto con el efectivo de la venta.
                </p>
              </div>
            }
            <div class="aviso">
              <app-icono nombre="info" [tamano]="32" />
              <p>
                @switch (metodo()) {
                  @case ('TARJETA') {
                    Cobra {{ dinero(c.total) }} en la terminal y captura la referencia de autorización del
                    comprobante. No captures el número de la tarjeta.
                  }
                  @case ('TRANSFERENCIA') {
                    Comprueba que se recibió la transferencia por {{ dinero(c.total) }}. La referencia es opcional.
                  }
                  @default {
                    Recibe {{ dinero(c.total) }} en efectivo.
                  }
                }
                Al registrar el cobro se cierra la cuenta y la mesa queda libre.
              </p>
            </div>

            @if (metodo() !== 'EFECTIVO') {
              <div class="campo">
                <label for="referencia">
                  {{ metodo() === 'TARJETA' ? 'Referencia de autorización' : 'Referencia (opcional)' }}
                </label>
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

            @if (pendiente()) {
              <div class="aviso" role="status">
                <app-icono nombre="info" [tamano]="32" />
                <p>No se confirmó el último intento. Envíalo de nuevo con los mismos datos: no se registrará dos veces.</p>
              </div>
            }
            @if (error(); as mensaje) {
              <p class="campo__error" role="alert">{{ mensaje }}</p>
            }
            <div class="acciones">
              <a class="boton" [routerLink]="['/cuentas', id()]">Volver</a>
              <button type="submit" class="boton boton--principal" [disabled]="guardando()">
                <app-icono nombre="palomita" />
                {{ guardando() ? 'Registrando…' : 'Registrar cobro de ' + dinero(c.total) }}
              </button>
            </div>
          </form>
        } @else {
          <div class="aviso aviso--error" role="alert">
            <app-icono nombre="info" [tamano]="32" />
            <div>
              <p>{{ motivo(c) }}</p>
              <a class="boton" [routerLink]="['/cuentas', id()]">Ver cuenta</a>
            </div>
          </div>
        }
      </div>
    } @else {
      <p role="status">Cargando cuenta…</p>
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

    .metodos {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(9rem, 100%), 1fr));
      gap: 0.75rem;
      margin: 0;
      padding: 0;
      border: 0;
    }

    legend {
      margin-bottom: 0.75rem;
      font-size: 1.25rem;
      font-weight: 650;
    }

    .metodo {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      min-height: 3.5rem;
      padding: 0.75rem 1rem;
      border: 2px solid var(--borde-control);
      border-radius: var(--radio-chico);
      background: var(--superficie);
      cursor: pointer;
    }

    .metodo--bloqueado {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .metodo--activo {
      border-color: var(--azul);
      background: var(--azul-aviso);
    }

    .metodo input {
      accent-color: var(--azul);
    }

    .metodo:has(input:focus-visible) {
      outline: 3px solid var(--azul);
      outline-offset: 2px;
    }

    .recibo {
      max-width: 40rem;
    }

    .recibo h2 {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      color: var(--verde);
    }

    .recibo > p:not(.total) {
      margin: 1rem 0;
    }
  `,
})
export class CobrarCuenta implements OnInit {
  readonly id = input.required<string>();

  private readonly servicio = inject(ServicioApi);
  private readonly ventas = inject(VentasApi);
  private readonly clave = new ClaveIdempotencia();

  protected readonly opciones: MetodoPago[] = ['EFECTIVO', 'TARJETA', 'TRANSFERENCIA'];
  protected readonly metodos = NOMBRE_METODO;
  protected readonly cuenta = signal<Cuenta | null>(null);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly cobrada = signal<Cobrada | null>(null);
  protected readonly revertir = signal<DatosCobro | null>(null);
  protected readonly pendiente = signal(false);
  protected readonly guardando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly errorServidor = signal<string | null>(null);
  protected readonly formulario = inject(NonNullableFormBuilder).group({
    metodo: 'EFECTIVO' as MetodoPago,
    referencia: '',
  });
  private readonly valores = toSignal(
    this.formulario.valueChanges.pipe(startWith(this.formulario.getRawValue())),
    { initialValue: this.formulario.getRawValue() },
  );
  protected readonly metodo = computed(() => this.valores().metodo ?? 'EFECTIVO');
  /** Un fondo de cambio pendiente obliga a cobrar en efectivo. */
  protected readonly soloEfectivo = computed(() => this.cuenta()?.fondo?.estado === 'PENDIENTE');
  protected readonly canceladas = computed(
    () => this.cuenta()?.comandas.filter((c) => c.estado === 'CANCELADA').length ?? 0,
  );
  protected readonly errorReferencia = computed(() => {
    const { metodo, referencia = '' } = this.valores();
    if (!this.enviado() || metodo === 'EFECTIVO') {
      return '';
    }
    if (referencia.trim() === '') {
      return metodo === 'TARJETA' ? 'Escribe la referencia de autorización.' : '';
    }
    if (!PATRON_REFERENCIA.test(referencia)) {
      return 'Usa hasta 40 letras, números o guiones.';
    }
    if (pareceTarjeta(referencia)) {
      return 'No captures el número de la tarjeta. Usa la referencia.';
    }
    return this.errorServidor() ?? '';
  });
  protected readonly dinero = dinero;
  protected readonly fechaHora = fechaHora;

  constructor() {
    this.formulario.valueChanges.subscribe(() => this.errorServidor.set(null));
  }

  ngOnInit(): void {
    const guardado = this.leer();
    if (guardado?.revertir) {
      this.revertir.set(guardado.datos);
    } else if (guardado?.clave) {
      this.formulario.setValue({ metodo: guardado.datos.metodo, referencia: guardado.datos.referenciaExterna });
      this.clave.restaurar(guardado.clave, guardado.datos);
      this.pendiente.set(true);
    }
    this.cargar();
  }

  protected cargar(): void {
    this.servicio.cuenta(this.id()).subscribe({
      next: (cuenta) => {
        this.cuenta.set(cuenta);
        this.errorCarga.set(null);
        if (cuenta.fondo?.estado === 'PENDIENTE') {
          this.formulario.controls.metodo.setValue('EFECTIVO');
        }
      },
      error: (error: unknown) => this.errorCarga.set(leerProblema(error).detalle),
    });
  }

  protected motivo(cuenta: Cuenta): string {
    if (cuenta.cobro) {
      return `Esta cuenta ya se cobró por ${dinero(cuenta.cobro.total)}.`;
    }
    if (cuenta.estado === 'CERRADA') {
      return 'La cuenta ya está cerrada.';
    }
    return 'Para cobrar, todas las comandas no canceladas deben estar servidas.';
  }

  protected cobrar(): void {
    const cuenta = this.cuenta();
    this.enviado.set(true);
    this.error.set(null);
    if (!cuenta || this.errorReferencia() || this.guardando()) {
      return;
    }
    const { metodo, referencia } = this.formulario.getRawValue();
    const datos: DatosCobro = {
      versionCuenta: cuenta.version,
      metodo,
      referenciaExterna: metodo === 'EFECTIVO' ? '' : referencia.trim(),
      totalEsperado: cuenta.total,
    };
    const clave = this.clave.para(datos);
    this.guardar({ clave, datos });
    this.guardando.set(true);
    this.ventas.cobrar(cuenta.id, datos, clave).subscribe({
      next: (cobrada) => {
        this.clave.completar();
        this.guardar(null);
        this.guardando.set(false);
        this.pendiente.set(false);
        this.cobrada.set(cobrada);
      },
      error: (error: unknown) => {
        const problema = leerProblema(error);
        this.guardando.set(false);
        if (problema.estado === 0 || problema.estado >= 500) {
          // Se conserva la clave: reintentar no registra dos veces.
          this.error.set(problema.detalle);
          return;
        }
        this.pendiente.set(false);
        if (problema.estado === 409 && CAMBIOS.includes(problema.codigo) && datos.referenciaExterna) {
          this.guardar({ datos, revertir: true });
          this.revertir.set(datos);
        } else {
          this.guardar(null);
          this.error.set(problema.detalle);
          this.errorServidor.set(problema.erroresCampo['referenciaExterna'] ?? null);
        }
        if (problema.estado === 409) {
          this.cargar();
        }
      },
    });
  }

  protected terminarReversion(): void {
    this.guardar(null);
    this.revertir.set(null);
    this.formulario.controls.referencia.setValue('');
    this.enviado.set(false);
  }

  private get llave(): string {
    return `sivant.cobro.${this.id()}`;
  }

  private leer(): Pendiente | null {
    try {
      const texto = sessionStorage.getItem(this.llave);
      return texto ? (JSON.parse(texto) as Pendiente) : null;
    } catch {
      return null;
    }
  }

  private guardar(pendiente: Pendiente | null): void {
    try {
      if (pendiente) {
        sessionStorage.setItem(this.llave, JSON.stringify(pendiente));
      } else {
        sessionStorage.removeItem(this.llave);
      }
    } catch {
      // Sin almacenamiento solo se pierde el aviso tras recargar; la clave sigue en memoria.
    }
  }
}
