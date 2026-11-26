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
      <div class="cobro-layout">
        <div class="cobro-principal">
          <section class="resumen-cuenta" aria-labelledby="titulo-resumen">
            <span class="resumen-cuenta__icono"><app-icono nombre="ventas" [tamano]="30" /></span>
            <div class="resumen-cuenta__datos">
              <h2 id="titulo-resumen">Cuenta {{ c.id }}</h2>
              <p>Mesa {{ c.mesa }}</p>
            </div>

            <div class="desglose" aria-label="Desglose de consumo">
              <ul>
                @for (comanda of c.comandas; track comanda.id) {
                  @if (comanda.estado !== 'CANCELADA') {
                    @for (detalle of comanda.detalles; track detalle.id) {
                      <li>
                        <span class="desglose__cantidad">{{ detalle.cantidad }} ×</span>
                        <span class="desglose__producto">
                          {{ detalle.producto }}
                          @if (detalle.observaciones) {
                            <small>{{ detalle.observaciones }}</small>
                          }
                        </span>
                        <strong>{{ dinero(detalle.subtotal) }}</strong>
                      </li>
                    }
                  }
                }
              </ul>
            </div>
            <strong class="resumen-cuenta__total">{{ dinero(c.total) }}</strong>
          </section>

          @if (canceladas()) {
            <p class="campo__ayuda">El total no incluye {{ canceladas() }} comanda(s) cancelada(s).</p>
          }

          @if (revertir(); as r) {
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
          } @else if (c.acciones.includes('COBRAR')) {
            <form class="formulario-cobro" [formGroup]="formulario" (ngSubmit)="cobrar()" novalidate>
              <fieldset class="metodos">
                <legend>Método de pago</legend>
                @for (m of opciones; track m) {
                  <label class="metodo" [class.metodo--activo]="metodo() === m">
                    <input type="radio" formControlName="metodo" [value]="m" [attr.value]="m" />
                    <app-icono
                      [nombre]="m === 'EFECTIVO' ? 'billete' : m === 'TARJETA' ? 'tarjeta' : 'movil'"
                      [tamano]="27"
                    />
                    <span>{{ metodos[m] }}</span>
                  </label>
                }
              </fieldset>

              <div class="controles-cobro">
                @if (metodo() !== 'EFECTIVO') {
                  <div class="campo">
                    <label for="referencia">
                      {{ metodo() === 'TARJETA' ? 'Referencia de autorización' : 'Referencia de transferencia (opcional)' }}
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

                <div class="estado-pago" role="status">
                  <app-icono [nombre]="guardando() ? 'reactivar' : 'info'" [tamano]="22" />
                  <p>
                    @switch (metodo()) {
                      @case ('TARJETA') {
                        Cobra {{ dinero(c.total) }} en la terminal y captura la referencia de autorización del
                        comprobante.
                      }
                      @case ('TRANSFERENCIA') {
                        Comprueba que se recibió la transferencia por {{ dinero(c.total) }}. La referencia es opcional.
                      }
                      @default {
                        Recibe {{ dinero(c.total) }} en efectivo.
                      }
                    }
                  </p>
                </div>

                @if (pendiente()) {
                  <div class="aviso" role="status">
                    <app-icono nombre="info" [tamano]="32" />
                    <p>No se confirmó el último intento. Envíalo de nuevo con los mismos datos: no se registrará dos veces.</p>
                  </div>
                }
                @if (error(); as mensaje) {
                  <p class="campo__error" role="alert">{{ mensaje }}</p>
                }
                <div class="acciones acciones-cobro">
                  <a class="boton" [routerLink]="['/cuentas', id()]">Volver</a>
                  <button type="submit" class="boton boton--principal" [disabled]="guardando()">
                    <app-icono nombre="palomita" />
                    {{ guardando() ? 'Cobrando…' : 'Cobrar ' + dinero(c.total) }}
                  </button>
                </div>
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

        @if (!revertir() && c.acciones.includes('COBRAR')) {
          <aside class="panel-metodo" aria-live="polite">
            @switch (metodo()) {
              @case ('TARJETA') {
                <div class="terminal" aria-hidden="true">
                  <div class="terminal__pantalla"><app-icono nombre="tarjeta" [tamano]="42" /></div>
                  <div class="terminal__teclado">
                    <i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i>
                    <i class="tecla-roja"></i><i class="tecla-amarilla"></i><i class="tecla-verde"></i>
                  </div>
                </div>
                <h2>Acerca o inserta la tarjeta</h2>
                <p>Procesa {{ dinero(c.total) }} en la terminal.</p>
              }
              @case ('TRANSFERENCIA') {
                <div class="telefono" aria-hidden="true">
                  <span class="telefono__altavoz"></span>
                  <div class="telefono__pantalla">
                    <span class="telefono__estado"><app-icono nombre="palomita" [tamano]="22" /></span>
                    <strong>{{ dinero(c.total) }}</strong>
                    <span>Transferencia recibida</span>
                  </div>
                </div>
                <h2>Confirma la transferencia</h2>
                <p>Verifica que se recibieron {{ dinero(c.total) }} antes de registrar el cobro.</p>
              }
              @default {
                <div class="efectivo" aria-hidden="true">
                  <span class="efectivo__billete efectivo__billete--fondo"></span>
                  <span class="efectivo__billete efectivo__billete--medio"></span>
                  <span class="efectivo__billete efectivo__billete--frente">
                    <i>$</i>
                    <b></b>
                  </span>
                  <span class="efectivo__moneda">$</span>
                </div>
                <h2>Recibe el efectivo</h2>
                <p>Recibe {{ dinero(c.total) }} y lleva el efectivo a caja: el cajero registra lo recibido y te da el cambio.</p>
              }
            }
          </aside>
        }
      </div>
    } @else {
      <p role="status">Cargando cuenta…</p>
    }
  `,
  styleUrl: '../../comandas/pages/comandas.css',
  styles: `
    .cobro-layout {
      display: grid;
      grid-template-columns: minmax(0, 1.15fr) minmax(18rem, 0.85fr);
      gap: 1.25rem;
      align-items: start;
    }

    .cobro-principal,
    .formulario-cobro {
      display: contents;
    }

    .resumen-cuenta {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr);
      align-items: start;
      gap: 1rem;
      min-height: 6.5rem;
      padding: 1rem 1.25rem;
      border: 2px solid var(--borde);
      border-radius: var(--radio);
      background: var(--superficie);
      grid-column: 1;
      grid-row: 1;
    }

    .resumen-cuenta__icono,
    .panel-metodo__icono {
      display: grid;
      place-items: center;
      width: 3.5rem;
      height: 3.5rem;
      border-radius: 50%;
      background: var(--azul-aviso);
      color: var(--azul);
    }

    .resumen-cuenta__datos {
      display: grid;
      gap: 0.25rem;
      min-width: 0;
    }

    .resumen-cuenta__datos p {
      color: var(--texto-suave);
    }

    .resumen-cuenta__total {
      grid-column: 2;
      justify-self: end;
      padding-top: 0.15rem;
      text-align: right;
      font-size: clamp(1.8rem, 1.3rem + 1.8vw, 2.6rem);
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }

    .metodos {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 0.75rem;
      margin: 0;
      padding: 0;
      border: 0;
      grid-column: 1;
      grid-row: 2;
    }

    legend {
      margin-bottom: 0.5rem;
      font-size: 1.1rem;
      font-weight: 650;
    }

    .metodo {
      position: relative;
      display: grid;
      justify-items: center;
      align-items: center;
      align-content: center;
      gap: 0.55rem;
      min-height: 4.75rem;
      padding: 0.55rem 0.5rem;
      border: 2px solid var(--borde-control);
      border-radius: var(--radio);
      background: var(--superficie);
      text-align: center;
      cursor: pointer;
      transition:
        border-color 150ms ease,
        background 150ms ease,
        color 150ms ease;
    }

    .metodo:hover {
      border-color: var(--azul);
    }

    .metodo--activo {
      border-color: var(--azul);
      background: var(--azul-aviso);
      color: var(--azul-fuerte);
    }

    .metodo input {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      opacity: 0;
    }

    .metodo:has(input:focus-visible) {
      border-color: var(--azul);
      box-shadow: 0 0 0 1px var(--azul);
    }

    .estado-pago {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      padding: 0.75rem 1rem;
      border-radius: var(--radio);
      background: var(--azul-aviso);
      color: var(--azul-fuerte);
    }

    .estado-pago app-icono {
      flex: none;
    }

    .desglose {
      grid-column: 2;
      min-width: 0;
    }

    .desglose ul {
      margin: 0;
      padding: 0;
      border-top: 1px solid var(--borde);
      list-style: none;
    }

    .desglose li {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      gap: 0.65rem;
      padding: 0.7rem 0;
      border-bottom: 1px solid var(--borde);
    }

    .desglose__cantidad {
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }

    .desglose__producto {
      overflow-wrap: anywhere;
    }

    .desglose__producto small {
      display: block;
      color: var(--texto-suave);
    }

    .desglose li strong {
      color: var(--texto);
      font-variant-numeric: tabular-nums;
    }

    .controles-cobro {
      display: grid;
      grid-column: 2;
      grid-row: 2;
      align-content: start;
      gap: 0.7rem;
    }

    .acciones-cobro .boton--principal {
      flex: 1 1 15rem;
    }

    .panel-metodo {
      display: grid;
      place-items: center;
      align-content: center;
      gap: 0.85rem;
      width: 100%;
      grid-column: 2;
      grid-row: 1;
      height: 100%;
      max-height: 22rem;
      min-height: 0;
      overflow: hidden;
      padding: clamp(1rem, 2.5vh, 2rem);
      border: 2px solid var(--borde);
      border-radius: var(--radio);
      background: var(--cabecera);
      text-align: center;
    }

    .panel-metodo > p {
      max-width: 24rem;
      color: var(--texto-suave);
    }

    .panel-metodo__icono {
      width: 7rem;
      height: 7rem;
      background: var(--superficie);
      box-shadow: var(--sombra);
    }

    .terminal {
      width: 8.5rem;
      padding: 0.65rem;
      border: 0.35rem solid #242b3a;
      border-radius: 1.1rem;
      background: #343d50;
      box-shadow: 0.8rem 1rem 1.25rem rgb(15 26 58 / 22%);
      rotate: 5deg;
    }

    .terminal__pantalla {
      display: grid;
      place-items: center;
      height: 4.75rem;
      border: 0.4rem solid #1d2431;
      border-radius: 0.65rem;
      background: #73809a;
      color: #fff;
    }

    .terminal__teclado {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 0.3rem;
      padding: 0.6rem 0.15rem 0.2rem;
    }

    .terminal__teclado i {
      height: 0.65rem;
      border-radius: 0.22rem;
      background: #8d98ad;
    }

    .terminal__teclado .tecla-roja {
      background: #ef5350;
    }

    .terminal__teclado .tecla-amarilla {
      background: #fbc83d;
    }

    .terminal__teclado .tecla-verde {
      background: #50c878;
    }

    .telefono {
      position: relative;
      display: grid;
      place-items: stretch;
      width: 7.25rem;
      height: 10.75rem;
      padding: 0.22rem;
      border: 0.12rem solid #171c27;
      border-radius: 1.45rem;
      background: #171c27;
      box-shadow: 0.7rem 0.85rem 1.2rem rgb(15 26 58 / 20%);
      rotate: -3deg;
    }

    .telefono__altavoz {
      position: absolute;
      left: 50%;
      top: 0.55rem;
      z-index: 1;
      width: 2.5rem;
      height: 0.5rem;
      border-radius: 999px;
      background: #171c27;
      translate: -50% 0;
    }

    .telefono__pantalla {
      display: grid;
      place-items: center;
      align-content: center;
      gap: 0.35rem;
      width: 100%;
      height: 100%;
      padding: 1.25rem 0.5rem 0.65rem;
      border-radius: 1.22rem;
      background: var(--superficie);
      color: var(--texto);
      text-align: center;
    }

    .telefono__pantalla strong {
      color: var(--azul-fuerte);
      font-size: 1.1rem;
      font-variant-numeric: tabular-nums;
    }

    .telefono__pantalla > span:last-child {
      color: var(--texto-suave);
      font-size: 0.68rem;
      line-height: 1.2;
    }

    .telefono__estado {
      display: grid;
      place-items: center;
      width: 2.2rem;
      height: 2.2rem;
      border-radius: 50%;
      background: var(--verde-fondo);
      color: var(--verde);
    }

    .efectivo {
      position: relative;
      width: 10rem;
      height: 10.75rem;
    }

    .efectivo__billete {
      position: absolute;
      left: 50%;
      top: 50%;
      display: grid;
      place-items: center;
      width: 8.75rem;
      height: 5rem;
      border: 0.25rem solid #286746;
      border-radius: 0.75rem;
      background: #8fd3aa;
      box-shadow: 0.55rem 0.7rem 1rem rgb(15 26 58 / 16%);
    }

    .efectivo__billete::before,
    .efectivo__billete::after {
      position: absolute;
      width: 0.7rem;
      height: 0.7rem;
      border: 0.14rem solid #286746;
      border-radius: 50%;
      content: '';
    }

    .efectivo__billete::before {
      left: 0.5rem;
      top: 0.5rem;
    }

    .efectivo__billete::after {
      right: 0.5rem;
      bottom: 0.5rem;
    }

    .efectivo__billete--fondo {
      translate: -56% -74%;
      rotate: -10deg;
      background: #b9e3c8;
    }

    .efectivo__billete--medio {
      translate: -44% -57%;
      rotate: 8deg;
      background: #a3dab7;
    }

    .efectivo__billete--frente {
      translate: -50% -45%;
    }

    .efectivo__billete--frente i {
      display: grid;
      place-items: center;
      width: 2.6rem;
      height: 2.6rem;
      border: 0.18rem solid #286746;
      border-radius: 50%;
      color: #20553a;
      font-size: 1.45rem;
      font-style: normal;
      font-weight: 700;
    }

    .efectivo__billete--frente b {
      position: absolute;
      left: 0.65rem;
      bottom: 0.55rem;
      width: 1.4rem;
      height: 0.22rem;
      border-radius: 999px;
      background: #286746;
    }

    .efectivo__moneda {
      position: absolute;
      right: -0.15rem;
      bottom: 0.2rem;
      display: grid;
      place-items: center;
      width: 2.8rem;
      height: 2.8rem;
      border: 0.25rem solid #bd7a12;
      border-radius: 50%;
      background: #fbc83d;
      color: #83540c;
      font-size: 1.15rem;
      font-weight: 700;
      box-shadow: 0.35rem 0.45rem 0.75rem rgb(15 26 58 / 18%);
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

    .recibo .total {
      display: flex;
      justify-content: space-between;
      gap: 1rem;
      margin-top: 1rem;
      font-size: 1.4rem;
      font-weight: 650;
    }

    .recibo > p:not(.total) {
      margin: 1rem 0;
    }

    @media (max-width: 60rem) {
      .cobro-layout {
        grid-template-columns: minmax(0, 1fr);
        align-items: stretch;
      }

      .resumen-cuenta,
      .metodos,
      .controles-cobro,
      .panel-metodo {
        grid-column: 1;
        grid-row: auto;
      }

      .panel-metodo {
        height: auto;
        min-height: 18rem;
      }
    }

    @media (min-width: 60.01rem) and (max-height: 45rem) {
      .pantalla__encabezado {
        margin-bottom: 0.75rem;
        padding-bottom: 0.75rem;
      }

      .resumen-cuenta__icono {
        width: 3rem;
        height: 3rem;
      }

      .campo {
        gap: 0.2rem;
      }

      .control {
        min-height: 2.65rem;
      }

      .acciones-cobro .boton {
        min-height: 2.65rem;
      }
    }

    @media (max-width: 36rem) {
      .metodos {
        grid-template-columns: repeat(auto-fit, minmax(7rem, 1fr));
      }
    }
  `,
})
export class CobrarCuenta implements OnInit {
  readonly id = input.required<string>();

  private readonly servicio = inject(ServicioApi);
  private readonly ventas = inject(VentasApi);
  private readonly clave = new ClaveIdempotencia();

  protected readonly opciones: MetodoPago[] = ['TARJETA', 'EFECTIVO', 'TRANSFERENCIA'];
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
