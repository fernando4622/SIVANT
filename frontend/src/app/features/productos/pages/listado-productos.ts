import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Sesion } from '../../../core/auth/sesion';
import { dinero } from '../../../shared/formato';
import { conectarFiltros, crearListado } from '../../../shared/listado';
import { Icono } from '../../../shared/ui/icono';
import { Paginacion } from '../../../shared/ui/paginacion';
import { NOMBRE_TIPO, Producto, ProductosApi } from '../data-access/productos.api';

/** RF08 y RF10. El mesero consulta el catálogo activo sin acciones. */
@Component({
  selector: 'app-listado-productos',
  imports: [ReactiveFormsModule, RouterLink, Icono, Paginacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './listado-productos.html',
  styleUrl: '../../../shared/ui/listado.css',
  styles: `
    .productos {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(22rem, 100%), 1fr));
      gap: 1rem;
      margin: 1.5rem 0 0;
      padding: 0;
      list-style: none;
    }

    .producto {
      display: grid;
      grid-template-columns: 7.5rem minmax(0, 1fr);
      gap: 1rem;
      min-height: 10rem;
      padding: 0.85rem;
      box-shadow: var(--sombra);
    }

    .producto__imagen {
      display: grid;
      place-items: center;
      overflow: hidden;
      min-height: 8.25rem;
      border-radius: var(--radio-chico);
      background: var(--cabecera);
      color: var(--texto-suave);
    }

    .producto__imagen img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .producto__contenido {
      display: grid;
      grid-template-rows: auto 1fr auto;
      gap: 0.55rem;
      min-width: 0;
    }

    .producto__encabezado {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
    }

    .producto__encabezado .boton-icono {
      width: 2.4rem;
      min-width: 2.4rem;
      min-height: 2.4rem;
    }

    .producto__texto h2 {
      font-size: 1.05rem;
    }

    .producto__texto p {
      margin-top: 0.2rem;
      color: var(--texto-suave);
      overflow-wrap: anywhere;
    }

    .producto__pie {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: center;
      gap: 1rem;
      padding-top: 0.65rem;
      border-top: 1px solid var(--borde);
    }

    .producto__pie span {
      text-align: center;
      font-weight: 600;
    }

    .producto__pie strong {
      padding-left: 1rem;
      border-left: 1px solid var(--borde);
      font-size: 1.15rem;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }

    @media (max-width: 28rem) {
      .producto {
        grid-template-columns: 5.5rem minmax(0, 1fr);
      }

      .producto__imagen {
        min-height: 6.5rem;
      }
    }
  `,
})
export class ListadoProductos {
  private readonly api = inject(ProductosApi);
  private readonly sesion = inject(Sesion);

  protected readonly administra = computed(() => this.sesion.perfil()?.rol === 'ADMINISTRADOR');
  protected readonly nombreTipo = NOMBRE_TIPO;
  protected readonly dinero = dinero;
  protected readonly filtros = inject(NonNullableFormBuilder).group({ q: '', tipo: '', activo: '' });
  protected readonly listado = crearListado((p, page) =>
    this.api.listar({ q: p.get('q') ?? '', tipo: p.get('tipo') ?? '', activo: p.get('activo') ?? '', page }),
  );

  /** Productos cuya imagen no cargó: se muestra el marcador. */
  protected readonly sinImagen = signal(new Set<string>());

  constructor() {
    conectarFiltros(this.filtros, this.listado);
  }

  protected ocultarImagen(id: string): void {
    this.sinImagen.update((ids) => new Set(ids).add(id));
  }
}
