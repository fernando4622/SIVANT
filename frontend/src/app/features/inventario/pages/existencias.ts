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
