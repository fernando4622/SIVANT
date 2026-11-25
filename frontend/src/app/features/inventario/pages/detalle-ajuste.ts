import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Sesion } from '../../../core/auth/sesion';
import { ClaveIdempotencia } from '../../../core/http/clave-idempotencia';
import { leerProblema } from '../../../core/http/problema';
import { cantidad, fechaHora, unidad } from '../../../shared/formato';
import { Icono } from '../../../shared/ui/icono';
import { InventarioApi, NOMBRE_ESTADO, Solicitud } from '../data-access/inventario.api';

/** RF15: el administrador autoriza o rechaza. El almacenista consulta el resultado. */
@Component({
  selector: 'app-detalle-ajuste',
  imports: [RouterLink, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <a class="boton-icono" routerLink="/inventario/ajustes" aria-label="Volver a ajustes">
        <app-icono nombre="atras" [tamano]="30" />
      </a>
      <h1>{{ resuelve() ? 'Autorizar ajuste de inventario' : 'Ajuste de inventario' }}</h1>
    </div>

    @if (errorCarga(); as mensaje) {
      <div class="aviso aviso--error" role="alert">
        <app-icono nombre="info" [tamano]="32" />
        <p>{{ mensaje }}</p>
      </div>
    } @else if (solicitud(); as s) {
      <div class="columnas">
        <section class="tarjeta" aria-labelledby="titulo-detalle">
          <h2 id="titulo-detalle">Detalle del ajuste</h2>
          <dl class="datos-lista">
            <dt>Artículo:</dt>
            <dd>
              <a [routerLink]="['/inventario/existencias', s.existenciaId]">{{ s.articulo }}</a>
            </dd>
            <dt>Cantidad al solicitar:</dt>
            <dd>{{ cantidad(s.cantidadAnterior) }} {{ unidad(s.unidadMedida) }}</dd>
            <dt>Cantidad solicitada:</dt>
            <dd>
              <strong>{{ cantidad(s.cantidadSolicitada) }} {{ unidad(s.unidadMedida) }}</strong>
            </dd>
            <dt>Existencia actual:</dt>
            <dd>{{ cantidad(s.cantidadActual) }} {{ unidad(s.unidadMedida) }}</dd>
            <dt>Justificación:</dt>
            <dd>{{ s.justificacion }}</dd>
          </dl>
          <hr />
          <h2>Solicitado por</h2>
          <p class="persona">
            <span><app-icono nombre="persona" [tamano]="28" /></span>{{ s.almacenista }}
          </p>
          <p class="persona">
            <span><app-icono nombre="calendario" [tamano]="28" /></span>{{ fechaHora(s.fechaHoraSolicitud) }}
          </p>
        </section>

        <div class="pila">
          @if (s.estado !== 'PENDIENTE') {
            <div class="aviso" role="status">
              <app-icono nombre="info" [tamano]="32" />
              <p>
                {{ nombre[s.estado] }} por {{ s.administrador }} el {{ fechaHora(s.fechaHoraResolucion!) }}.
                {{ s.estado === 'RECHAZADA' ? 'La existencia no cambió.' : '' }}
              </p>
            </div>
          } @else if (s.existenciaCambiada) {
            <div class="aviso aviso--error">
              <app-icono nombre="info" [tamano]="32" />
              <p>La existencia cambió desde la solicitud. Solo se puede rechazar y pedir una nueva.</p>
            </div>
          } @else {
            <div class="aviso">
              <app-icono nombre="info" [tamano]="32" />
              <p>
                {{
                  administra()
                    ? 'Este ajuste requiere tu autorización como administrador.'
                    : 'Este ajuste está pendiente de autorización.'
                }}
              </p>
            </div>
          }

          @if (error(); as mensaje) {
            <p class="campo__error" role="alert">{{ mensaje }}</p>
          }

          @if (resuelve()) {
            <div class="acciones">
              <button type="button" class="boton" [disabled]="resolviendo()" (click)="resolver('RECHAZADA')">
                Rechazar
              </button>
              <button
                type="button"
                class="boton boton--principal"
                [disabled]="resolviendo() || s.existenciaCambiada"
                (click)="resolver('AUTORIZADA')"
              >
                <app-icono nombre="palomita" />
                Autorizar
              </button>
            </div>
          }
        </div>
      </div>
    } @else {
      <p role="status">Cargando solicitud…</p>
    }
  `,
  styles: `
    hr {
      margin: 1.25rem 0;
      border: 0;
      border-top: 2px solid var(--borde);
    }

    .persona {
      display: flex;
      align-items: center;
      gap: 1rem;
      margin-top: 0.75rem;
    }

    .persona span {
      display: grid;
      place-items: center;
      width: 3.25rem;
      height: 3.25rem;
      border-radius: 50%;
      background: var(--azul-aviso);
      color: var(--azul);
    }

    .acciones .boton {
      flex: 1 1 9rem;
    }
  `,
})
export class DetalleAjuste implements OnInit {
  readonly id = input.required<string>();

  private readonly api = inject(InventarioApi);
  private readonly sesion = inject(Sesion);
  private readonly clave = new ClaveIdempotencia();

  protected readonly administra = computed(() => this.sesion.perfil()?.rol === 'ADMINISTRADOR');
  protected readonly solicitud = signal<Solicitud | null>(null);
  protected readonly resuelve = computed(() => this.administra() && this.solicitud()?.estado === 'PENDIENTE');
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly resolviendo = signal(false);
  protected readonly nombre = NOMBRE_ESTADO;
  protected readonly cantidad = cantidad;
  protected readonly unidad = unidad;
  protected readonly fechaHora = fechaHora;

  ngOnInit(): void {
    this.cargar();
  }

  protected resolver(decision: 'AUTORIZADA' | 'RECHAZADA'): void {
    if (this.resolviendo()) {
      return;
    }
    this.error.set(null);
    this.resolviendo.set(true);
    this.api.resolver(this.id(), decision, this.clave.para(decision)).subscribe({
      next: (solicitud) => {
        this.clave.completar();
        this.resolviendo.set(false);
        this.solicitud.set(solicitud);
      },
      error: (error: unknown) => {
        const problema = leerProblema(error);
        this.resolviendo.set(false);
        this.error.set(problema.detalle);
        if (problema.estado === 409) {
          this.cargar();
        }
      },
    });
  }

  private cargar(): void {
    this.api.ajuste(this.id()).subscribe({
      next: (solicitud) => this.solicitud.set(solicitud),
      error: (error: unknown) => this.errorCarga.set(leerProblema(error).detalle),
    });
  }
}
