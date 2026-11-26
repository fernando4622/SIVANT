import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { cantidad, fechaHora, unidad } from '../../../shared/formato';
import { conectarFiltros, crearListado } from '../../../shared/listado';
import { Icono } from '../../../shared/ui/icono';
import { Paginacion } from '../../../shared/ui/paginacion';
import { PestanasInventario } from '../components/pestanas-inventario';
import { CLASE_ESTADO, InventarioApi, NOMBRE_ESTADO } from '../data-access/inventario.api';

/** Solicitudes de RF15 y RF18. */
@Component({
  selector: 'app-ajustes',
  imports: [ReactiveFormsModule, RouterLink, Icono, Paginacion, PestanasInventario],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <a class="boton-icono" routerLink="/inventario" aria-label="Volver a inventario">
        <app-icono nombre="atras" [tamano]="30" />
      </a>
      <h1>Ajustes de inventario</h1>
    </div>

    <app-pestanas-inventario />

    <form class="filtros" [formGroup]="filtros">
      <select class="control" formControlName="estado" aria-label="Filtrar por estado">
        <option value="">Todos los estados</option>
        <option value="PENDIENTE">Pendiente</option>
        <option value="AUTORIZADA">Autorizada</option>
        <option value="RECHAZADA">Rechazada</option>
      </select>
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
        <table [attr.aria-busy]="listado.vista().cargando">
          <caption class="solo-lector">
            Solicitudes de ajuste
          </caption>
          <thead>
            <tr>
              <th scope="col">Fecha</th>
              <th scope="col">Artículo</th>
              <th scope="col">Registrada</th>
              <th scope="col">Solicitada</th>
              <th scope="col">Solicitó</th>
              <th scope="col">Estado</th>
              <th scope="col">Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (s of datos.contenido; track s.id) {
              <tr>
                <th scope="row">{{ fechaHora(s.fechaHoraSolicitud) }}</th>
                <td data-etiqueta="Artículo">{{ s.articulo }}</td>
                <td data-etiqueta="Registrada" class="numero">
                  {{ cantidad(s.cantidadAnterior) }} {{ unidad(s.unidadMedida) }}
                </td>
                <td data-etiqueta="Solicitada" class="numero">
                  {{ cantidad(s.cantidadSolicitada) }} {{ unidad(s.unidadMedida) }}
                </td>
                <td data-etiqueta="Solicitó">{{ s.almacenista }}</td>
                <td data-etiqueta="Estado">
                  <span [class]="clase[s.estado]">{{ nombre[s.estado] }}</span>
                </td>
                <td class="celda-acciones">
                  <a
                    class="boton-icono"
                    [routerLink]="['/inventario/ajustes', s.id]"
                    [attr.aria-label]="'Ver ajuste ' + s.id"
                    title="Ver ajuste"
                  >
                    <app-icono nombre="ver" />
                  </a>
                </td>
              </tr>
            }
          </tbody>
        </table>
        <app-paginacion [datos]="datos" elementos="solicitudes" (ir)="listado.irA($event)" />
      } @else {
        <p class="tarjeta">No hay solicitudes.</p>
      }
    } @else {
      <p role="status">Cargando solicitudes…</p>
    }
  `,
  styleUrl: '../../../shared/ui/listado.css',
  styles: `
    .estado--autorizada {
      display: inline-flex;
      align-items: center;
      gap: 0.55rem;
      white-space: nowrap;
    }

    .estado--autorizada::before {
      width: 0.6rem;
      height: 0.6rem;
      flex: none;
      border-radius: 50%;
      background: var(--verde);
      content: '';
    }
  `,
})
export class Ajustes {
  private readonly api = inject(InventarioApi);

  protected readonly cantidad = cantidad;
  protected readonly unidad = unidad;
  protected readonly fechaHora = fechaHora;
  protected readonly nombre = NOMBRE_ESTADO;
  protected readonly clase = CLASE_ESTADO;
  protected readonly filtros = inject(NonNullableFormBuilder).group({ estado: '' });
  protected readonly listado = crearListado((p, page, automatica) =>
    this.api.ajustes({ estado: p.get('estado') ?? '', page }, automatica),
  );

  constructor() {
    conectarFiltros(this.filtros, this.listado);
  }
}
