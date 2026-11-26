import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { dinero, fechaHora } from '../../../shared/formato';
import { conectarFiltros, crearListado } from '../../../shared/listado';
import { Icono } from '../../../shared/ui/icono';
import { Paginacion } from '../../../shared/ui/paginacion';
import { PestanasCaja } from '../components/pestanas-caja';
import { CLASE_FONDO, CajaApi, NOMBRE_FONDO } from '../data-access/caja.api';

/** RF26: seguimiento de cada fondo hasta su aplicación y liquidación, o su anulación. */
@Component({
  selector: 'app-fondos',
  imports: [ReactiveFormsModule, RouterLink, Icono, Paginacion, PestanasCaja],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <a class="boton-icono" routerLink="/inicio" aria-label="Volver a inicio">
        <app-icono nombre="atras" [tamano]="30" />
      </a>
      <h1>Fondos de cambio</h1>
    </div>

    <app-pestanas-caja />

    <form class="filtros" role="search" [formGroup]="filtros" (ngSubmit)="listado.recargar()">
      <select class="control" formControlName="estado" aria-label="Filtrar por estado">
        <option value="">Todos los estados</option>
        @for (e of estados; track e[0]) {
          <option [value]="e[0]">{{ e[1] }}</option>
        }
      </select>
    </form>

    <p class="campo__ayuda">Para entregar un fondo, abre la cuenta desde Mesas.</p>

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
            Fondos de cambio
          </caption>
          <thead>
            <tr>
              <th scope="col">Fondo</th>
              <th scope="col">Fecha</th>
              <th scope="col">Cuenta</th>
              <th scope="col">Mesero</th>
              <th scope="col">Monto</th>
              <th scope="col">Estado</th>
              <th scope="col">Seguimiento</th>
            </tr>
          </thead>
          <tbody>
            @for (f of datos.contenido; track f.id) {
              <tr>
                <th scope="row">Fondo {{ f.id }}</th>
                <td data-etiqueta="Fecha">{{ fechaHora(f.fechaHora) }}</td>
                <td data-etiqueta="Cuenta">
                  <a [routerLink]="['/cuentas', f.cuentaId]">Cuenta {{ f.cuentaId }} · Mesa {{ f.mesa }}</a>
                </td>
                <td data-etiqueta="Mesero">{{ f.mesero }}</td>
                <td data-etiqueta="Monto" class="numero">{{ dinero(f.monto) }}</td>
                <td data-etiqueta="Estado"><span [class]="clase[f.estado]">{{ nombre[f.estado] }}</span></td>
                <td data-etiqueta="Seguimiento">
                  @if (f.anulacion; as a) {
                    {{ a.justificacion }}
                  } @else if (f.entregaId) {
                    Entrega {{ f.entregaId }}
                  } @else if (f.ventaId) {
                    Venta {{ f.ventaId }}
                  } @else {
                    Entregó {{ f.cajero }}
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
        <app-paginacion [datos]="datos" elementos="fondos" (ir)="listado.irA($event)" />
      } @else {
        <p class="tarjeta">No hay fondos de cambio con ese estado.</p>
      }
    } @else {
      <p role="status">Cargando fondos…</p>
    }
  `,
  styleUrl: '../../../shared/ui/listado.css',
  styles: `
    .filtros {
      max-width: 22rem;
    }

    .campo__ayuda {
      margin: 0.75rem 0 1rem;
    }
  `,
})
export class Fondos {
  private readonly api = inject(CajaApi);

  protected readonly estados = Object.entries(NOMBRE_FONDO);
  protected readonly nombre = NOMBRE_FONDO;
  protected readonly clase = CLASE_FONDO;
  protected readonly dinero = dinero;
  protected readonly fechaHora = fechaHora;
  protected readonly filtros = inject(NonNullableFormBuilder).group({ estado: '' });
  protected readonly listado = crearListado((p, page) => this.api.fondos(p.get('estado') ?? '', page));

  constructor() {
    conectarFiltros(this.filtros, this.listado);
  }
}
