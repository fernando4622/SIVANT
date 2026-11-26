import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ClaveIdempotencia } from '../../../core/http/clave-idempotencia';
import { leerProblema } from '../../../core/http/problema';
import { PATRON_DINERO, dinero, fechaHora, normalizarDecimal, sumaDinero } from '../../../shared/formato';
import { Icono } from '../../../shared/ui/icono';
import { CajaApi, Entrega, PagoEfectivo } from '../data-access/caja.api';

interface Mesero {
  id: string;
  nombre: string;
  pagos: PagoEfectivo[];
}

/** Centavos, para comparar importes sin perder precisión. */
function centavos(importe: string): bigint {
  const [entero, fraccion = ''] = normalizarDecimal(importe).split('.');
  return BigInt(entero + fraccion.padEnd(2, '0'));
}

function deCentavos(valor: bigint): string {
  const texto = valor.toString().padStart(3, '0');
  return `${texto.slice(0, -2)}.${texto.slice(-2)}`;
}

/**
 * RF25 y RF26: el mesero trae a caja el efectivo de un cobro. El cajero captura lo que entregó el cliente y SIVANT
 * calcula el cambio que le devuelve.
 */
@Component({
  selector: 'app-entregas',
  imports: [RouterLink, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <a class="boton-icono" routerLink="/inicio" aria-label="Volver a inicio">
        <app-icono nombre="atras" [tamano]="30" />
      </a>
      <h1>Recibir efectivo</h1>
    </div>

    <div role="status">
      @if (recibida(); as e) {
        <section class="tarjeta comprobante" aria-labelledby="titulo-comprobante">
          <header class="comprobante__encabezado">
            <span class="comprobante__icono"><app-icono nombre="palomita" [tamano]="30" /></span>
            <div>
              <h2 id="titulo-comprobante">Recepción registrada</h2>
              <p>Comprobante {{ e.id }}</p>
            </div>
            @if (e.fechaHora) {
              <time [attr.datetime]="e.fechaHora">{{ fechaHora(e.fechaHora) }}</time>
            }
          </header>

          <dl class="comprobante__datos">
            <div>
              <dt>Mesero</dt>
              <dd>{{ e.mesero }}</dd>
            </div>
            <div>
              <dt>Pagos recibidos</dt>
              <dd>{{ e.pagos.length }}</dd>
            </div>
            <div>
              <dt>Efectivo registrado</dt>
              <dd>{{ dinero(e.monto) }}</dd>
            </div>
          </dl>

          <p class="comprobante__cambio" [class.comprobante__cambio--cero]="e.cambio === '0.00'">
            <span>{{ e.cambio === '0.00' ? 'Sin cambio pendiente' : 'Cambio a entregar' }}</span>
            <strong>{{ dinero(e.cambio) }}</strong>
          </p>
        </section>
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
    } @else if (meseros(); as lista) {
      @if (lista.length) {
        <div class="columnas">
          <section class="pila" aria-labelledby="titulo-pagos">
            <div class="campo">
              <label for="mesero">Mesero que trae el efectivo</label>
              <select id="mesero" class="control" [value]="meseroId()" (change)="elegir($any($event.target).value)">
                @for (m of lista; track m.id) {
                  <option [value]="m.id">{{ m.nombre }} · {{ m.pagos.length }} pago(s)</option>
                }
              </select>
            </div>
            <h2 id="titulo-pagos">Cobros en efectivo por recibir</h2>
            <ul class="pagos">
              @for (p of mesero()?.pagos ?? []; track p.id) {
                <li [class.pago--elegido]="elegidos().has(p.id)">
                  <label class="pago">
                    <input type="checkbox" [checked]="elegidos().has(p.id)" (change)="alternar(p.id)" />
                    <span class="pago__datos">
                      <strong>Venta {{ p.ventaId }} · Mesa {{ p.mesa }}</strong>
                      <span class="campo__ayuda">{{ fechaHora(p.fechaHora) }}</span>
                      @if (p.estadoVenta === 'CANCELADA') {
                        <span class="etiqueta etiqueta--mal">Venta cancelada</span>
                      }
                    </span>
                    <strong class="numero">{{ dinero(p.monto) }}</strong>
                  </label>
                  @if (elegidos().has(p.id)) {
                    <div class="recibido">
                      <div class="campo">
                        <label [for]="'recibido-' + p.id">Recibido del cliente</label>
                        <input
                          [id]="'recibido-' + p.id"
                          class="control"
                          inputmode="decimal"
                          autocomplete="off"
                          [value]="recibidos()[p.id] ?? ''"
                          (input)="capturar(p.id, $any($event.target).value)"
                          [attr.aria-invalid]="!!errorDe(p)"
                          [attr.aria-describedby]="'recibido-error-' + p.id"
                        />
                        <span [id]="'recibido-error-' + p.id" class="campo__error" aria-live="polite">{{ errorDe(p) }}</span>
                      </div>
                      <p class="cambio">
                        <span>Cambio</span>
                        <strong>{{ cambioDe(p) === null ? '—' : dinero(cambioDe(p)!) }}</strong>
                      </p>
                    </div>
                  }
                </li>
              }
            </ul>
          </section>

          <section class="tarjeta pila" aria-labelledby="titulo-resumen">
            <h2 id="titulo-resumen">Resumen</h2>
            <dl class="datos-lista">
              <dt>Pagos seleccionados:</dt>
              <dd>{{ seleccion().length }}</dd>
              <dt>Importe de las ventas:</dt>
              <dd>{{ dinero(totalPagos()) }}</dd>
              <dt>Recibido del cliente:</dt>
              <dd>{{ totalRecibido() === null ? '—' : dinero(totalRecibido()!) }}</dd>
            </dl>
            <p class="total">
              <span>Cambio a entregar</span>
              <strong>{{ totalCambio() === null ? '—' : dinero(totalCambio()!) }}</strong>
            </p>
            <div class="aviso">
              <app-icono nombre="info" [tamano]="32" />
              <p>Cuenta el efectivo antes de registrar. Quedarás como cajero responsable de la recepción.</p>
            </div>
            @if (error(); as mensaje) {
              <p class="campo__error" role="alert">{{ mensaje }}</p>
            }
            <button
              type="button"
              class="boton boton--principal"
              [disabled]="guardando() || !seleccion().length"
              (click)="recibir()"
            >
              <app-icono nombre="palomita" />
              {{ guardando() ? 'Registrando…' : 'Registrar recepción' }}
            </button>
          </section>
        </div>
      } @else {
        <p class="tarjeta">No hay cobros en efectivo por recibir.</p>
      }
    } @else {
      <p role="status">Cargando pagos…</p>
    }
  `,
  styles: `
    .comprobante {
      display: grid;
      gap: 1rem;
      margin-bottom: 1.25rem;
      padding: 1.25rem;
      border-color: var(--azul);
      background: var(--azul-aviso);
    }

    .comprobante__encabezado {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
      gap: 0.85rem;
    }

    .comprobante__encabezado p,
    .comprobante__encabezado time,
    .comprobante__datos dt {
      color: var(--texto-suave);
    }

    .comprobante__encabezado time {
      text-align: right;
    }

    .comprobante__icono {
      display: grid;
      place-items: center;
      width: 3rem;
      height: 3rem;
      border-radius: 50%;
      background: var(--azul);
      color: white;
    }

    .comprobante__datos {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      margin: 0;
      padding-block: 1rem;
      border-block: 1px solid var(--borde);
    }

    .comprobante__datos > div {
      display: grid;
      gap: 0.25rem;
      padding-inline: 1rem;
      border-right: 1px solid var(--borde);
    }

    .comprobante__datos > div:first-child {
      padding-left: 0;
    }

    .comprobante__datos > div:last-child {
      padding-right: 0;
      border-right: 0;
    }

    .comprobante__datos dd {
      margin: 0;
      font-size: 1.1rem;
      font-weight: 650;
      overflow-wrap: anywhere;
    }

    .comprobante__cambio {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 1rem;
      margin: 0;
      color: var(--azul-fuerte);
      font-size: 1.25rem;
      font-weight: 650;
    }

    .comprobante__cambio strong {
      font-size: 1.75rem;
      font-variant-numeric: tabular-nums;
    }

    .comprobante__cambio--cero {
      color: var(--texto);
    }

    .pagos {
      margin: 0;
      padding: 0;
      border-top: 2px solid var(--borde);
      list-style: none;
    }

    .pagos li {
      border-bottom: 2px solid var(--borde);
    }

    .pago {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
      gap: 0.75rem;
      padding: 0.85rem 0.25rem;
      cursor: pointer;
    }

    .pago input {
      width: 1.4rem;
      height: 1.4rem;
      accent-color: var(--azul);
    }

    .pago__datos {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.25rem 0.75rem;
      overflow-wrap: anywhere;
    }

    .pago__datos strong {
      flex: 1 1 100%;
    }

    .recibido {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: end;
      gap: 1rem;
      padding: 0 0.25rem 1rem 2.4rem;
    }

    .cambio {
      display: grid;
      justify-items: end;
      margin: 0 0 1.6rem;
    }

    .cambio strong {
      font-size: 1.25rem;
    }

    .total {
      display: flex;
      justify-content: space-between;
      gap: 1rem;
      margin: 0;
      padding-top: 1rem;
      border-top: 2px solid var(--borde);
      font-size: 1.4rem;
      font-weight: 650;
    }

    h2 {
      margin: 0;
    }

    @media (max-width: 30rem) {
      .comprobante__encabezado {
        grid-template-columns: auto minmax(0, 1fr);
      }

      .comprobante__encabezado time {
        grid-column: 2;
        text-align: left;
      }

      .comprobante__datos {
        grid-template-columns: 1fr;
        padding-block: 0;
      }

      .comprobante__datos > div,
      .comprobante__datos > div:first-child,
      .comprobante__datos > div:last-child {
        padding: 0.75rem 0;
        border-right: 0;
        border-bottom: 1px solid var(--borde);
      }

      .comprobante__datos > div:last-child {
        border-bottom: 0;
      }

      .recibido {
        padding-left: 0.25rem;
      }
    }
  `,
})
export class Entregas implements OnInit {
  private readonly api = inject(CajaApi);
  private readonly clave = new ClaveIdempotencia();

  private readonly pendientes = signal<PagoEfectivo[] | null>(null);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly meseroId = signal('');
  protected readonly elegidos = signal(new Set<string>());
  protected readonly recibidos = signal<Partial<Record<string, string>>>({});
  protected readonly recibida = signal<Entrega | null>(null);
  protected readonly enviado = signal(false);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly meseros = computed(() => {
    const pagos = this.pendientes();
    if (!pagos) {
      return null;
    }
    const porMesero = new Map<string, Mesero>();
    for (const p of pagos) {
      const mesero = porMesero.get(p.meseroId) ?? { id: p.meseroId, nombre: p.mesero, pagos: [] };
      mesero.pagos.push(p);
      porMesero.set(p.meseroId, mesero);
    }
    return [...porMesero.values()];
  });
  protected readonly mesero = computed(() => this.meseros()?.find((m) => m.id === this.meseroId()));
  protected readonly seleccion = computed(() => (this.mesero()?.pagos ?? []).filter((p) => this.elegidos().has(p.id)));
  protected readonly totalPagos = computed(() => sumaDinero(this.seleccion().map((p) => p.monto)));
  protected readonly totalRecibido = computed(() => this.sumar((p) => this.recibidos()[p.id] ?? ''));
  protected readonly totalCambio = computed(() => {
    const cambios = this.seleccion().map((p) => this.cambioDe(p));
    return cambios.some((c) => c === null) ? null : sumaDinero(cambios as string[]);
  });
  protected readonly dinero = dinero;
  protected readonly fechaHora = fechaHora;

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.api.pendientes().subscribe({
      next: (pagos) => {
        this.pendientes.set(pagos);
        this.errorCarga.set(null);
        const sigue = pagos.some((p) => p.meseroId === this.meseroId());
        this.elegir(sigue ? this.meseroId() : (pagos[0]?.meseroId ?? ''));
      },
      error: (error: unknown) => this.errorCarga.set(leerProblema(error).detalle),
    });
  }

  /** Al cambiar de mesero se selecciona su cobro más reciente; lo recibido inicia en el importe exacto. */
  protected elegir(id: string): void {
    this.meseroId.set(id);
    const primero = this.mesero()?.pagos[0];
    this.elegidos.set(new Set(primero ? [primero.id] : []));
    this.recibidos.set(primero ? { [primero.id]: primero.monto } : {});
    this.enviado.set(false);
  }

  protected alternar(id: string): void {
    const elegidos = new Set(this.elegidos());
    const recibidos = { ...this.recibidos() };
    if (elegidos.delete(id)) {
      delete recibidos[id];
    } else {
      elegidos.add(id);
      recibidos[id] = this.mesero()?.pagos.find((p) => p.id === id)?.monto ?? '';
    }
    this.elegidos.set(elegidos);
    this.recibidos.set(recibidos);
  }

  protected capturar(id: string, valor: string): void {
    this.recibidos.set({ ...this.recibidos(), [id]: valor });
  }

  /** Nulo si lo capturado no es un importe o no cubre el pago. */
  protected cambioDe(p: PagoEfectivo): string | null {
    const texto = this.recibidos()[p.id] ?? '';
    if (!PATRON_DINERO.test(texto)) {
      return null;
    }
    const diferencia = centavos(texto) - centavos(p.monto);
    return diferencia < 0n ? null : deCentavos(diferencia);
  }

  protected errorDe(p: PagoEfectivo): string {
    if (!this.enviado()) {
      return '';
    }
    const texto = this.recibidos()[p.id] ?? '';
    if (!PATRON_DINERO.test(texto)) {
      return 'Escribe lo que entregó el cliente.';
    }
    return this.cambioDe(p) === null ? `No cubre el pago de ${dinero(p.monto)}.` : '';
  }

  private sumar(valor: (p: PagoEfectivo) => string): string | null {
    const valores = this.seleccion().map(valor);
    return valores.every((v) => PATRON_DINERO.test(v)) ? sumaDinero(valores.map(normalizarDecimal)) : null;
  }

  protected recibir(): void {
    this.enviado.set(true);
    this.error.set(null);
    this.recibida.set(null);
    if (!this.seleccion().length || this.seleccion().some((p) => this.errorDe(p)) || this.guardando()) {
      return;
    }
    const recepcion = {
      meseroId: this.meseroId(),
      pagos: this.seleccion().map((p) => ({ pagoId: p.id, montoRecibido: normalizarDecimal(this.recibidos()[p.id] ?? '') })),
    };
    this.guardando.set(true);
    this.api.recibir(recepcion, this.clave.para(recepcion)).subscribe({
      next: (entrega) => {
        this.clave.completar();
        this.guardando.set(false);
        this.recibida.set(entrega);
        this.enviado.set(false);
        this.cargar();
      },
      error: (error: unknown) => {
        const problema = leerProblema(error);
        this.guardando.set(false);
        this.error.set(problema.erroresCampo['pagos'] ?? problema.detalle);
        if (problema.estado === 409) {
          this.cargar();
        }
      },
    });
  }
}
