import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Icono } from '../../../shared/ui/icono';

const MAXIMO = 999;

@Component({
  selector: 'app-paso-cantidad',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      [attr.aria-label]="'Quitar uno de ' + nombre()"
      [disabled]="cantidad() <= 1"
      (click)="cambio.emit(cantidad() - 1)"
    >
      <app-icono nombre="menos" [tamano]="20" />
    </button>
    <span role="status" [attr.aria-label]="cantidad() + ' de ' + nombre()">{{ cantidad() }}</span>
    <button
      type="button"
      [attr.aria-label]="'Agregar uno de ' + nombre()"
      [disabled]="cantidad() >= maximo"
      (click)="cambio.emit(cantidad() + 1)"
    >
      <app-icono nombre="mas" [tamano]="20" />
    </button>
  `,
  styles: `
    :host {
      display: inline-grid;
      justify-self: start;
      grid-template-columns: 2.75rem 3rem 2.75rem;
      align-items: center;
      border-radius: var(--radio-chico);
      background: var(--cabecera);
    }

    button {
      display: grid;
      place-items: center;
      height: 2.75rem;
      border: 0;
      border-radius: var(--radio-chico);
      background: transparent;
      cursor: pointer;
    }

    button:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }

    span {
      padding: 0.35rem 0;
      border-radius: 0.35rem;
      background: var(--superficie);
      font-weight: 600;
      text-align: center;
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class PasoCantidad {
  readonly nombre = input.required<string>();
  readonly cantidad = input.required<number>();
  readonly cambio = output<number>();
  protected readonly maximo = MAXIMO;
}
