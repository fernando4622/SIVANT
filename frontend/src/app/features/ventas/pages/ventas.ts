import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { dinero, fechaHora } from '../../../shared/formato';
import { conectarFiltros, crearListado } from '../../../shared/listado';
import { Icono } from '../../../shared/ui/icono';
import { Paginacion } from '../../../shared/ui/paginacion';
import { CLASE_VENTA, NOMBRE_METODO, NOMBRE_VENTA, Opciones, VentasApi } from '../data-access/ventas.api';

/** RF06: ventas completadas y canceladas de los últimos 24 meses. */
@Component({
  selector: 'app-ventas',
  imports: [ReactiveFormsModule, RouterLink, Icono, Paginacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <a class="boton-icono" routerLink="/inicio" aria-label="Volver a inicio">
        <app-icono nombre="atras" [tamano]="30" />
      </a>
      <h1>Historial de ventas</h1>
    </div>

    <form class="pila" role="search" [formGroup]="filtros" (ngSubmit)="listado.recargar()">
      <div class="rejilla">
        <div class="campo">
          <label for="desde">Desde</label>
          <input id="desde" class="control" type="date" formControlName="desde" />
        </div>
        <div class="campo">
          <label for="hasta">Hasta</label>
          <input id="hasta" class="control" type="date" formControlName="hasta" />
        </div>
        <div class="campo">
          <label for="estado">Estado</label>
          <select id="estado" class="control" formControlName="estado">
            <option value="">Todos</option>
            @for (e of estados; track e[0]) {
              <option [value]="e[0]">{{ e[1] }}</option>
            }
          </select>
        </div>
        <div class="campo">
          <label for="metodo">Método</label>
          <select id="metodo" class="control" formControlName="metodo">
            <option value="">Todos</option>
            @for (m of metodos; track m[0]) {
              <option [value]="m[0]">{{ m[1] }}</option>
            }
          </select>
        </div>
        <div class="campo">
          <label for="meseroId">Mesero</label>
          <select id="meseroId" class="control" formControlName="meseroId">
            <option value="">Todos</option>
            @for (m of opciones().meseros; track m.id) {
              <option [value]="m.id">{{ m.nombre }}</option>
            }
          </select>
        </div>
        <div class="campo">
          <label for="mesaId">Mesa</label>
          <select id="mesaId" class="control" formControlName="mesaId">
            <option value="">Todas</option>
            @for (m of opciones().mesas; track m.id) {
              <option [value]="m.id">{{ m.nombre }}</option>
            }
          </select>
        </div>
      </div>
    </form>

    <p class="campo__ayuda">Se consultan las ventas de los últimos 24 meses.</p>

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
        <table [attr.aria-busy]="listado.vista().cargando">
          <caption class="solo-lector">
            Ventas registradas
          </caption>
          <thead>
            <tr>
              <th scope="col">Venta</th>
              <th scope="col">Fecha</th>
              <th scope="col">Mesa</th>
              <th scope="col">Mesero</th>
              <th scope="col">Método</th>
              <th scope="col">Total</th>
              <th scope="col">Estado</th>
              <th scope="col"><span class="solo-lector">Acciones</span></th>
            </tr>
          </thead>
          <tbody>
            @for (v of datos.contenido; track v.id) {
              <tr>
                <th scope="row">Venta {{ v.id }}</th>
                <td data-etiqueta="Fecha">{{ fechaHora(v.fechaHora) }}</td>
                <td data-etiqueta="Mesa">{{ v.mesa }}</td>
                <td data-etiqueta="Mesero">{{ v.mesero }}</td>
                <td data-etiqueta="Método">{{ nombreMetodo[v.metodo] }}</td>
                <td data-etiqueta="Total" class="numero">{{ dinero(v.total) }}</td>
                <td data-etiqueta="Estado"><span [class]="clase[v.estado]">{{ nombreEstado[v.estado] }}</span></td>
                <td class="celda-acciones">
                  <a
                    class="boton-icono"
                    [routerLink]="['/ventas', v.id]"
                    [attr.aria-label]="'Ver venta ' + v.id"
                    title="Ver detalle"
                  >
                    <app-icono nombre="ver" />
                  </a>
                </td>
              </tr>
            }
          </tbody>
        </table>
        <app-paginacion [datos]="datos" elementos="ventas" (ir)="listado.irA($event)" />
      } @else {
        <p class="tarjeta">No hay ventas que coincidan con los filtros.</p>
      }
    } @else {
      <p role="status">Cargando ventas…</p>
    }
  `,
  styleUrl: '../../../shared/ui/listado.css',
  styles: `
    .rejilla {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(9rem, 100%), 1fr));
      gap: 0.75rem 1rem;
    }

    /* El campo de fecha tiene un ancho mínimo propio que lo haría salir de su columna. */
    .rejilla .campo,
    .rejilla .control {
      min-width: 0;
    }

    .campo__ayuda {
      margin: 0.75rem 0 1rem;
    }
  `,
})
export class Ventas {
  private readonly api = inject(VentasApi);

  protected readonly estados = Object.entries(NOMBRE_VENTA);
  protected readonly metodos = Object.entries(NOMBRE_METODO);
  protected readonly nombreEstado = NOMBRE_VENTA;
  protected readonly nombreMetodo = NOMBRE_METODO;
  protected readonly clase = CLASE_VENTA;
  protected readonly dinero = dinero;
  protected readonly fechaHora = fechaHora;
  protected readonly opciones = toSignal(
    this.api.opciones().pipe(catchError(() => of<Opciones>({ meseros: [], mesas: [] }))),
    { initialValue: { meseros: [], mesas: [] } as Opciones },
  );
  protected readonly filtros = inject(NonNullableFormBuilder).group({
    desde: '',
    hasta: '',
    estado: '',
    metodo: '',
    meseroId: '',
    mesaId: '',
  });
  protected readonly listado = crearListado((p, page) =>
    this.api.ventas({
      desde: p.get('desde') ?? '',
      hasta: p.get('hasta') ?? '',
      estado: p.get('estado') ?? '',
      metodo: p.get('metodo') ?? '',
      meseroId: p.get('meseroId') ?? '',
      mesaId: p.get('mesaId') ?? '',
      page,
    }),
  );

  constructor() {
    conectarFiltros(this.filtros, this.listado);
  }
}
