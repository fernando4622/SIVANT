import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { NOMBRE_ROL, ROLES } from '../../../core/auth/rol';
import { leerProblema } from '../../../core/http/problema';
import { conectarFiltros, crearListado } from '../../../shared/listado';
import { Confirmacion } from '../../../shared/ui/confirmacion';
import { Icono } from '../../../shared/ui/icono';
import { Paginacion } from '../../../shared/ui/paginacion';
import { Empleado, EmpleadosApi } from '../data-access/empleados.api';

@Component({
  selector: 'app-listado-empleados',
  imports: [ReactiveFormsModule, RouterLink, Icono, Confirmacion, Paginacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './listado-empleados.html',
  styleUrls: ['../../../shared/ui/listado.css', './listado-empleados.css'],
})
export class ListadoEmpleados {
  private readonly api = inject(EmpleadosApi);
  protected readonly roles = ROLES;
  protected readonly nombreRol = NOMBRE_ROL;

  protected readonly filtros = inject(NonNullableFormBuilder).group({ q: '', rol: '', estado: '' });
  protected readonly listado = crearListado((p, page) =>
    this.api.listar({ q: p.get('q') ?? '', rol: p.get('rol') ?? '', estado: p.get('estado') ?? '', page }),
  );

  protected readonly seleccionado = signal<Empleado | null>(null);
  protected readonly reactivacion = computed(() => this.seleccionado()?.estado === 'INACTIVO');
  protected readonly actualizando = signal(false);
  protected readonly errorActualizacion = signal<string | null>(null);
  protected readonly mensaje = signal('');
  private readonly confirmacion = viewChild.required(Confirmacion);

  constructor() {
    conectarFiltros(this.filtros, this.listado);
  }

  protected iniciales(nombre: string): string {
    const palabras = nombre.trim().split(/\s+/);
    return palabras
      .slice(0, 2)
      .map((p) => p.charAt(0).toUpperCase())
      .join('');
  }

  protected pedirConfirmacion(empleado: Empleado): void {
    this.errorActualizacion.set(null);
    this.mensaje.set('');
    this.seleccionado.set(empleado);
    this.confirmacion().abrir();
  }

  protected cambiarEstado(): void {
    const empleado = this.seleccionado();
    if (!empleado || this.actualizando()) {
      return;
    }
    const reactivar = empleado.estado === 'INACTIVO';
    const solicitud = reactivar ? this.api.reactivar(empleado.id) : this.api.desactivar(empleado.id);
    this.actualizando.set(true);
    this.errorActualizacion.set(null);
    solicitud.subscribe({
      next: () => {
        this.actualizando.set(false);
        this.confirmacion().cerrar();
        this.mensaje.set(reactivar
          ? `${empleado.nombreCompleto} ya puede iniciar sesión.`
          : `${empleado.nombreCompleto} quedó inactivo y ya no puede acceder.`);
        this.listado.recargar();
      },
      error: (error: unknown) => {
        this.actualizando.set(false);
        this.errorActualizacion.set(leerProblema(error).detalle);
      },
    });
  }
}
