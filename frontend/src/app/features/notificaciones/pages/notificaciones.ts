import { ChangeDetectionStrategy, Component, computed, effect, inject, untracked } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Sesion } from '../../../core/auth/sesion';
import { Bandeja } from '../../../core/notificaciones/bandeja';
import { NotificacionesApi } from '../../../core/notificaciones/notificaciones.api';
import { fechaHora } from '../../../shared/formato';
import { TAMANO_PAGINA, conectarFiltros, crearListado } from '../../../shared/listado';
import { Icono } from '../../../shared/ui/icono';
import { Paginacion } from '../../../shared/ui/paginacion';

@Component({
  selector: 'app-notificaciones',
  imports: [ReactiveFormsModule, RouterLink, Icono, Paginacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <a class="boton-icono" routerLink="/inicio" aria-label="Volver a inicio">
        <app-icono nombre="atras" [tamano]="30" />
      </a>
      <h1>Avisos</h1>
    </div>

    <form class="filtros" [formGroup]="filtros">
      <select class="control" formControlName="leida" aria-label="Filtrar por lectura">
        <option value="">Todos los avisos</option>
        <option value="false">Sin leer</option>
        <option value="true">Leídos</option>
      </select>
    </form>

    @if (bandeja.error()) {
      <p class="campo__error" role="alert">No se pudo marcar el aviso. Intenta de nuevo.</p>
    }

    @if (listado.vista().error; as error) {
      <div class="aviso aviso--error" role="alert">
        <app-icono nombre="info" [tamano]="32" />
        <div>
          <p>{{ error }}</p>
          <button type="button" class="boton" (click)="listado.recargar()">Reintentar</button>
        </div>
      </div>
    } @else if (listado.pagina(); as datos) {
      @if (datos.contenido.length) {
        <ul>
          @for (n of datos.contenido; track n.id) {
            <li class="tarjeta" [class.sin-leer]="!n.leida">
              <app-icono nombre="campana" />
              <div>
                <p>
                  @if (esMesero() && n.contexto.cuentaId) {
                    <a [routerLink]="['/cuentas', n.contexto.cuentaId]">{{ n.mensaje }}</a>
                  } @else {
                    {{ n.mensaje }}
                  }
                </p>
                <p class="hora">{{ fechaHora(n.fechaHora) }}</p>
              </div>
              @if (n.leida) {
                <span class="hora">Leído</span>
              } @else {
                <button
                  type="button"
                  class="boton"
                  [attr.aria-label]="'Marcar como leído: ' + n.mensaje"
                  (click)="bandeja.leer(n.id)"
                >
                  Marcar leído
                </button>
              }
            </li>
          }
        </ul>
        <app-paginacion [datos]="datos" elementos="avisos" (ir)="listado.irA($event)" />
      } @else {
        <p class="tarjeta">No hay avisos.</p>
      }
    } @else {
      <p role="status">Cargando avisos…</p>
    }
  `,
  styles: `
    .filtros {
      max-width: 18rem;
      margin-bottom: 1.25rem;
    }

    ul {
      display: grid;
      gap: 0.75rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    li {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem 1.25rem;
      padding: 1rem 1.25rem;
    }

    li > div {
      flex: 1 1 14rem;
    }

    .sin-leer {
      border-color: var(--azul);
      background: var(--azul-aviso);
    }

    .hora {
      color: var(--texto-suave);
    }
  `,
})
export class Notificaciones {
  private readonly api = inject(NotificacionesApi);
  private readonly sesion = inject(Sesion);

  protected readonly bandeja = inject(Bandeja);
  protected readonly esMesero = computed(() => this.sesion.perfil()?.rol === 'MESERO');
  protected readonly fechaHora = fechaHora;
  protected readonly filtros = inject(NonNullableFormBuilder).group({ leida: '' });
  protected readonly listado = crearListado((p, page) => this.api.listar(p.get('leida') ?? '', page, TAMANO_PAGINA));

  constructor() {
    conectarFiltros(this.filtros, this.listado);
    let primera = true;
    effect(() => {
      // Un aviso nuevo o recién leído actualiza la lista.
      this.bandeja.pendientes();
      untracked(() => {
        if (!primera) {
          this.listado.recargar();
        }
        primera = false;
      });
    });
  }
}
