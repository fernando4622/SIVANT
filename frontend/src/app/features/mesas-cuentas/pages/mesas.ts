import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Sesion } from '../../../core/auth/sesion';
import { ClaveIdempotencia } from '../../../core/http/clave-idempotencia';
import { leerProblema } from '../../../core/http/problema';
import { TiempoReal } from '../../../core/realtime/tiempo-real';
import { Icono } from '../../../shared/ui/icono';
import { Mesa, ServicioApi } from '../data-access/servicio.api';

/** RF01: el mesero abre una cuenta por mesa. El cajero consulta las cuentas abiertas. */
@Component({
  selector: 'app-mesas',
  imports: [RouterLink, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <a class="boton-icono" routerLink="/inicio" aria-label="Volver a inicio">
        <app-icono nombre="atras" [tamano]="30" />
      </a>
      <h1>Mesas</h1>
      <button type="button" class="boton" (click)="cargar()">Actualizar</button>
    </div>

    @if (error(); as mensaje) {
      <div class="aviso aviso--error" role="alert">
        <app-icono nombre="info" [tamano]="32" />
        <p>{{ mensaje }}</p>
      </div>
    }

    @if (mesas(); as lista) {
      @if (lista.length) {
        <ul class="mesas">
          @for (mesa of lista; track mesa.id) {
            <li class="tarjeta">
              <div class="mesa-titulo">
                <span class="mesa-redonda" aria-hidden="true">
                  <i></i><i></i><i></i><i></i>
                </span>
                <h2>Mesa {{ mesa.numero }}</h2>
              </div>
              <p class="situacion" [class.situacion--ocupada]="!mesa.disponible">
                {{ mesa.disponible ? 'Libre' : 'Ocupada' }}
              </p>
              <p class="mesero">{{ mesa.mesero }}</p>
              @if (mesa.cuentaId) {
                <a class="boton boton--principal" [routerLink]="['/cuentas', mesa.cuentaId]">Ver cuenta</a>
              } @else if (mesa.disponible && esMesero()) {
                <button type="button" class="boton" [disabled]="abriendo() !== null" (click)="abrir(mesa)">
                  {{ abriendo() === mesa.id ? 'Abriendo…' : 'Abrir cuenta' }}
                </button>
              }
            </li>
          }
        </ul>
      } @else {
        <p class="tarjeta">No hay mesas registradas.</p>
      }
    } @else if (!error()) {
      <p role="status">Cargando mesas…</p>
    }
  `,
  styles: `
    .aviso {
      margin-bottom: 1.25rem;
    }

    .mesas {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(13rem, 100%), 1fr));
      gap: 1.25rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .mesas .tarjeta {
      display: grid;
      grid-template-rows: auto auto 1fr auto;
      gap: 0.5rem;
      min-height: 13rem;
    }

    .mesa-titulo {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .mesa-redonda {
      position: relative;
      display: block;
      width: 3.75rem;
      height: 3.25rem;
      flex: none;
    }

    .mesa-redonda::before {
      position: absolute;
      left: 0.15rem;
      top: 0.35rem;
      width: 3.45rem;
      height: 1.05rem;
      border: 0.18rem solid #6d452b;
      border-radius: 50%;
      background: #9a6843;
      box-shadow:
        inset 0 -0.18rem 0 rgb(109 69 43 / 35%),
        0 0.2rem 0.35rem rgb(15 26 58 / 14%);
      content: '';
    }

    .mesa-redonda::after {
      position: absolute;
      left: 50%;
      top: 1.25rem;
      width: 0.38rem;
      height: 1.45rem;
      border-radius: 0 0 0.2rem 0.2rem;
      background: #6d452b;
      content: '';
      translate: -50% 0;
    }

    .mesa-redonda i {
      display: none;
    }

    .mesa-redonda i:first-child {
      position: absolute;
      left: 50%;
      bottom: 0.3rem;
      display: block;
      width: 1.75rem;
      height: 0.3rem;
      border-radius: 999px;
      background: #6d452b;
      translate: -50% 0;
    }

    .situacion {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .situacion::before {
      width: 0.85rem;
      height: 0.85rem;
      border-radius: 50%;
      background: var(--verde);
      content: '';
    }

    .situacion--ocupada::before {
      background: var(--naranja);
    }

    .mesero {
      color: var(--texto-suave);
      overflow-wrap: anywhere;
    }
  `,
})
export class Mesas {
  private readonly api = inject(ServicioApi);
  private readonly router = inject(Router);
  private readonly sesion = inject(Sesion);
  private readonly clave = new ClaveIdempotencia();

  protected readonly esMesero = computed(() => this.sesion.perfil()?.rol === 'MESERO');
  protected readonly mesas = signal<Mesa[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly abriendo = signal<string | null>(null);

  constructor() {
    this.cargar();
    inject(TiempoReal)
      .cambios()
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.consultar(true));
  }

  protected cargar(): void {
    this.error.set(null);
    this.consultar(false);
  }

  protected abrir(mesa: Mesa): void {
    this.error.set(null);
    this.abriendo.set(mesa.id);
    this.api.abrir(mesa.id, this.clave.para(mesa.id)).subscribe({
      next: (cuenta) => void this.router.navigate(['/cuentas', cuenta.id]),
      error: (error: unknown) => {
        const problema = leerProblema(error);
        this.abriendo.set(null);
        this.error.set(problema.detalle);
        if (problema.estado === 409) {
          this.clave.completar();
          this.consultar(false);
        }
      },
    });
  }

  private consultar(automatica: boolean): void {
    this.api.mesas(automatica).subscribe({
      next: (mesas) => this.mesas.set(mesas),
      error: (error: unknown) => this.error.set(leerProblema(error).detalle),
    });
  }
}
