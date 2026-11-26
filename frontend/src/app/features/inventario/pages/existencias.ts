import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { TiempoReal } from '../../../core/realtime/tiempo-real';
import { cantidad, unidad } from '../../../shared/formato';
import { conectarFiltros, crearListado } from '../../../shared/listado';
import { Icono } from '../../../shared/ui/icono';
import { Paginacion } from '../../../shared/ui/paginacion';
import { PestanasInventario } from '../components/pestanas-inventario';
import { InventarioApi } from '../data-access/inventario.api';

/** RF16. */
@Component({
  selector: 'app-existencias',
  imports: [ReactiveFormsModule, RouterLink, Icono, Paginacion, PestanasInventario],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './existencias.html',
  styleUrl: '../../../shared/ui/listado.css',
  styles: `
    .existencias {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(23rem, 100%), 1fr));
      gap: 1rem;
      margin: 1.5rem 0 0;
      padding: 0;
      list-style: none;
    }

    .existencia {
      display: grid;
      grid-template-columns: 7.5rem minmax(0, 1fr);
      gap: 1rem;
      min-height: 10rem;
      padding: 0.85rem;
      box-shadow: var(--sombra);
    }

    .existencia__imagen {
      display: grid;
      place-items: center;
      min-height: 8.25rem;
      border-radius: var(--radio-chico);
      background: var(--cabecera);
      color: var(--texto-suave);
    }

    .existencia__contenido {
      display: grid;
      grid-template-rows: auto 1fr auto;
      gap: 0.55rem;
      min-width: 0;
    }

    .existencia__encabezado {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
    }

    .existencia__encabezado .boton-icono {
      width: 2.4rem;
      min-width: 2.4rem;
      min-height: 2.4rem;
    }

    .existencia__texto h2 {
      font-size: 1.05rem;
    }

    .existencia__texto p,
    .existencia__cantidades dt {
      color: var(--texto-suave);
    }

    .existencia__texto p {
      margin-top: 0.2rem;
    }

    .existencia__cantidades {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      margin: 0;
      padding-top: 0.65rem;
      border-top: 1px solid var(--borde);
    }

    .existencia__cantidades > div {
      display: grid;
      gap: 0.15rem;
      padding-right: 0.75rem;
    }

    .existencia__cantidades > div + div {
      padding-right: 0;
      padding-left: 0.75rem;
      border-left: 1px solid var(--borde);
    }

    .existencia__cantidades dd {
      margin: 0;
      font-weight: 650;
      font-variant-numeric: tabular-nums;
    }

    .nivel {
      display: inline-flex;
      align-items: center;
      gap: 0.55rem;
      white-space: nowrap;
    }

    .nivel::before {
      width: 0.6rem;
      height: 0.6rem;
      flex: none;
      border-radius: 50%;
      background: var(--verde);
      content: '';
    }

    .nivel--bajo::before {
      background: var(--naranja);
    }

    @media (max-width: 28rem) {
      .existencia {
        grid-template-columns: 5.5rem minmax(0, 1fr);
      }

      .existencia__imagen {
        min-height: 6.5rem;
      }

      .existencia__cantidades {
        grid-template-columns: minmax(0, 1fr);
        gap: 0.55rem;
      }

      .existencia__cantidades > div + div {
        padding: 0.55rem 0 0;
        border-top: 1px solid var(--borde);
        border-left: 0;
      }
    }
  `,
})
export class Existencias {
  private readonly api = inject(InventarioApi);

  protected readonly cantidad = cantidad;
  protected readonly unidad = unidad;
  protected readonly filtros = inject(NonNullableFormBuilder).group({
    q: '',
    tipo: '',
    nivelBajo: '',
  });
  protected readonly listado = crearListado((p, page, automatica) =>
    this.api.existencias(
      {
        q: p.get('q') ?? '',
        tipo: p.get('tipo') ?? '',
        nivelBajo: p.get('nivelBajo') ?? '',
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
