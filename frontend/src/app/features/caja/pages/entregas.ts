import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { startWith } from 'rxjs';
import { ClaveIdempotencia } from '../../../core/http/clave-idempotencia';
import { leerProblema } from '../../../core/http/problema';
import { PATRON_DINERO, dinero, fechaHora, normalizarDecimal, sumaDinero } from '../../../shared/formato';
import { Icono } from '../../../shared/ui/icono';
import { PestanasCaja } from '../components/pestanas-caja';
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

/** RF25: el cajero recibe el efectivo de los cobros de un mesero y, aparte, el fondo de cambio aplicado. */
@Component({
  selector: 'app-entregas',
  imports: [ReactiveFormsModule, RouterLink, Icono, PestanasCaja],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <a class="boton-icono" routerLink="/inicio" aria-label="Volver a inicio">
        <app-icono nombre="atras" [tamano]="30" />
      </a>
      <h1>Recibir efectivo</h1>
    </div>

    <app-pestanas-caja />

    <div role="status">
      @if (recibida(); as e) {
        <div class="aviso">
          <app-icono nombre="palomita" [tamano]="28" />
          <p>
            Entrega {{ e.id }} registrada: {{ dinero(e.monto) }} de {{ e.pagos.length }} pago(s) de
            {{ e.mesero }}{{ e.montoFondo !== '0.00' ? ' y ' + dinero(e.montoFondo) + ' de fondo de cambio' : '' }}.
          </p>
        </div>
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
              <label for="mesero">Mesero que entrega</label>
              <select id="mesero" class="control" [value]="meseroId()" (change)="elegir($any($event.target).value)">
                @for (m of lista; track m.id) {
                  <option [value]="m.id">{{ m.nombre }} · {{ m.pagos.length }} pago(s)</option>
                }
              </select>
            </div>
            <h2 id="titulo-pagos">Pagos en efectivo por entregar</h2>
            <ul class="pagos">
              @for (p of mesero()?.pagos ?? []; track p.id) {
                <li>
                  <label>
                    <input type="checkbox" [checked]="elegidos().has(p.id)" (change)="alternar(p.id)" />
                    <span class="pago">
                      <strong>Venta {{ p.ventaId }} · Mesa {{ p.mesa }}</strong>
                      <span class="campo__ayuda">{{ fechaHora(p.fechaHora) }}</span>
                      @if (p.estadoVenta === 'CANCELADA') {
                        <span class="etiqueta etiqueta--mal">Venta cancelada</span>
                      }
                      @if (p.fondo) {
                        <span class="etiqueta etiqueta--proceso">Fondo {{ dinero(p.fondo) }}</span>
                      }
                    </span>
                    <strong class="numero">{{ dinero(p.monto) }}</strong>
                  </label>
                </li>
              }
            </ul>
          </section>

          <form class="tarjeta pila" [formGroup]="formulario" (ngSubmit)="recibir()" novalidate>
            <h2>Resumen</h2>
            <dl class="datos-lista">
              <dt>Pagos seleccionados:</dt>
              <dd>{{ seleccion().length }}</dd>
              <dt>Efectivo de los pagos:</dt>
              <dd>
                <strong>{{ dinero(totalPagos()) }}</strong>
              </dd>
              @if (hayFondo()) {
                <dt>Fondo de cambio:</dt>
                <dd>
                  <strong>{{ dinero(totalFondo()) }}</strong>
                </dd>
              }
            </dl>
            <div class="campo">
              <label for="montoRecibido">Efectivo recibido de los pagos</label>
              <input
                id="montoRecibido"
                class="control"
                inputmode="decimal"
                formControlName="montoRecibido"
                [attr.aria-invalid]="!!errorMonto()"
                aria-describedby="monto-error"
              />
              <span id="monto-error" class="campo__error" aria-live="polite">{{ errorMonto() }}</span>
            </div>
            @if (hayFondo()) {
              <div class="campo">
                <label for="fondoRecibido">Fondo de cambio recibido</label>
                <input
                  id="fondoRecibido"
                  class="control"
                  inputmode="decimal"
                  formControlName="fondoRecibido"
                  [attr.aria-invalid]="!!errorFondo()"
                  aria-describedby="fondo-error"
                />
                <span id="fondo-error" class="campo__error" aria-live="polite">{{ errorFondo() }}</span>
              </div>
            }
            <div class="aviso">
              <app-icono nombre="info" [tamano]="32" />
              <p>Cuenta el efectivo antes de registrar. Quedarás como cajero responsable de la recepción.</p>
            </div>
            @if (error(); as mensaje) {
              <p class="campo__error" role="alert">{{ mensaje }}</p>
            }
            <button type="submit" class="boton boton--principal" [disabled]="guardando() || !seleccion().length">
              <app-icono nombre="palomita" />
              {{ guardando() ? 'Registrando…' : 'Registrar recepción' }}
            </button>
          </form>
        </div>
      } @else {
        <p class="tarjeta">No hay pagos en efectivo por entregar.</p>
      }
    } @else {
      <p role="status">Cargando pagos…</p>
    }
  `,
  styles: `
    .pagos {
      margin: 0;
      padding: 0;
      border-top: 2px solid var(--borde);
      list-style: none;
    }

    .pagos li {
      border-bottom: 2px solid var(--borde);
    }

    .pagos label {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
      gap: 0.75rem;
      padding: 0.85rem 0.25rem;
      cursor: pointer;
    }

    .pagos input {
      width: 1.4rem;
      height: 1.4rem;
      accent-color: var(--azul);
    }

    .pago {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.25rem 0.75rem;
      overflow-wrap: anywhere;
    }

    .pago strong {
      flex: 1 1 100%;
    }

    h2 {
      margin: 0;
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
  protected readonly recibida = signal<Entrega | null>(null);
  protected readonly enviado = signal(false);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly errores = signal<Record<string, string>>({});
  protected readonly formulario = inject(NonNullableFormBuilder).group({ montoRecibido: '', fondoRecibido: '' });
  private readonly valores = toSignal(this.formulario.valueChanges.pipe(startWith(this.formulario.getRawValue())), {
    initialValue: this.formulario.getRawValue(),
  });

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
  protected readonly totalFondo = computed(() =>
    sumaDinero(this.seleccion().flatMap((p) => (p.fondo ? [p.fondo] : []))),
  );
  protected readonly hayFondo = computed(() => this.totalFondo() !== '0.00');
  protected readonly errorMonto = computed(() =>
    this.errorImporte(this.valores().montoRecibido ?? '', this.totalPagos(), 'montoRecibido'),
  );
  protected readonly errorFondo = computed(() =>
    this.errorImporte(this.valores().fondoRecibido ?? '', this.totalFondo(), 'fondoRecibido'),
  );
  protected readonly dinero = dinero;
  protected readonly fechaHora = fechaHora;

  ngOnInit(): void {
    this.formulario.valueChanges.subscribe(() => this.errores.set({}));
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

  /** Al cambiar de mesero se seleccionan todos sus pagos. */
  protected elegir(id: string): void {
    this.meseroId.set(id);
    this.elegidos.set(new Set(this.mesero()?.pagos.map((p) => p.id) ?? []));
    this.enviado.set(false);
  }

  protected alternar(id: string): void {
    const elegidos = new Set(this.elegidos());
    if (!elegidos.delete(id)) {
      elegidos.add(id);
    }
    this.elegidos.set(elegidos);
  }

  private errorImporte(texto: string, esperado: string, campo: string): string {
    if (!this.enviado()) {
      return this.errores()[campo] ?? '';
    }
    if (!PATRON_DINERO.test(texto)) {
      return 'Escribe el importe contado.';
    }
    if (centavos(texto) !== centavos(esperado)) {
      return `No coincide: deben ser ${dinero(esperado)}. Vuelve a contar o ajusta la selección.`;
    }
    return this.errores()[campo] ?? '';
  }

  protected recibir(): void {
    this.enviado.set(true);
    this.error.set(null);
    this.recibida.set(null);
    if (!this.seleccion().length || this.errorMonto() || (this.hayFondo() && this.errorFondo()) || this.guardando()) {
      return;
    }
    const recepcion = {
      meseroId: this.meseroId(),
      pagoIds: this.seleccion().map((p) => p.id),
      montoRecibido: normalizarDecimal(this.valores().montoRecibido ?? ''),
      fondoRecibido: this.hayFondo() ? normalizarDecimal(this.valores().fondoRecibido ?? '') : '0',
    };
    this.guardando.set(true);
    this.api.recibir(recepcion, this.clave.para(recepcion)).subscribe({
      next: (entrega) => {
        this.clave.completar();
        this.guardando.set(false);
        this.recibida.set(entrega);
        this.formulario.reset();
        this.enviado.set(false);
        this.cargar();
      },
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
}
