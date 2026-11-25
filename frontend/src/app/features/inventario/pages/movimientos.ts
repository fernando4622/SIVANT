import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { TiempoReal } from '../../../core/realtime/tiempo-real';
import { conectarFiltros, crearListado } from '../../../shared/listado';
import { Icono } from '../../../shared/ui/icono';
import { Paginacion } from '../../../shared/ui/paginacion';
import { PestanasInventario } from '../components/pestanas-inventario';
import { TablaHistorial } from '../components/tabla-historial';
import { InventarioApi, NOMBRE_HISTORIAL } from '../data-access/inventario.api';

/** Historial de RF16, con los ajustes autorizados y rechazados. */
@Component({
  selector: 'app-movimientos',
  imports: [ReactiveFormsModule, RouterLink, Icono, Paginacion, PestanasInventario, TablaHistorial],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <a class="boton-icono" routerLink="/inventario" aria-label="Volver a inventario">
        <app-icono nombre="atras" [tamano]="30" />
      </a>
      <h1>Movimientos de inventario</h1>
    </div>

    <app-pestanas-inventario />

    <form class="pila" role="search" [formGroup]="filtros" (ngSubmit)="listado.recargar()">
      <div class="filtros">
        <div class="buscador">
          <app-icono nombre="buscar" />
          <input
            class="control"
            type="search"
            formControlName="q"
            placeholder="Buscar artículo…"
            aria-label="Buscar por artículo"
            maxlength="120"
          />
        </div>
        <select class="control" formControlName="tipo" aria-label="Filtrar por tipo">
          <option value="">Todos los tipos</option>
          @for (t of tipos; track t[0]) {
            <option [value]="t[0]">{{ t[1] }}</option>
          }
        </select>
      </div>
      <div class="fechas">
        <div class="campo">
          <label for="desde">Desde</label>
          <input id="desde" class="control" type="date" formControlName="desde" />
        </div>
        <div class="campo">
          <label for="hasta">Hasta</label>
          <input id="hasta" class="control" type="date" formControlName="hasta" />
        </div>
      </div>
    </form>

    <p class="mensaje"></p>

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
        <app-tabla-historial
          [movimientos]="datos.contenido"
          [cargando]="listado.vista().cargando"
        />
        <app-paginacion [datos]="datos" elementos="movimientos" (ir)="listado.irA($event)" />
      } @else {
        <p class="tarjeta">No hay movimientos que coincidan con la búsqueda.</p>
      }
    } @else {
      <p role="status">Cargando movimientos…</p>
    }
  `,
  styleUrl: '../../../shared/ui/listado.css',
  styles: `
    .fechas {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(12rem, 100%), 16rem));
      gap: 1rem;
    }
  `,
})
export class Movimientos {
  private readonly api = inject(InventarioApi);

  protected readonly tipos = Object.entries(NOMBRE_HISTORIAL);
  protected readonly filtros = inject(NonNullableFormBuilder).group({
    q: '',
    tipo: '',
    desde: '',
    hasta: '',
  });
  protected readonly listado = crearListado((p, page, automatica) =>
    this.api.movimientos(
      {
        q: p.get('q') ?? '',
        tipo: p.get('tipo') ?? '',
        desde: p.get('desde') ?? '',
        hasta: p.get('hasta') ?? '',
        page,
      },
      automatica,
    ),
  );

  constructor() {
    conectarFiltros(this.filtros, this.listado);
    inject(TiempoReal)
      .cambios('EXISTENCIA_ACTUALIZADA')
      .pipe(debounceTime(300), takeUntilDestroyed())
      .subscribe(() => this.listado.recargar(true));
  }
}
