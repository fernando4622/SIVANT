import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NOMBRE_ROL, Rol } from '../../core/auth/rol';
import { Sesion } from '../../core/auth/sesion';
import { Icono, NombreIcono } from '../../shared/ui/icono';

interface Funcion {
  texto: string;
  descripcion: string;
  ruta: string;
  icono: NombreIcono;
  imagen?: 'ajustes' | 'caja' | 'empleados' | 'inventario' | 'mesas' | 'productos' | 'ventas';
}

const FUNCIONES: Record<Rol, readonly Funcion[]> = {
  ADMINISTRADOR: [
    {
      texto: 'Empleados',
      descripcion: 'Gestiona al personal y sus accesos.',
      ruta: '/empleados',
      icono: 'empleados',
      imagen: 'empleados',
    },
    {
      texto: 'Productos',
      descripcion: 'Administra el catálogo y los precios.',
      ruta: '/productos',
      icono: 'productos',
      imagen: 'productos',
    },
    {
      texto: 'Ventas',
      descripcion: 'Consulta el historial y cancela ventas.',
      ruta: '/ventas',
      icono: 'ventas',
      imagen: 'ventas',
    },
    {
      texto: 'Inventario',
      descripcion: 'Consulta existencias y movimientos.',
      ruta: '/inventario',
      icono: 'inventario',
      imagen: 'inventario',
    },
    {
      texto: 'Ajustes de inventario',
      descripcion: 'Autoriza o rechaza solicitudes de ajuste.',
      ruta: '/inventario/ajustes',
      icono: 'ajuste',
      imagen: 'ajustes',
    },
  ],
  MESERO: [
    {
      texto: 'Mesas',
      descripcion: 'Abre cuentas, registra y sirve comandas, y cobra.',
      ruta: '/mesas',
      icono: 'mesas',
      imagen: 'mesas',
    },
    {
      texto: 'Catálogo',
      descripcion: 'Consulta los productos y sus precios.',
      ruta: '/productos',
      icono: 'productos',
      imagen: 'productos',
    },
  ],
  CAJERO: [
    {
      texto: 'Caja',
      descripcion: 'Recibe el efectivo de los cobros y da el cambio.',
      ruta: '/caja/entregas',
      icono: 'cobro',
      imagen: 'caja',
    },
    {
      texto: 'Mesas',
      descripcion: 'Consulta cuentas y cancela comandas.',
      ruta: '/mesas',
      icono: 'mesas',
      imagen: 'mesas',
    },
    {
      texto: 'Ventas',
      descripcion: 'Consulta el historial de ventas.',
      ruta: '/ventas',
      icono: 'ventas',
      imagen: 'ventas',
    },
  ],
  COCINERO: [
    {
      texto: 'Cocina',
      descripcion: 'Revisa la cola y actualiza la preparación de las comandas.',
      ruta: '/cocina',
      icono: 'cocina',
    },
  ],
  ALMACENISTA: [
    {
      texto: 'Inventario',
      descripcion: 'Consulta existencias y registra movimientos.',
      ruta: '/inventario',
      icono: 'inventario',
      imagen: 'inventario',
    },
    {
      texto: 'Ajustes de inventario',
      descripcion: 'Consulta tus solicitudes y su estado.',
      ruta: '/inventario/ajustes',
      icono: 'ajuste',
      imagen: 'ajustes',
    },
  ],
};

@Component({
  selector: 'app-inicio',
  imports: [RouterLink, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla__encabezado">
      <div>
        <h1>Hola, {{ perfil()?.nombre }}</h1>
        <p class="rol">{{ nombreRol() }}</p>
      </div>
    </div>

    <nav aria-labelledby="funciones">
      <h2 id="funciones">Accesos rápidos</h2>
      <ul class="accesos">
        @for (funcion of funciones(); track funcion.ruta) {
          <li>
            <a class="acceso" [routerLink]="funcion.ruta">
              <span class="acceso__icono" [class.acceso__icono--imagen]="funcion.imagen">
                @if (funcion.imagen; as imagen) {
                  <img [src]="'/inicio-' + imagen + '.png'" alt="" />
                } @else {
                  <app-icono [nombre]="funcion.icono" />
                }
              </span>
              <span class="acceso__texto">
                <strong>{{ funcion.texto }}</strong>
                <span>{{ funcion.descripcion }}</span>
              </span>
              <app-icono class="acceso__flecha" nombre="siguiente" [tamano]="20" />
            </a>
          </li>
        }
      </ul>
    </nav>
  `,
  styles: `
    .rol {
      margin-top: 0.35rem;
      color: var(--texto-suave);
      font-size: 1.1rem;
      font-weight: 600;
    }

    .accesos {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(19rem, 100%), 1fr));
      gap: 1rem;
      margin: 1rem 0 0;
      padding: 0;
      list-style: none;
    }

    .acceso {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
      gap: 0.85rem;
      height: 100%;
      min-height: 7rem;
      padding: 1.1rem;
      border: 2px solid var(--borde);
      border-radius: var(--radio);
      background: var(--superficie);
      color: var(--texto);
      text-decoration: none;
    }

    .acceso:hover,
    .acceso:focus-visible {
      border-color: var(--azul);
    }

    .acceso:focus-visible {
      outline: none;
    }

    .acceso__icono {
      display: grid;
      place-items: center;
      width: 2.75rem;
      height: 2.75rem;
      border-radius: var(--radio-chico);
      background: var(--cabecera);
      color: var(--texto-suave);
    }

    .acceso__icono--imagen {
      width: 4.5rem;
      height: 4.5rem;
      overflow: hidden;
      border-radius: 1rem;
      background: var(--cabecera);
    }

    .acceso__icono--imagen img {
      width: 4.2rem;
      height: 4.2rem;
      object-fit: contain;
    }

    .acceso__flecha {
      color: var(--texto-suave);
    }

    .acceso:hover .acceso__icono,
    .acceso:focus-visible .acceso__icono {
      background: var(--azul-aviso);
    }

    .acceso:hover :is(.acceso__icono, .acceso__flecha),
    .acceso:focus-visible :is(.acceso__icono, .acceso__flecha) {
      color: var(--azul);
    }

    .acceso__texto {
      display: grid;
      gap: 0.25rem;
      overflow-wrap: anywhere;
    }

    .acceso__texto strong {
      font-size: 1.125rem;
      font-weight: 650;
    }

    .acceso__texto > span {
      color: var(--texto-suave);
    }

    @media (forced-colors: active) {
      .acceso:focus-visible {
        border-color: Highlight;
      }
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
}
