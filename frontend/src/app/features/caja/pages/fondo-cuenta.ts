import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ClaveIdempotencia } from '../../../core/http/clave-idempotencia';
import { leerProblema } from '../../../core/http/problema';
import { PATRON_DINERO, dinero, fechaHora, normalizarDecimal } from '../../../shared/formato';
import { Icono } from '../../../shared/ui/icono';
import { Cuenta, ServicioApi } from '../../mesas-cuentas/data-access/servicio.api';
import { CLASE_FONDO, CajaApi, Fondo, NOMBRE_FONDO } from '../data-access/caja.api';

/** RF26: el cajero entrega el fondo al mesero de la cuenta, o lo anula si se lo regresan antes del cobro. */
@Component({
  selector: 'app-fondo-cuenta',
  imports: [ReactiveFormsModule, RouterLink, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <a class="boton-icono" [routerLink]="['/cuentas', id()]" aria-label="Volver a la cuenta">
        <app-icono nombre="atras" [tamano]="30" />
      </a>
      <h1>Fondo de cambio</h1>
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
    } @else if (cuenta(); as c) {
      <div class="columnas">
        <div class="pila">
          <section class="tarjeta" aria-labelledby="titulo-cuenta">
            <h2 id="titulo-cuenta">Cuenta {{ c.id }}</h2>
            <dl class="datos-lista">
              <dt>Mesero:</dt>
              <dd>{{ c.mesero }}</dd>
              <dt>Estado:</dt>
              <dd>{{ c.estado === 'ABIERTA' ? 'Abierta' : 'Cerrada' }}</dd>
              <dt>Total:</dt>
              <dd>{{ dinero(c.total) }}</dd>
            </dl>
          </section>

          <section class="tarjeta" aria-labelledby="titulo-historial">
            <h2 id="titulo-historial">Fondos de esta cuenta</h2>
            <ul class="fondos">
              @for (f of fondos(); track f.id) {
                <li>
                  <span class="fila">
                    <strong>{{ dinero(f.monto) }}</strong>
                    <span [class]="clase[f.estado]">{{ nombre[f.estado] }}</span>
                  </span>
                  <span class="campo__ayuda">Entregó {{ f.cajero }} el {{ fechaHora(f.fechaHora) }}.</span>
                  @if (f.anulacion; as a) {
                    <span class="campo__ayuda">
                      Anuló {{ a.cajero }} el {{ fechaHora(a.fechaHora) }}: {{ a.justificacion }}
                    </span>
                  }
                  @if (f.ventaId) {
                    <span class="campo__ayuda">Aplicado al cobro de la venta {{ f.ventaId }}.</span>
                  }
                  @if (f.entregaId) {
                    <span class="campo__ayuda">Regresado a caja en la entrega {{ f.entregaId }}.</span>
                  }
                </li>
              } @empty {
                <li class="campo__ayuda">Esta cuenta no ha recibido fondo de cambio.</li>
              }
            </ul>
          </section>
        </div>

        @if (c.acciones.includes('ENTREGAR_FONDO')) {
          <form class="pila" [formGroup]="entrega" (ngSubmit)="entregar()" novalidate>
            <div class="aviso">
              <app-icono nombre="info" [tamano]="32" />
              <p>
                Entrega el efectivo a {{ c.mesero }} antes de registrar. Mientras el fondo esté pendiente, la cuenta
                solo podrá cobrarse en efectivo.
              </p>
            </div>
            <div class="campo">
              <label for="monto">Monto entregado</label>
              <input
                id="monto"
                class="control"
                inputmode="decimal"
                formControlName="monto"
                [attr.aria-invalid]="!!errorMonto()"
                aria-describedby="monto-error"
              />
              <span id="monto-error" class="campo__error" aria-live="polite">{{ errorMonto() }}</span>
            </div>
            @if (error(); as mensaje) {
              <p class="campo__error" role="alert">{{ mensaje }}</p>
            }
            <div class="acciones">
              <a class="boton" [routerLink]="['/cuentas', id()]">Volver</a>
              <button type="submit" class="boton boton--principal" [disabled]="guardando()">
                <app-icono nombre="cobro" />
                {{ guardando() ? 'Registrando…' : 'Registrar fondo' }}
              </button>
            </div>
          </form>
        } @else if (c.acciones.includes('ANULAR_FONDO')) {
          <form class="pila" [formGroup]="anulacion" (ngSubmit)="anular()" novalidate>
            <div class="aviso">
              <app-icono nombre="info" [tamano]="32" />
              <p>
                Recibe de {{ c.mesero }} los {{ dinero(c.fondo!.monto) }} completos antes de anular. Esta acción
                registrará al cajero responsable y no podrá deshacerse.
              </p>
            </div>
            <div class="campo">
              <label for="justificacion">Motivo de la anulación</label>
              <textarea
                id="justificacion"
                class="control"
                formControlName="justificacion"
                placeholder="Por ejemplo: la mesa pagó con el importe exacto…"
                [attr.aria-invalid]="!!errorJustificacion()"
                aria-describedby="justificacion-error"
              ></textarea>
              <span id="justificacion-error" class="campo__error" aria-live="polite">{{ errorJustificacion() }}</span>
            </div>
            @if (error(); as mensaje) {
              <p class="campo__error" role="alert">{{ mensaje }}</p>
            }
            <div class="acciones">
              <a class="boton" [routerLink]="['/cuentas', id()]">Volver</a>
              <button type="submit" class="boton boton--peligro" [disabled]="guardando()">
                <app-icono nombre="basura" />
                {{ guardando() ? 'Anulando…' : 'Anular fondo' }}
              </button>
            </div>
          </form>
        } @else {
          <div class="aviso" role="status">
            <app-icono nombre="info" [tamano]="32" />
            <p>{{ motivo(c) }}</p>
          </div>
        }
      </div>
    } @else {
      <p role="status">Cargando cuenta…</p>
    }
  `,
  styleUrl: '../../comandas/pages/comandas.css',
  styles: `
    .fondos {
      margin: 1rem 0 0;
      padding: 0;
      list-style: none;
    }

    .fondos li {
      display: grid;
      gap: 0.25rem;
      padding: 0.75rem 0;
      border-top: 2px solid var(--borde);
    }

    .fila {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 0.5rem;
    }
  `,
})
export class FondoCuenta implements OnInit {
  readonly id = input.required<string>();

  private readonly servicio = inject(ServicioApi);
  private readonly api = inject(CajaApi);
  private readonly clave = new ClaveIdempotencia();
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly cuenta = signal<Cuenta | null>(null);
  protected readonly fondos = signal<Fondo[]>([]);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly errores = signal<Record<string, string>>({});
  protected readonly enviado = signal(false);
  protected readonly guardando = signal(false);
  protected readonly entrega = this.fb.group({ monto: ['', [Validators.required, Validators.pattern(PATRON_DINERO)]] });
  protected readonly anulacion = this.fb.group({
    justificacion: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(500)]],
  });
  protected readonly nombre = NOMBRE_FONDO;
  protected readonly clase = CLASE_FONDO;
  protected readonly dinero = dinero;
  protected readonly fechaHora = fechaHora;

  protected errorMonto(): string {
    const control = this.entrega.controls.monto;
    if (this.enviado() && (control.invalid || Number(normalizarDecimal(control.value)) <= 0)) {
      return 'Escribe un importe mayor que 0, con hasta 2 decimales.';
    }
    return this.errores()['monto'] ?? '';
  }

  protected errorJustificacion(): string {
    if (this.enviado() && this.anulacion.controls.justificacion.invalid) {
      return 'Escribe el motivo, con 500 caracteres como máximo.';
    }
    return this.errores()['justificacion'] ?? '';
  }

  ngOnInit(): void {
    this.entrega.valueChanges.subscribe(() => this.errores.set({}));
    this.anulacion.valueChanges.subscribe(() => this.errores.set({}));
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
    this.api.fondosDe(this.id()).subscribe({ next: (fondos) => this.fondos.set(fondos), error: () => undefined });
  }

  protected motivo(cuenta: Cuenta): string {
    if (cuenta.fondo) {
      return `La cuenta tiene un fondo de ${dinero(cuenta.fondo.monto)}: ${NOMBRE_FONDO[cuenta.fondo.estado as keyof typeof NOMBRE_FONDO].toLowerCase()}.`;
    }
    if (cuenta.estado === 'CERRADA') {
      return 'La cuenta está cerrada.';
    }
    return 'Para entregar un fondo, la cuenta debe tener comandas por cobrar.';
  }

  protected entregar(): void {
    this.enviar(() => this.errorMonto(), () => {
      const monto = normalizarDecimal(this.entrega.controls.monto.value);
      return this.api.entregarFondo(this.id(), monto, this.clave.para(['entregar', monto]));
    });
  }

  protected anular(): void {
    this.enviar(() => this.errorJustificacion(), () => {
      const justificacion = this.anulacion.controls.justificacion.value;
      return this.api.anularFondo(this.id(), justificacion, this.clave.para(['anular', justificacion]));
    });
  }

  private enviar(validar: () => string, solicitud: () => ReturnType<CajaApi['anularFondo']>): void {
    this.enviado.set(true);
    this.error.set(null);
    if (validar() || this.guardando()) {
      return;
    }
    this.guardando.set(true);
    solicitud().subscribe({
      next: () => {
        this.clave.completar();
        this.guardando.set(false);
        this.enviado.set(false);
        this.entrega.reset();
        this.anulacion.reset();
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
