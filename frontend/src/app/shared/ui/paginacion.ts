import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Pagina } from '../listado';
import { Icono } from './icono';

@Component({
  selector: 'app-paginacion',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p>{{ rango() }}</p>
    <nav aria-label="Páginas">
      <button
        type="button"
        class="boton pagina"
        aria-label="Página anterior"
        [disabled]="datos().page === 0"
        (click)="ir.emit(datos().page - 1)"
      >
        <app-icono nombre="anterior" />
      </button>
      <span class="pagina pagina--actual" aria-current="page">
        <span class="solo-lector">Página </span>{{ datos().page + 1 }}
        <span class="solo-lector">de {{ datos().totalPaginas }}</span>
      </span>
      <button
        type="button"
        class="boton pagina"
        aria-label="Página siguiente"
        [disabled]="datos().page + 1 >= datos().totalPaginas"
        (click)="ir.emit(datos().page + 1)"
      >
        <app-icono nombre="siguiente" />
      </button>
    </nav>
  `,
  styles: `
    :host {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      margin-top: 1.5rem;
      font-weight: 600;
    }

    nav {
      display: flex;
      gap: 1rem;
    }

    .pagina {
      display: grid;
      place-items: center;
      min-width: 3.25rem;
      min-height: 3rem;
      padding: 0.25rem;
    }

    .pagina--actual {
      border-radius: var(--radio-chico);
      background: var(--azul);
      color: #fff;
    }
  `,
})
export class Paginacion {
  readonly datos = input.required<Pagina<unknown>>();
  /** Plural de lo que se lista: «empleados», «productos». */
  readonly elementos = input.required<string>();
  readonly ir = output<number>();

  protected readonly rango = computed(() => {
    const { page, size, contenido, totalElementos } = this.datos();
    const desde = page * size + 1;
    return `Mostrando ${desde}–${desde + contenido.length - 1} de ${totalElementos} ${this.elementos()}`;
  });
}
