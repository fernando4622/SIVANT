import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, untracked } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Sesion } from '../../../core/auth/sesion';
import { Bandeja } from '../../../core/notificaciones/bandeja';
import { Notificacion, NotificacionesApi } from '../../../core/notificaciones/notificaciones.api';
import { cantidad, fechaHora, unidad } from '../../../shared/formato';
import { TAMANO_PAGINA, conectarFiltros, crearListado } from '../../../shared/listado';
import { Icono } from '../../../shared/ui/icono';
import { Paginacion } from '../../../shared/ui/paginacion';

@Component({
  selector: 'app-notificaciones',
  imports: [ReactiveFormsModule, RouterLink, NgTemplateOutlet, Icono, Paginacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <a class="boton-icono" routerLink="/inicio" aria-label="Volver a inicio">
        <app-icono nombre="atras" [tamano]="30" />
      </a>
      <div>
        <h1>Avisos</h1>
        <p class="introduccion">Consulta las novedades que requieren tu atención.</p>
      </div>
    </div>

    <form class="filtros" [formGroup]="filtros">
      <select class="control" formControlName="leida" aria-label="Filtrar por lectura">
        <option value="">Todos los avisos</option>
        <option value="false">Sin leer</option>
        <option value="true">Leídos</option>
      </select>
    </form>

    @if (bandeja.error()) {
      <p class="campo__error" role="alert">No se pudo actualizar el aviso. Intenta de nuevo.</p>
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
        <ul class="avisos">
          @for (n of datos.contenido; track n.id) {
            <li class="notificacion" [class.notificacion--pendiente]="!n.leida">
              @if (destino(n); as ruta) {
                <a class="notificacion__enlace" [routerLink]="ruta">
                  <ng-container *ngTemplateOutlet="contenido; context: { $implicit: n }" />
                </a>
              } @else {
                <div class="notificacion__enlace notificacion__enlace--estatico">
                  <span class="notificacion__icono"><app-icono nombre="campana" /></span>
                  <div class="notificacion__contenido">
                    <p class="notificacion__mensaje">{{ n.mensaje }}</p>
                    <p class="notificacion__hora">{{ fechaHora(n.fechaHora) }}</p>
                  </div>
                </div>
              }
              <div class="notificacion__acciones">
                @if (n.leida) {
                  <span class="notificacion__estado">
                    <app-icono nombre="palomita" [tamano]="18" />
                    Leído
                  </span>
                } @else {
                  <button
                    type="button"
                    class="boton notificacion__accion"
                    [attr.aria-label]="'Marcar como leído: ' + n.mensaje"
                    (click)="bandeja.leer(n.id)"
                  >
                    Marcar leído
                  </button>
                }
                <button
                  type="button"
                  class="boton-icono notificacion__descartar"
                  [attr.aria-label]="'Descartar: ' + n.mensaje"
                  title="Descartar"
                  (click)="bandeja.descartar(n.id)"
                >
                  <app-icono nombre="cerrar" [tamano]="20" />
                </button>
              </div>
            </li>
          }
        </ul>
        <app-paginacion [datos]="datos" elementos="avisos" (ir)="listado.irA($event)" />
      } @else {
        <div class="vacio">
          <span class="vacio__icono"><app-icono nombre="campana" [tamano]="28" /></span>
          <div>
            <h2>No hay avisos</h2>
            <p>Cuando haya una novedad importante, aparecerá aquí.</p>
          </div>
        </div>
      }
    } @else {
      <p role="status">Cargando avisos…</p>
    }

    <ng-template #contenido let-n>
      <span class="notificacion__icono"><app-icono nombre="campana" /></span>
      <div class="notificacion__contenido">
        <p class="notificacion__mensaje">
          {{ n.mensaje }}
          @if (n.contexto.existenciaId) {
            Faltan {{ cantidad(n.contexto.faltante ?? '0') }} {{ unidad(n.contexto.unidadMedida ?? '') }}.
          }
        </p>
        <p class="notificacion__hora">{{ fechaHora(n.fechaHora) }}</p>
      </div>
    </ng-template>
  `,
  styles: `
    .introduccion {
      margin-top: 0.35rem;
      color: var(--texto-suave);
    }

    .filtros {
      max-width: 18rem;
      margin-bottom: 1rem;
    }

    .avisos {
      display: grid;
      gap: 1rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .notificacion {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: center;
      gap: 0.85rem;
      min-height: 7rem;
      padding: 1.1rem;
      border: 2px solid var(--borde);
      border-radius: var(--radio);
      background: var(--superficie);
      transition:
        border-color 150ms ease,
        background 150ms ease,
        box-shadow 150ms ease;
    }

    .notificacion:hover,
    .notificacion:focus-within {
      border-color: var(--azul);
      background: var(--azul-aviso);
      box-shadow: var(--sombra);
    }

    .notificacion__enlace {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr);
      align-items: center;
      gap: 0.85rem;
      min-width: 0;
      color: var(--texto);
      text-decoration: none;
    }

    .notificacion__enlace:focus-visible {
      border-radius: var(--radio-chico);
      outline: 2px solid var(--azul);
      outline-offset: 0.35rem;
    }

    .notificacion__enlace--estatico {
      cursor: default;
    }

    .notificacion__icono,
    .vacio__icono {
      display: grid;
      place-items: center;
      width: 2.75rem;
      height: 2.75rem;
      border-radius: var(--radio-chico);
      background: var(--cabecera);
      color: var(--texto-suave);
    }

    .notificacion--pendiente {
      border-color: var(--azul);
    }

    .notificacion--pendiente .notificacion__icono {
      background: var(--azul-aviso);
      color: var(--azul);
    }

    .notificacion:hover .notificacion__icono,
    .notificacion:focus-within .notificacion__icono {
      background: var(--azul-avatar);
      color: var(--azul);
    }

    .notificacion__contenido {
      display: grid;
      gap: 0.35rem;
      min-width: 0;
    }

    .notificacion__mensaje {
      font-size: 1.05rem;
      overflow-wrap: anywhere;
    }

    .notificacion__mensaje a {
      font-weight: 650;
    }

    .notificacion__hora,
    .notificacion__estado {
      color: var(--texto-suave);
    }

    .notificacion__hora {
      font-size: 0.925rem;
    }

    .notificacion__estado {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      white-space: nowrap;
    }

    .notificacion__accion {
      white-space: nowrap;
    }

    .notificacion__acciones {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 0.5rem;
    }

    .notificacion__descartar {
      color: var(--texto-suave);
    }

    .notificacion__descartar:hover,
    .notificacion__descartar:focus-visible {
      color: var(--rojo);
    }

    .vacio {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      min-height: 7rem;
      padding: 1.1rem;
      border: 2px solid var(--borde);
      border-radius: var(--radio);
      background: var(--superficie);
    }

    .vacio h2 {
      font-size: 1.125rem;
    }

    .vacio p {
      margin-top: 0.25rem;
      color: var(--texto-suave);
    }

    @media (max-width: 40rem) {
      .notificacion {
        grid-template-columns: minmax(0, 1fr);
      }

      .notificacion__acciones {
        margin-left: 3.6rem;
        justify-self: start;
      }
    }
  `,
})
export class Notificaciones {
  private readonly api = inject(NotificacionesApi);
  private readonly sesion = inject(Sesion);

  protected readonly bandeja = inject(Bandeja);
  protected readonly esMesero = computed(() => this.sesion.perfil()?.rol === 'MESERO');
  protected readonly fechaHora = fechaHora;
  protected readonly cantidad = cantidad;
  protected readonly unidad = unidad;
  protected readonly filtros = inject(NonNullableFormBuilder).group({ leida: '' });
  protected readonly listado = crearListado((p, page, automatica) =>
    this.api.listar(p.get('leida') ?? '', page, TAMANO_PAGINA, automatica),
  );

  constructor() {
    conectarFiltros(this.filtros, this.listado);
    let primera = true;
    effect(() => {
      // Un aviso nuevo o recién leído actualiza la lista.
      this.bandeja.pendientes();
      untracked(() => {
        if (!primera) {
          this.listado.recargar(true);
        }
        primera = false;
      });
    });
  }

  protected destino(notificacion: Notificacion): string[] | null {
    if (this.esMesero() && notificacion.contexto.cuentaId) {
      return ['/cuentas', notificacion.contexto.cuentaId];
    }
    if (notificacion.contexto.existenciaId) {
      return ['/inventario/existencias', notificacion.contexto.existenciaId];
    }
    return null;
  }
}
