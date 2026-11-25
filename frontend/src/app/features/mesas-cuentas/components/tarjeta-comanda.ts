import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { dinero, fechaHora } from '../../../shared/formato';
import { Icono } from '../../../shared/ui/icono';
import { CLASE_COMANDA, Comanda, NOMBRE_COMANDA } from '../data-access/servicio.api';

/** Comanda con sus productos. Las acciones se proyectan dentro. */
@Component({
  selector: 'app-tarjeta-comanda',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="tarjeta" [class.cancelada]="comanda().estado === 'CANCELADA'">
      <header>
        <h2>Comanda {{ comanda().id }}</h2>
        <span [class]="clase[comanda().estado]">{{ nombre[comanda().estado] }}</span>
      </header>
      <p class="dato"><app-icono nombre="calendario" />{{ fechaHora(comanda().fechaHoraEnvio) }}</p>
      @if (mesero()) {
        <p class="dato"><app-icono nombre="persona" />Mesero: {{ mesero() }}</p>
      }
      <ul>
        @for (d of comanda().detalles; track d.id) {
          <li>
            <span class="cantidad">{{ d.cantidad }} ×</span>
            <span class="producto">
              {{ d.producto }}
              @if (d.observaciones) {
                <span class="observaciones">{{ d.observaciones }}</span>
              }
            </span>
            <strong>{{ dinero(d.subtotal) }}</strong>
          </li>
        }
      </ul>
      <p class="total">
        <span>{{ comanda().estado === 'CANCELADA' ? 'No se cobra' : 'Subtotal' }}</span>
        <strong>{{ dinero(comanda().subtotal) }}</strong>
      </p>
      @if (comanda().cancelacion; as c) {
        <p class="aviso aviso--error">
          <app-icono nombre="info" />
          <span>Cancelada por {{ c.cajero }} el {{ fechaHora(c.fechaHora) }}. {{ c.justificacion }}</span>
        </p>
      }
      <ng-content />
    </article>
  `,
  styleUrl: './tarjeta-comanda.css',
})
export class TarjetaComanda {
  readonly comanda = input.required<Comanda>();
  readonly mesero = input('');

  protected readonly nombre = NOMBRE_COMANDA;
  protected readonly clase = CLASE_COMANDA;
  protected readonly dinero = dinero;
  protected readonly fechaHora = fechaHora;
}
