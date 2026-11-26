import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  output,
  viewChild,
} from '@angular/core';
import { Icono, NombreIcono } from './icono';

/** Diálogo modal nativo: conserva el foco dentro y se cierra con Escape. */
@Component({
  selector: 'app-confirmacion',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialogo aria-labelledby="confirmacion-titulo" (cancel)="alCancelar($event)">
      <h2 id="confirmacion-titulo">{{ titulo() }}</h2>
      <div class="aviso">
        <app-icono nombre="info" [tamano]="32" />
        <p><ng-content /></p>
      </div>
      @if (error(); as mensaje) {
        <p class="campo__error" role="alert">{{ mensaje }}</p>
      }
      <div class="acciones">
        <button type="button" class="boton" [disabled]="ocupado()" (click)="cerrar()">
          Cancelar
        </button>
        <button
          type="button"
          [class]="peligro() ? 'boton boton--peligro' : 'boton boton--principal'"
          [disabled]="ocupado()"
          (click)="confirmado.emit()"
        >
          <app-icono [nombre]="icono()" />
          {{ accion() }}
        </button>
      </div>
    </dialog>
  `,
  styles: `
    dialog {
      width: min(34rem, calc(100vw - 2rem));
      padding: 1.5rem;
      border: 2px solid var(--borde);
      border-radius: var(--radio);
      color: var(--texto);
      box-shadow: var(--sombra);
    }

    dialog[open] {
      display: grid;
      gap: 1.25rem;
    }

    dialog::backdrop {
      background: rgb(15 26 58 / 55%);
    }
  `,
})
export class Confirmacion {
  readonly titulo = input.required<string>();
  readonly accion = input.required<string>();
  readonly icono = input<NombreIcono>('basura');
  /** Falso en acciones habituales que no retiran nada. */
  readonly peligro = input(true);
  readonly ocupado = input(false);
  readonly error = input<string | null>(null);
  readonly confirmado = output<void>();
  readonly cancelado = output<void>();

  private readonly dialogo = viewChild.required<ElementRef<HTMLDialogElement>>('dialogo');

  abrir(): void {
    this.dialogo().nativeElement.showModal();
  }

  cerrar(): void {
    this.dialogo().nativeElement.close();
    this.cancelado.emit();
  }

  protected alCancelar(evento: Event): void {
    evento.preventDefault();
    if (!this.ocupado()) {
      this.cerrar();
    }
  }
}
