import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Sesion } from '../../../core/auth/sesion';
import { leerProblema } from '../../../core/http/problema';
import { dinero } from '../../../shared/formato';
import { conectarFiltros, crearListado } from '../../../shared/listado';
import { Confirmacion } from '../../../shared/ui/confirmacion';
import { Icono } from '../../../shared/ui/icono';
import { Paginacion } from '../../../shared/ui/paginacion';
import { NOMBRE_TIPO, Producto, ProductosApi } from '../data-access/productos.api';

/** RF08 y RF10. El mesero consulta el catálogo activo sin acciones. */
@Component({
  selector: 'app-listado-productos',
  imports: [ReactiveFormsModule, RouterLink, Icono, Confirmacion, Paginacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './listado-productos.html',
  styleUrl: '../../../shared/ui/listado.css',
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

  protected readonly porDesactivar = signal<Producto | null>(null);
  protected readonly desactivando = signal(false);
  protected readonly errorDesactivacion = signal<string | null>(null);
  protected readonly mensaje = signal('');
  private readonly confirmacion = viewChild.required(Confirmacion);

  constructor() {
    conectarFiltros(this.filtros, this.listado);
  }

  protected pedirConfirmacion(producto: Producto): void {
    this.errorDesactivacion.set(null);
    this.mensaje.set('');
    this.porDesactivar.set(producto);
    this.confirmacion().abrir();
  }

  protected desactivar(): void {
    const producto = this.porDesactivar();
    if (!producto || this.desactivando()) {
      return;
    }
    this.desactivando.set(true);
    this.api.desactivar(producto.id).subscribe({
      next: () => {
        this.desactivando.set(false);
        this.confirmacion().cerrar();
        this.mensaje.set(`${producto.nombre} ya no se ofrece en nuevas comandas.`);
        this.listado.recargar();
      },
      error: (error: unknown) => {
        this.desactivando.set(false);
        this.errorDesactivacion.set(leerProblema(error).detalle);
      },
    });
  }
}
