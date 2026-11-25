import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NOMBRE_ROL, Rol } from '../../core/auth/rol';
import { Sesion } from '../../core/auth/sesion';
import { Icono } from '../../shared/ui/icono';

interface Funcion {
  texto: string;
  ruta?: string;
}

/** Al implementar una fase se agrega la ruta de cada función. Sin ruta todavía no está disponible. */
const FUNCIONES: Record<Rol, Funcion[]> = {
  ADMINISTRADOR: [
    { texto: 'Gestionar empleados', ruta: '/empleados' },
    { texto: 'Gestionar productos', ruta: '/productos' },
    { texto: 'Consultar y cancelar ventas' },
    { texto: 'Consultar inventario', ruta: '/inventario' },
    { texto: 'Resolver ajustes de inventario', ruta: '/inventario/ajustes' },
  ],
  MESERO: [
    { texto: 'Consultar el catálogo', ruta: '/productos' },
    { texto: 'Abrir cuentas y registrar comandas', ruta: '/mesas' },
    { texto: 'Servir comandas', ruta: '/mesas' },
    { texto: 'Cobrar cuentas' },
  ],
  CAJERO: [
    { texto: 'Cancelar comandas', ruta: '/mesas' },
    { texto: 'Consultar ventas' },
    { texto: 'Recibir efectivo y entregar fondo de cambio' },
  ],
  COCINERO: [{ texto: 'Consultar la cola y preparar comandas', ruta: '/cocina' }],
  ALMACENISTA: [
    { texto: 'Consultar inventario y registrar movimientos', ruta: '/inventario' },
    { texto: 'Consultar solicitudes de ajuste', ruta: '/inventario/ajustes' },
  ],
};

@Component({
  selector: 'app-inicio',
  imports: [RouterLink, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <h1>Hola, {{ perfil()?.nombre }}</h1>
    </div>

    <div class="contenido">
      <section class="tarjeta" aria-labelledby="funciones">
        <h2 id="funciones">Funciones de {{ nombreRol() }}</h2>
        <ul>
          @for (funcion of funciones(); track funcion.texto) {
            <li>
              @if (funcion.ruta) {
                <a [routerLink]="funcion.ruta">{{ funcion.texto }}</a>
              } @else {
                <span>{{ funcion.texto }}</span>
                <span class="pendiente">Próximamente</span>
              }
            </li>
          }
        </ul>
      </section>

      @if (hayPendientes()) {
        <div class="aviso">
          <app-icono nombre="info" [tamano]="32" />
          <p>Las funciones marcadas como «Próximamente» se habilitarán en las siguientes fases.</p>
        </div>
      }
    </div>
  `,
  styles: `
    .contenido {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(20rem, 100%), 1fr));
      align-items: start;
      gap: 1.25rem;
    }

    ul {
      margin: 1rem 0 0;
      padding: 0;
      list-style: none;
    }

    li {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.25rem 1rem;
      min-height: 3.25rem;
      border-top: 2px solid var(--borde);
    }

    .pendiente {
      color: var(--texto-suave);
    }
  `,
})
export class Inicio {
  protected readonly perfil = inject(Sesion).perfil;
  protected readonly nombreRol = computed(() => {
    const perfil = this.perfil();
    return perfil ? NOMBRE_ROL[perfil.rol] : '';
  });
  protected readonly funciones = computed(() => {
    const perfil = this.perfil();
    return perfil ? FUNCIONES[perfil.rol] : [];
  });
  protected readonly hayPendientes = computed(() => this.funciones().some((f) => !f.ruta));
}
