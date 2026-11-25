import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ClaveIdempotencia } from '../../../core/http/clave-idempotencia';
import { leerProblema } from '../../../core/http/problema';
import { Icono } from '../../../shared/ui/icono';
import { TarjetaComanda } from '../../mesas-cuentas/components/tarjeta-comanda';
import { Comanda, Cuenta, ServicioApi } from '../../mesas-cuentas/data-access/servicio.api';

/** RF03: el cajero cancela con justificación obligatoria. */
@Component({
  selector: 'app-cancelar-comanda',
  imports: [ReactiveFormsModule, RouterLink, Icono, TarjetaComanda],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <a class="boton-icono" [routerLink]="['/cuentas', id()]" aria-label="Volver a la cuenta">
        <app-icono nombre="atras" [tamano]="30" />
      </a>
      <h1>Cancelar comanda</h1>
      @if (cuenta(); as c) {
        <span class="mesa">Mesa {{ c.mesa }}</span>
      }
    </div>

    @if (errorCarga(); as mensaje) {
      <div class="aviso aviso--error" role="alert">
        <app-icono nombre="info" [tamano]="32" />
        <p>{{ mensaje }}</p>
      </div>
    } @else if (comanda(); as c) {
      <div class="columnas">
        <app-tarjeta-comanda [comanda]="c" [mesero]="cuenta()?.mesero ?? ''" />

        @if (cancelable()) {
          <form class="pila" [formGroup]="formulario" (ngSubmit)="cancelar()" novalidate>
            <div class="aviso">
              <app-icono nombre="info" [tamano]="32" />
              <p>Esta acción registrará al cajero responsable y no podrá deshacerse.</p>
            </div>
            <div class="campo">
              <label for="justificacion">Justificación</label>
              <textarea
                id="justificacion"
                class="control"
                formControlName="justificacion"
                placeholder="Describe el motivo de la cancelación…"
                [attr.aria-invalid]="enviado() && formulario.invalid"
                aria-describedby="justificacion-error"
              ></textarea>
              <span id="justificacion-error" class="campo__error" aria-live="polite">
                @if (enviado() && formulario.invalid) {
                  Escribe la justificación, con 500 caracteres como máximo.
                }
              </span>
            </div>
            @if (error(); as mensaje) {
              <p class="campo__error" role="alert">{{ mensaje }}</p>
            }
            <div class="acciones">
              <a class="boton" [routerLink]="['/cuentas', id()]">Volver</a>
              <button type="submit" class="boton boton--peligro" [disabled]="guardando()">
                <app-icono nombre="basura" />
                {{ guardando() ? 'Cancelando…' : 'Cancelar comanda' }}
              </button>
            </div>
          </form>
        } @else {
          <div class="aviso aviso--error" role="alert">
            <app-icono nombre="info" [tamano]="32" />
            <p>{{ error() ?? 'Esta comanda ya no puede cancelarse.' }}</p>
          </div>
        }
      </div>
    } @else {
      <p role="status">Cargando comanda…</p>
    }
  `,
  styleUrl: './comandas.css',
})
export class CancelarComanda implements OnInit {
  readonly id = input.required<string>();
  readonly comandaId = input.required<string>();

  private readonly api = inject(ServicioApi);
  private readonly router = inject(Router);
  private readonly clave = new ClaveIdempotencia();

  protected readonly cuenta = signal<Cuenta | null>(null);
  protected readonly comanda = signal<Comanda | null>(null);
  protected readonly cancelable = computed(() => this.comanda()?.acciones.includes('CANCELAR') ?? false);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly enviado = signal(false);
  protected readonly guardando = signal(false);
  protected readonly formulario = inject(NonNullableFormBuilder).group({
    justificacion: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cancelar(): void {
    const comanda = this.comanda();
    this.enviado.set(true);
    this.error.set(null);
    if (!comanda || this.formulario.invalid || this.guardando()) {
      return;
    }
    const { justificacion } = this.formulario.getRawValue();
    this.guardando.set(true);
    this.api
      .cancelar(comanda.id, comanda.version, justificacion, this.clave.para([comanda.version, justificacion]))
      .subscribe({
        next: () => void this.router.navigate(['/cuentas', this.id()]),
        error: (error: unknown) => {
          const problema = leerProblema(error);
          this.guardando.set(false);
          this.error.set(problema.detalle);
          if (problema.estado === 409) {
            this.cargar();
          }
        },
      });
  }

  private cargar(): void {
    this.api.cuenta(this.id()).subscribe({
      next: (cuenta) => {
        const comanda = cuenta.comandas.find((c) => c.id === this.comandaId());
        this.cuenta.set(cuenta);
        this.comanda.set(comanda ?? null);
        this.errorCarga.set(comanda ? null : 'La comanda no existe.');
      },
      error: (error: unknown) => this.errorCarga.set(leerProblema(error).detalle),
    });
  }
}
