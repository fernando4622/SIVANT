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

  protected readonly seleccionado = signal<Producto | null>(null);
  protected readonly reactivacion = computed(() => this.seleccionado()?.activo === false);
  protected readonly actualizando = signal(false);
  protected readonly errorActualizacion = signal<string | null>(null);
  protected readonly mensaje = signal('');
  private readonly confirmacion = viewChild.required(Confirmacion);

  constructor() {
    conectarFiltros(this.filtros, this.listado);
  }

  protected pedirConfirmacion(producto: Producto): void {
    this.errorActualizacion.set(null);
    this.mensaje.set('');
    this.seleccionado.set(producto);
    this.confirmacion().abrir();
  }

  protected cambiarEstado(): void {
    const producto = this.seleccionado();
    if (!producto || this.actualizando()) {
      return;
    }
    const reactivar = !producto.activo;
    this.actualizando.set(true);
    this.errorActualizacion.set(null);
    const solicitud = reactivar ? this.api.reactivar(producto.id) : this.api.desactivar(producto.id);
    solicitud.subscribe({
      next: () => {
        this.actualizando.set(false);
        this.confirmacion().cerrar();
        this.mensaje.set(reactivar
          ? `${producto.nombre} vuelve a estar disponible para nuevas comandas.`
          : `${producto.nombre} ya no se ofrece en nuevas comandas.`);
        this.listado.recargar();
      },
      error: (error: unknown) => {
        this.actualizando.set(false);
        this.errorActualizacion.set(leerProblema(error).detalle);
      },
    });
  }
}
