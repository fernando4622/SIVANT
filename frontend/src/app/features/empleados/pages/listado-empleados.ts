import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { NOMBRE_ROL, ROLES, Rol } from '../../../core/auth/rol';
import { conectarFiltros, crearListado } from '../../../shared/listado';
import { Icono } from '../../../shared/ui/icono';
import { Paginacion } from '../../../shared/ui/paginacion';
import { EmpleadosApi } from '../data-access/empleados.api';

const IMAGEN_ROL: Record<Rol, string> = {
  ADMINISTRADOR: '/cargo-administrador.png',
  MESERO: '/cargo-mesero.png',
  CAJERO: '/cargo-cajero.png',
  COCINERO: '/cargo-cocinero.png',
  ALMACENISTA: '/cargo-almacenista.png',
};

@Component({
  selector: 'app-listado-empleados',
  imports: [ReactiveFormsModule, RouterLink, Icono, Paginacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './listado-empleados.html',
  styleUrls: ['../../../shared/ui/listado.css', './listado-empleados.css'],
})
export class ListadoEmpleados {
  private readonly api = inject(EmpleadosApi);
  protected readonly roles = ROLES;
  protected readonly nombreRol = NOMBRE_ROL;
  protected readonly imagenRol = IMAGEN_ROL;

  protected readonly filtros = inject(NonNullableFormBuilder).group({ q: '', rol: '', estado: '' });
  protected readonly listado = crearListado((p, page) =>
    this.api.listar({ q: p.get('q') ?? '', rol: p.get('rol') ?? '', estado: p.get('estado') ?? '', page }),
  );

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

}
