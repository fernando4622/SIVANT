import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { cambio, cantidad, fechaHora, unidad } from '../../../shared/formato';
import { Historial, NOMBRE_CAUSA, NOMBRE_HISTORIAL } from '../data-access/inventario.api';

@Component({
  selector: 'app-tabla-historial',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <table [attr.aria-busy]="cargando()">
      <caption class="solo-lector">
        Movimientos de inventario
      </caption>
      <thead>
        <tr>
          <th scope="col">Fecha</th>
          @if (conArticulo()) {
            <th scope="col">Artículo</th>
          }
          <th scope="col">Tipo</th>
          <th scope="col">Cantidad</th>
          <th scope="col">Saldo</th>
          <th scope="col">Empleado</th>
          <th scope="col">Referencia</th>
        </tr>
      </thead>
      <tbody>
        @for (m of movimientos(); track m.tipo + m.id) {
          <tr>
            <th scope="row">{{ fechaHora(m.fechaHora) }}</th>
            @if (conArticulo()) {
              <td data-etiqueta="Artículo">
                <a class="enlace-tabla" [routerLink]="['/inventario/existencias', m.existenciaId]">{{ m.articulo }}</a>
              </td>
            }
            <td data-etiqueta="Tipo">
              <span [class.entrada]="m.tipo === 'ENTRADA_MANUAL'" [class.rechazo]="m.tipo === 'AJUSTE_RECHAZADO'">{{
                nombre[m.tipo]
              }}</span>
            </td>
            <td data-etiqueta="Cantidad" class="numero">
              @if (m.saldoPosterior === undefined) {
                <s>{{ cambio(m.cambio) }} {{ unidad(m.unidadMedida) }}</s>
              } @else {
                {{ cambio(m.cambio) }} {{ unidad(m.unidadMedida) }}
              }
            </td>
            <td data-etiqueta="Saldo" class="numero">
              {{ m.saldoPosterior === undefined ? 'Sin cambio' : cantidad(m.saldoPosterior) }}
            </td>
            <td data-etiqueta="Empleado">{{ m.empleado }}</td>
            <td data-etiqueta="Referencia">
              @if (m.solicitudId) {
                <a class="enlace-tabla" [routerLink]="['/inventario/ajustes', m.solicitudId]">Ajuste {{ m.solicitudId }}</a>
              } @else if (m.comandaId) {
                Comanda {{ m.comandaId }}
              } @else if (m.causa) {
                {{ causa[m.causa] }}{{ m.motivo ? ': ' + m.motivo : '' }}
              } @else {
                —
              }
            </td>
          </tr>
        }
      </tbody>
    </table>
  `,
  styleUrl: '../../../shared/ui/listado.css',
  styles: `
    .entrada {
      color: var(--verde);
    }

    .rechazo {
      color: var(--rojo);
    }

    .enlace-tabla {
      text-decoration: none;
    }

    .enlace-tabla:hover,
    .enlace-tabla:focus-visible {
      color: var(--azul-fuerte);
    }
  `,
})
export class TablaHistorial {
  readonly movimientos = input.required<Historial[]>();
  readonly conArticulo = input(true);
  readonly cargando = input(false);

  protected readonly nombre = NOMBRE_HISTORIAL;
  protected readonly causa = NOMBRE_CAUSA;
  protected readonly cambio = cambio;
  protected readonly cantidad = cantidad;
  protected readonly fechaHora = fechaHora;
  protected readonly unidad = unidad;
}
