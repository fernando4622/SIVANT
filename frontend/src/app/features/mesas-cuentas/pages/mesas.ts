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
                  <img src="/inicio-mesas.png" alt="" />
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
      display: grid;
      grid-template-columns: 4.35rem minmax(0, 1fr);
      align-items: center;
      gap: 0.75rem;
    }

    .mesa-redonda {
      display: grid;
      place-items: center;
      width: 4.35rem;
      height: 3.75rem;
      flex: none;
    }

    .mesa-redonda img {
      width: 4.35rem;
      height: 3.75rem;
      object-fit: contain;
      transform: translateX(-0.55rem);
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
