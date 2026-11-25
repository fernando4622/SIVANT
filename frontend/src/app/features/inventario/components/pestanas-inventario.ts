import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-pestanas-inventario',
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="pildoras" aria-label="Secciones de inventario">
      @for (p of pestanas; track p.ruta) {
        <a
          class="pildora"
          [routerLink]="p.ruta"
          routerLinkActive="pildora--activa"
          [routerLinkActiveOptions]="exacta"
          ariaCurrentWhenActive="page"
          >{{ p.texto }}</a
        >
      }
    </nav>
  `,
})
export class PestanasInventario {
  protected readonly exacta = {
    paths: 'exact',
    queryParams: 'ignored',
    matrixParams: 'ignored',
    fragment: 'ignored',
  } as const;
  protected readonly pestanas = [
    { ruta: '/inventario', texto: 'Existencias' },
    { ruta: '/inventario/movimientos', texto: 'Movimientos' },
    { ruta: '/inventario/ajustes', texto: 'Ajustes' },
  ];
}
