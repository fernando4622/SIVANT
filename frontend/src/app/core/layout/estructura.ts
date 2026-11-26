import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { Icono, NombreIcono } from '../../shared/ui/icono';
import { Logo } from '../../shared/ui/logo';
import { NOMBRE_ROL, Rol } from '../auth/rol';
import { Sesion } from '../auth/sesion';
import { Bandeja } from '../notificaciones/bandeja';

interface Opcion {
  ruta: string;
  texto: string;
  icono: NombreIcono;
  /** Otra ruta que pertenece a la misma opción. */
  incluye?: string;
  roles?: Rol[];
}

/** Cada fase agrega aquí sus opciones con los roles que pueden usarlas. */
const OPCIONES: Opcion[] = [
  { ruta: '/inicio', texto: 'Inicio', icono: 'inicio' },
  { ruta: '/mesas', texto: 'Mesas', icono: 'mesas', incluye: '/cuentas', roles: ['MESERO', 'CAJERO'] },
  { ruta: '/cocina', texto: 'Cocina', icono: 'cocina', roles: ['COCINERO'] },
  { ruta: '/empleados', texto: 'Empleados', icono: 'empleados', roles: ['ADMINISTRADOR'] },
  { ruta: '/productos', texto: 'Productos', icono: 'productos', roles: ['ADMINISTRADOR', 'MESERO'] },
  { ruta: '/caja/entregas', texto: 'Caja', icono: 'cobro', incluye: '/caja', roles: ['CAJERO'] },
  { ruta: '/ventas', texto: 'Ventas', icono: 'ventas', roles: ['ADMINISTRADOR', 'CAJERO'] },
  { ruta: '/inventario', texto: 'Inventario', icono: 'inventario', roles: ['ADMINISTRADOR', 'ALMACENISTA'] },
];

@Component({
  selector: 'app-estructura',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icono, Logo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './estructura.html',
  styleUrl: './estructura.css',
})
export class Estructura {
  private readonly sesion = inject(Sesion);
  private readonly router = inject(Router);

  protected readonly perfil = this.sesion.perfil;
  protected readonly nombreRol = computed(() => {
    const perfil = this.perfil();
    return perfil ? NOMBRE_ROL[perfil.rol] : '';
  });
  protected readonly opciones = computed(() => {
    const rol = this.perfil()?.rol;
    return OPCIONES.filter((o) => rol && (!o.roles || o.roles.includes(rol)));
  });
  protected readonly url = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );
  protected readonly bandeja = inject(Bandeja);
  protected readonly menuAbierto = signal(false);
  protected readonly errorSalida = signal(false);

  protected salir(): void {
    this.errorSalida.set(false);
    this.sesion.cerrar().subscribe({
      next: () => void this.router.navigateByUrl('/login'),
      error: () => this.errorSalida.set(this.sesion.activa()),
    });
  }
}
