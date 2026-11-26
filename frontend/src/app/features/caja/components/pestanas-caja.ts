import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-pestanas-caja',
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="pildoras" aria-label="Secciones de caja">
      @for (p of pestanas; track p.ruta) {
        <a class="pildora" [routerLink]="p.ruta" routerLinkActive="pildora--activa" ariaCurrentWhenActive="page">{{
          p.texto
        }}</a>
      }
    </nav>
  `,
})
export class PestanasCaja {
  protected readonly pestanas = [
    { ruta: '/caja/entregas', texto: 'Recibir efectivo' },
    { ruta: '/caja/fondos', texto: 'Fondos de cambio' },
  ];
}
